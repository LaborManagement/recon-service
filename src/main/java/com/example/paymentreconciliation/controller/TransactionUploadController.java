package com.example.paymentreconciliation.controller;

import java.util.Map;

import org.slf4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.paymentreconciliation.dto.TransactionMatchResponse;
import com.example.paymentreconciliation.dto.TransactionUploadResponse;
import com.example.paymentreconciliation.service.TransactionMatchService;
import com.example.paymentreconciliation.service.TransactionUploadService;
import com.example.paymentreconciliation.service.TransactionUploadService.DuplicateFileException;
import com.shared.utilities.logger.LoggerFactoryProvider;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/recon-service/api/v1/reconciliation/transaction-uploads")
@Tag(name = "Transaction Uploads", description = "Upload CSV of transactions for search and claim")
@SecurityRequirement(name = "Bearer Authentication")
public class TransactionUploadController {

    private static final Logger log = LoggerFactoryProvider.getLogger(TransactionUploadController.class);

    private final TransactionUploadService uploadService;
    private final TransactionMatchService matchService;

    public TransactionUploadController(TransactionUploadService uploadService,
            TransactionMatchService matchService) {
        this.uploadService = uploadService;
        this.matchService = matchService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload transaction CSV", description = "Accepts CSV with columns: txn_ref, request_nmbr (or wage_list) optional, must match worker_payment_receipts.receipt_number, txn_date, txn_amount, txn_type (optional, defaults to UPI). Tenant board/employer/toli are derived from access context (not from CSV).")
    public ResponseEntity<?> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String uploadedBy) {
        try {
            TransactionUploadResponse uploadResponse = uploadService.upload(file, uploadedBy);
            if (!"LOADED".equalsIgnoreCase(uploadResponse.getStatus())) {
                return ResponseEntity.badRequest().body(uploadResponse);
            }
            TransactionMatchResponse matchResponse = matchService.matchUpload(uploadResponse.getUploadId());
            uploadResponse.setMatchedRows(matchResponse.getMatched());
            uploadResponse.setNotFoundRows(matchResponse.getMarkedNotFound());
            return ResponseEntity.ok(uploadResponse);
        } catch (DuplicateFileException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "error", ex.getMessage(),
                            "uploadId", ex.getExistingUploadId()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            log.error("Failed to upload transaction CSV", ex);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Unable to upload transactions right now"));
        }
    }
}
