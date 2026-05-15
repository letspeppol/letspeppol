package org.letspeppol.proxy.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.letspeppol.proxy.service.BalanceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/monitor")
@Tag(name = "Proxy Monitoring", description = "Lightweight operational endpoints used for health checks and simple balance top-up monitoring during proxy operations.")
public class MonitoringController {

    private final BalanceService balanceService;

    @GetMapping()
    @Operation(summary = "Health probe", description = "Returns a simple ok response for basic monitoring or connectivity checks.")
    public ResponseEntity<String> ok() {
        return ResponseEntity.ok( "ok" );
    }

    @GetMapping("{amount}")
    @Operation(summary = "Increase monitored balance", description = "Adds the given amount to the monitored balance counter and returns the resulting value.")
    public ResponseEntity<String> topUp(@PathVariable long amount) {
        return ResponseEntity.ok( "balance = " + balanceService.incrementBy(amount) );
    }

}
