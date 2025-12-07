package com.example.paymentreconciliation.controller;

import org.slf4j.Logger;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.paymentreconciliation.model.TransactionSearchDetailSearchRequest;
import com.example.paymentreconciliation.model.TransactionSearchDetailView;
import com.example.paymentreconciliation.service.TransactionSearchDetailService;
import com.shared.common.annotation.SecurePagination;
import com.shared.common.util.SecurePaginationUtil;
import com.shared.utilities.logger.LoggerFactoryProvider;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/reconciliation/transaction-search-details")
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

            java.util.List<TransactionSearchDetailView> result = searchService.search(request, validation);
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
}
