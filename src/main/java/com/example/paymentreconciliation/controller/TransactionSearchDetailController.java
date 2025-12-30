package com.example.paymentreconciliation.controller;

import org.slf4j.Logger;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.paymentreconciliation.model.TransactionSearchDetailDownloadRequest;
import com.example.paymentreconciliation.model.TransactionSearchDetailSearchRequest;
import com.example.paymentreconciliation.model.TransactionSearchDetailSearchResponse;
import com.example.paymentreconciliation.service.TransactionSearchDetailService;
import com.shared.common.annotation.SecurePagination;
import com.shared.common.util.SecurePaginationUtil;
import com.shared.utilities.logger.LoggerFactoryProvider;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/recon-service/api/v1/reconciliation/transaction-search-details")
@Tag(name = "Transaction Search Details", description = "Summary aggregation over uploaded transaction_search_details")
@SecurityRequirement(name = "Bearer Authentication")
public class TransactionSearchDetailController {

    private static final Logger log = LoggerFactoryProvider.getLogger(TransactionSearchDetailController.class);

    private final TransactionSearchDetailService searchService;

    public TransactionSearchDetailController(TransactionSearchDetailService searchService) {
        this.searchService = searchService;
    }

    @PostMapping("/search")
    @SecurePagination
    @Operation(summary = "Summary search of transaction_search_details", description = "Requires startDate and endDate; filters optional (requestNmbr, status, uploadId); results aggregated by request_nmbr and status.")
    public ResponseEntity<?> search(
            @Valid @RequestBody TransactionSearchDetailSearchRequest request) {
        try {

            SecurePaginationUtil.ValidationResult validation = SecurePaginationUtil.validatePaginationRequest(request);
            if (!validation.isValid()) {
                return ResponseEntity.badRequest().body(SecurePaginationUtil.createErrorResponse(validation));
            }

            TransactionSearchDetailSearchResponse result = searchService.search(request, validation);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", ex.getMessage()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(403).body(java.util.Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            log.error("Failed to search transaction_search_details", ex);
            return ResponseEntity.internalServerError()
                    .body(java.util.Map.of("error", "Unable to search transaction search details right now"));
        }
    }

    @PostMapping("/download")
    @Operation(summary = "Download transaction search details as CSV", description = "Downloads transaction search details filtered by request_nmbr and status as a CSV file.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "CSV file generated successfully", content = @Content(mediaType = "text/csv")),
            @ApiResponse(responseCode = "400", description = "Invalid request parameters"),
            @ApiResponse(responseCode = "403", description = "No tenant access"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<?> downloadCsv(@Valid @RequestBody TransactionSearchDetailDownloadRequest request) {
        try {
            log.info("Download request for requestNmbr={}, status={}", request.getRequestNmbr(), request.getStatus());

            java.util.List<com.example.paymentreconciliation.model.TransactionSearchDetailDownloadDto> records = searchService
                    .fetchForDownload(request.getRequestNmbr(), request.getStatus());

            if (records.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(java.util.Map.of("error", "No records found for the given request number and status"));
            }

            // Generate CSV
            StringBuilder csv = new StringBuilder();
            csv.append("request_nmbr,txn_date,txn_ref,txn_amount,status\n");

            for (var record : records) {
                csv.append(csvEscape(record.getRequestNmbr())).append(",");
                csv.append(record.getTxnDate() != null ? record.getTxnDate().toString() : "").append(",");
                csv.append(csvEscape(record.getTxnRef())).append(",");
                csv.append(record.getTxnAmount() != null ? record.getTxnAmount().toString() : "").append(",");
                csv.append(csvEscape(record.getStatus())).append("\n");
            }

            String filename = String.format("transaction_search_details_%s_%s_%s.csv",
                    request.getRequestNmbr(),
                    request.getStatus(),
                    java.time.LocalDateTime.now()
                            .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")));

            Resource resource = new ByteArrayResource(csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("text/csv"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .body(resource);

        } catch (IllegalArgumentException ex) {
            log.warn("Invalid download request: {}", ex.getMessage());
            return ResponseEntity.badRequest().body(java.util.Map.of("error", ex.getMessage()));
        } catch (IllegalStateException ex) {
            log.warn("Access denied for download: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(java.util.Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            log.error("Failed to generate CSV download", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(java.util.Map.of("error", "Failed to generate download file"));
        }
    }

    /**
     * Escape CSV values to handle commas, quotes, and newlines.
     */
    private String csvEscape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
