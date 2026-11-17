#!/bin/sh
# Runs k6 in bounded iterations (DURATION each) forever, so in-memory metric
# aggregation never grows unbounded on long-running lab clusters.
set -eu

SCRIPT="${K6_SCRIPT:-/scripts/load-test.js}"

while true; do
  echo "Starting k6 run (duration=${DURATION:-1h}, target=${BASE_URL:-http://frontend:8080})"
  k6 run --no-summary --quiet "$SCRIPT" || echo "k6 run exited with status $?, restarting in 10s"
  sleep 10
done
