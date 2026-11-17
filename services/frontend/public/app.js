'use strict';

const PAGE_SIZE = 10;
const CUSTOMER_ID = 'web-' + Math.random().toString(36).slice(2, 8);
let skip = 0;

const $ = (sel) => document.querySelector(sel);

function escapeHtml(value) {
  return String(value).replace(/[&<>"']/g, (c) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
  }[c]));
}

function toast(message, ok = true) {
  const el = $('#toast');
  el.textContent = message;
  el.className = 'show ' + (ok ? 'ok' : 'err');
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => { el.className = ''; }, 3500);
}

async function api(path, options = {}) {
  const res = await fetch(path, {
    headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
    ...options,
  });
  const text = await res.text();
  let body;
  try { body = text ? JSON.parse(text) : null; } catch { body = text; }
  if (!res.ok) {
    const detail = (body && (body.detail || body.title)) || res.statusText;
    throw new Error(`${res.status} ${detail}`);
  }
  return body;
}

// ---------- Navigation ----------
function showPage(name) {
  document.querySelectorAll('.page').forEach((p) => p.classList.toggle('active', p.id === name));
  document.querySelectorAll('nav a').forEach((a) => a.classList.toggle('active', a.dataset.page === name));
  if (name === 'catalog') loadProducts();
  if (name === 'orders') loadOrders();
  if (name === 'dashboard') loadDashboard();
}

window.addEventListener('hashchange', () => showPage(location.hash.slice(1) || 'dashboard'));

// ---------- Dashboard ----------
async function setStatus(selector, path) {
  const el = $(selector);
  try {
    const body = await api(path);
    el.textContent = body.status;
    el.className = 'badge ok';
  } catch (e) {
    el.textContent = 'DOWN';
    el.className = 'badge err';
  }
}

async function loadDashboard() {
  setStatus('#catalog-status', '/api/catalog/health/ready');
  setStatus('#order-status', '/api/order-health');
  try {
    const stats = await api('/api/orders/stats');
    $('#stats').innerHTML = Object.entries(stats).map(([status, count]) => `
      <div class="card"><div class="card-title">${escapeHtml(status)}</div><div class="metric">${count}</div></div>`).join('');
  } catch (e) {
    $('#stats').innerHTML = `<div class="card err-text">${escapeHtml(e.message)}</div>`;
  }
}

// ---------- Catalog ----------
async function loadProducts() {
  const q = $('#search').value.trim();
  const params = new URLSearchParams({ skip, limit: PAGE_SIZE });
  if (q) params.set('q', q);
  try {
    const page = await api('/api/catalog/products?' + params);
    $('#products').innerHTML = page.items.map((p) => `
      <tr>
        <td>${p.id}</td><td>${escapeHtml(p.name)}</td><td><span class="tag">${escapeHtml(p.category)}</span></td>
        <td>R$ ${p.price.toFixed(2)}</td><td>${p.stock}</td>
        <td><button class="btn sm" data-buy="${p.id}">Comprar</button></td>
      </tr>`).join('');
    const pages = Math.max(1, Math.ceil(page.total / PAGE_SIZE));
    $('#page-info').textContent = `Página ${Math.floor(skip / PAGE_SIZE) + 1} de ${pages}`;
    $('#prev-btn').disabled = skip === 0;
    $('#next-btn').disabled = skip + PAGE_SIZE >= page.total;
  } catch (e) {
    $('#products').innerHTML = `<tr><td colspan="6" class="err-text">${escapeHtml(e.message)}</td></tr>`;
  }
}

async function buy(productId) {
  try {
    const order = await api('/api/orders', {
      method: 'POST',
      body: JSON.stringify({ customerId: CUSTOMER_ID, productId, quantity: 1 }),
    });
    toast(`Pedido #${order.id} criado (R$ ${Number(order.total).toFixed(2)})`);
    loadProducts();
  } catch (e) {
    toast('Falha no pedido: ' + e.message, false);
  }
}

// ---------- Orders ----------
async function loadOrders() {
  try {
    const orders = await api('/api/orders?limit=25');
    $('#orders-body').innerHTML = orders.map((o) => `
      <tr>
        <td>${o.id}</td><td>${escapeHtml(o.customerId)}</td><td>${escapeHtml(o.productName)}</td>
        <td>${o.quantity}</td><td>R$ ${Number(o.total).toFixed(2)}</td>
        <td><span class="badge status-${o.status.toLowerCase()}">${o.status}</span></td>
        <td>${new Date(o.createdAt).toLocaleString('pt-BR')}</td>
      </tr>`).join('');
  } catch (e) {
    $('#orders-body').innerHTML = `<tr><td colspan="7" class="err-text">${escapeHtml(e.message)}</td></tr>`;
  }
}

// ---------- Chaos ----------
async function triggerChaos(path) {
  const started = performance.now();
  try {
    const body = await api(path);
    $('#chaos-output').textContent = `${path}\n${JSON.stringify(body, null, 2)}`;
    toast(`OK em ${Math.round(performance.now() - started)} ms`);
  } catch (e) {
    $('#chaos-output').textContent = `${path}\n${e.message}`;
    toast(e.message, false);
  }
}

// ---------- Wiring ----------
document.addEventListener('click', (event) => {
  const target = event.target;
  if (target.dataset.buy) buy(Number(target.dataset.buy));
  if (target.dataset.chaos) triggerChaos(target.dataset.chaos);
});
$('#prev-btn').addEventListener('click', () => { skip = Math.max(0, skip - PAGE_SIZE); loadProducts(); });
$('#next-btn').addEventListener('click', () => { skip += PAGE_SIZE; loadProducts(); });
$('#search').addEventListener('input', () => { skip = 0; clearTimeout(loadProducts.t); loadProducts.t = setTimeout(loadProducts, 300); });
$('#refresh-orders').addEventListener('click', loadOrders);

showPage(location.hash.slice(1) || 'dashboard');
setInterval(() => { if ($('#dashboard').classList.contains('active')) loadDashboard(); }, 15000);
