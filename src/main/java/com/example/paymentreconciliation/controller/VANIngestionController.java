package com.example.paymentreconciliation.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.paymentreconciliation.service.VANIngestionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/recon-service/api/van")
@Tag(name = "VAN Ingestion", description = "APIs for VAN CSV file ingestion and processing")
@SecurityRequirement(name = "Bearer Authentication")
public class VANIngestionController {
    @Autowired
    private VANIngestionService vanIngestionService;

    @PostMapping("/ingest")
    @Operation(summary = "Trigger VAN ingestion", description = "Triggers polling and processing of VAN CSV files in the inbox")
    public ResponseEntity<String> ingest() {
        vanIngestionService.pollAndProcessInbox();
        return ResponseEntity.ok("VAN ingestion triggered");
    }
}
