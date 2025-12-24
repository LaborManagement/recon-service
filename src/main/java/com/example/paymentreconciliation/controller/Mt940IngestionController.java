package com.example.paymentreconciliation.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.paymentreconciliation.service.Mt940IngestionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/recon-service/api/mt940")
@Tag(name = "MT940 Ingestion", description = "APIs for MT940 file ingestion and processing")
@SecurityRequirement(name = "Bearer Authentication")
public class Mt940IngestionController {
    @Autowired
    private Mt940IngestionService mt940IngestionService;
    @PostMapping("/ingest")
    @Operation(summary = "Trigger MT940 ingestion", description = "Triggers polling and processing of MT940 files in the inbox")
    public ResponseEntity<String> ingest() {
        mt940IngestionService.pollAndProcessInbox();
        return ResponseEntity.ok("MT940 ingestion triggered");
    }
}
