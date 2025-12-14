package org.letspeppol.proxy.controller;

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
public class MonitoringController {

    private final BalanceService balanceService;

    @GetMapping()
    public ResponseEntity<String> ok() {
        return ResponseEntity.ok( "ok" );
    }

    @GetMapping("{amount}")
    public ResponseEntity<String> topUp(@PathVariable long amount) {
        return ResponseEntity.ok( "balance = " + balanceService.incrementBy(amount) );
    }

}
