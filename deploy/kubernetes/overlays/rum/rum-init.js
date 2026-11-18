/*
 * RUM bootstrap served at /rum-init.js (mounted from a ConfigMap; no image rebuild).
 *
 * Paste here the JavaScript your RUM vendor generates, WITHOUT the <script> tags:
 *   1. the CDN loader (if the vendor has one) first,
 *   2. then the init/config block.
 * This file is loaded synchronously from <head>, so the loader runs before the init.
 *
 * For backend trace correlation, allow W3C trace context on same-origin API calls,
 * so the instrumented services continue the browser trace. Example matcher:
 *
 *   match: function (url) { return url.indexOf(window.location.origin + '/api/') === 0; },
 *   propagatorTypes: ['tracecontext'],
 *
 * Client tokens end up in every visitor's browser by design, but avoid committing
 * real ones to a public repository. See README.md in this folder.
 */

// ---- 1. Vendor loader (replace) --------------------------------------------

// ---- 2. Vendor init (replace) ----------------------------------------------

console.info('[rum-init] placeholder loaded: paste your RUM snippet into deploy/kubernetes/overlays/rum/rum-init.js');
