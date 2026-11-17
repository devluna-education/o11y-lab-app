package com.example.orders.chaos;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** On-demand failure scenarios: exceptions, CPU burn and slow responses. */
@RestController
@Validated
@RequestMapping("/api/chaos")
public class ChaosController {

    private static final Logger log = LoggerFactory.getLogger(ChaosController.class);

    @GetMapping("/error")
    public Map<String, Object> error() {
        log.error("Chaos: throwing exception on demand");
        throw new IllegalStateException("Chaos: exception triggered on demand");
    }

    @GetMapping("/cpu")
    public Map<String, Object> cpu(@RequestParam(defaultValue = "500") @Min(1) @Max(10000) long durationMs) {
        long end = System.nanoTime() + durationMs * 1_000_000;
        double acc = 0;
        while (System.nanoTime() < end) {
            acc += Math.sqrt(Math.random()) * Math.sin(acc);
        }
        log.warn("Chaos: burned CPU for {} ms", durationMs);
        return Map.of("burnedMs", durationMs, "result", acc);
    }

    @GetMapping("/slow")
    public Map<String, Object> slow(@RequestParam(defaultValue = "2000") @Min(1) @Max(30000) long durationMs)
            throws InterruptedException {
        Thread.sleep(durationMs);
        log.warn("Chaos: slow response of {} ms", durationMs);
        return Map.of("sleptMs", durationMs);
    }
}
