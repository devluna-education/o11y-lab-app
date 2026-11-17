package com.example.orders.chaos;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Fault injection settings, used to exercise alerting and anomaly detection.
 *
 * @param errorRate fraction (0.0 - 1.0) of /api requests that fail with HTTP 500
 * @param latencyMs maximum extra latency (random 0..latencyMs) added to /api requests
 */
@ConfigurationProperties(prefix = "chaos")
public record ChaosProperties(double errorRate, long latencyMs) {
}
