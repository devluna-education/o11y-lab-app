# syntax=docker/dockerfile:1.7
FROM nginxinc/nginx-unprivileged:1.27-alpine

# Upstreams are resolved when nginx starts; override per environment.
ENV CATALOG_UPSTREAM=catalog-service:8000 \
    ORDER_UPSTREAM=order-service:8080

COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY public/ /usr/share/nginx/html/

EXPOSE 8080
