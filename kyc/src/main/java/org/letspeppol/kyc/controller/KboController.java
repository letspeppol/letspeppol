package org.letspeppol.kyc.controller;

import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.letspeppol.kyc.service.kbo.KboXmlSyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/lapi/kbo")
@Hidden
public class KboController {

    private final KboXmlSyncService kboXmlSyncService;

    @GetMapping()
    public ResponseEntity<String> ok() {
        return ResponseEntity.ok( "ok" );
    }

    @PostMapping("/sync")
    public ResponseEntity<String> sync() {
        kboXmlSyncService.initialSync();
        return ResponseEntity.ok( ).build();
    }

}
