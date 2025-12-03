package com.example.paymentreconciliation.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;

import org.slf4j.Logger;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.paymentreconciliation.model.BankTransactionMappingUpdateRequest;
import com.example.paymentreconciliation.model.BankTransactionView;
import com.example.paymentreconciliation.service.BankTransactionMappingService;
import com.example.paymentreconciliation.service.BankTransactionSearchService;
import com.shared.utilities.logger.LoggerFactoryProvider;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/reconciliation/bank-transactions")
@Tag(name = "Reconciliation Bank Transactions", description = "APIs to search bank transactions from reconciliation database")
@SecurityRequirement(name = "Bearer Authentication")
public class BankTransactionController {

    private static final Logger log = LoggerFactoryProvider.getLogger(BankTransactionController.class);
    private static final int MAX_PAGE_SIZE = 200;

    private final BankTransactionSearchService searchService;
    private final BankTransactionMappingService mappingService;

    public BankTransactionController(BankTransactionSearchService searchService,
            BankTransactionMappingService mappingService) {
        this.searchService = searchService;
        this.mappingService = mappingService;
    }

    @GetMapping("/search")
    @Operation(summary = "Search transactions in reconciliation schema", description = "Filters by txn_date, amount, dr_cr_flag, bank_account_id, bank_account_nmbr, txn_ref; returns matching transactions without pagination metadata")
    public ResponseEntity<?> searchTransactions(
            @RequestParam(required = false) String txnDate,
            @RequestParam(required = false) BigDecimal amount,
            @RequestParam(required = false) String drCrFlag,
            @RequestParam(required = false) Long bankAccountId,
            @RequestParam(name = "bankAccountNmbr", required = false) String bankAccountNmbr,
            @RequestParam(name = "bankAccountNumber", required = false) String bankAccountNumberAlias,
            @RequestParam(required = false) String txnRef,
            @RequestParam(defaultValue = "20") int size) {
        try {
            String bankAccountNumber = resolveAccountNumber(bankAccountNmbr, bankAccountNumberAlias);
            int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
            List<BankTransactionView> result = searchService.search(
                    parseTxnDate(txnDate),
                    amount,
                    drCrFlag,
                    bankAccountId,
                    bankAccountNumber,
                    txnRef,
                    safeSize);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            log.error("Failed to search bank transactions", ex);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Unable to search bank transactions right now"));
        }
    }

    @PatchMapping("/{id}/mapping")
    @Operation(summary = "Update reconciliation mapping flag", description = "Updates is_mapped on statement_transaction or van_transaction based on txnType")
    public ResponseEntity<?> updateMappingStatus(
            @PathVariable("id") Long id,
            @Valid @RequestBody BankTransactionMappingUpdateRequest request) {
        try {
            mappingService.updateMappingStatus(request.getTxnType(), id, request.getIsMapped());
            return ResponseEntity.ok(Map.of(
                    "id", id,
                    "txnType", request.getTxnType().trim().toUpperCase(),
                    "isMapped", request.getIsMapped()));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.status(404).body(Map.of("error", ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(409).body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            log.error("Failed to update mapping status for transaction {}", id, ex);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Unable to update mapping status right now"));
        }
    }

    private String resolveAccountNumber(String bankAccountNmbr, String bankAccountNumberAlias) {
        if (bankAccountNumberAlias != null && !bankAccountNumberAlias.trim().isEmpty()) {
            return bankAccountNumberAlias.trim();
        }
        if (bankAccountNmbr != null && !bankAccountNmbr.trim().isEmpty()) {
            return bankAccountNmbr.trim();
        }
        return null;
    }

    private LocalDate parseTxnDate(String txnDate) {
        if (txnDate == null || txnDate.isBlank()) {
            return null;
        }
        String value = txnDate.trim();
        DateTimeFormatter iso = DateTimeFormatter.ISO_LOCAL_DATE; // yyyy-MM-dd
        DateTimeFormatter dMmmYyyy = new DateTimeFormatterBuilder()
                .parseCaseInsensitive()
                .appendPattern("d-MMM-uuuu")
                .toFormatter(Locale.ENGLISH); // e.g., 14-OCT-2025
        for (DateTimeFormatter fmt : new DateTimeFormatter[] { iso, dMmmYyyy }) {
            try {
                return LocalDate.parse(value, fmt);
            } catch (DateTimeParseException ignored) {
                // try next
            }
        }
        throw new IllegalArgumentException("Invalid txnDate format. Use yyyy-MM-dd or d-MMM-yyyy (e.g., 14-OCT-2025).");
    }
}
