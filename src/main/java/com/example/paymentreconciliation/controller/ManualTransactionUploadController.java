package com.example.paymentreconciliation.controller;

import com.example.paymentreconciliation.model.ManualTransactionUploadRequest;
import com.example.paymentreconciliation.model.ManualTransactionUploadResponse;
import com.example.paymentreconciliation.model.ManualTransactionUploadBatchResponse;
import com.example.paymentreconciliation.model.ManualUploadRunResponse;
import com.example.paymentreconciliation.service.ManualTransactionUploadService;
import com.example.paymentreconciliation.service.ManualTransactionUploadService.DuplicateManualTransactionException;
import com.shared.utilities.logger.LoggerFactoryProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.List;
import org.slf4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@RestController
@RequestMapping("/recon-service/api/v1/reconciliation/manual-transactions")
@Tag(name = "Manual Transactions", description = "Directly upload manual transactions to reconciliation")
@SecurityRequirement(name = "Bearer Authentication")
public class ManualTransactionUploadController {

    private static final Logger log = LoggerFactoryProvider.getLogger(ManualTransactionUploadController.class);

    private final ManualTransactionUploadService service;

    public ManualTransactionUploadController(ManualTransactionUploadService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create manual transaction", description = "Adds a manual transaction (txn_ref, txn_date, txn_amount, dr_cr_flag, description) with type STATEMENT_UPLOAD.")
    public ResponseEntity<?> create(@Valid @RequestBody ManualTransactionUploadRequest request,
            @RequestParam(required = false) String createdBy) {
        try {
            ManualTransactionUploadResponse response = service.create(request, createdBy);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (DuplicateManualTransactionException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            log.error("Failed to create manual transaction", ex);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Unable to create manual transaction right now"));
        }
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload manual transactions CSV", description = "CSV headers: txn_ref, txn_date (yyyy-MM-dd), txn_amount, dr_cr_flag (CR/DR), description (optional).")
    public ResponseEntity<?> uploadCsv(@RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String createdBy) {
        try {
            ManualTransactionUploadBatchResponse response = service.uploadCsv(file, createdBy);
            HttpStatus status = response.getFailedRows() > 0 ? HttpStatus.BAD_REQUEST : HttpStatus.CREATED;
            return ResponseEntity.status(status).body(response);
        } catch (DuplicateManualTransactionException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            log.error("Failed to upload manual transactions CSV", ex);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Unable to upload manual transactions right now"));
        }
    }

    @GetMapping("/runs")
    @Operation(summary = "List manual upload runs", description = "Returns recent manual upload runs tracked in import_run with file_type=MANUAL_TXN.")
    public ResponseEntity<List<ManualUploadRunResponse>> listRuns(
            @RequestParam(defaultValue = "50") int size) {
        List<ManualUploadRunResponse> runs = service.listRuns(size);
        return ResponseEntity.ok(runs);
    }

    @GetMapping("/runs/{runId}")
    @Operation(summary = "List manual transactions for a run", description = "Returns manual_transaction_upload rows linked to the given import_run id.")
    public ResponseEntity<?> listTransactionsByRun(@PathVariable Long runId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            var rows = service.listTransactionsByRun(runId, page, size);
            return ResponseEntity.ok(rows);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            log.error("Failed to fetch manual transactions for run {}", runId, ex);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Unable to fetch manual transactions right now"));
        }
    }
}
