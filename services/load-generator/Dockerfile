# syntax=docker/dockerfile:1.7
FROM grafana/k6:0.54.0

COPY --chmod=0755 entrypoint.sh /usr/local/bin/entrypoint.sh
COPY scripts/ /scripts/

ENV BASE_URL=http://frontend:8080 \
    DURATION=1h

ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
