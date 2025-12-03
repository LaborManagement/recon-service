package com.example.paymentreconciliation.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.paymentreconciliation.model.BankTransactionView;
import com.example.paymentreconciliation.service.BankTransactionSearchService;
import com.example.paymentreconciliation.service.StatusService;
import com.shared.common.annotation.SecurePagination;
import com.shared.common.dto.SecurePaginationRequest;
import com.shared.common.dto.SecurePaginationResponse;
import com.shared.common.util.SecurePaginationUtil;
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
    private static final List<String> SECURE_SORT_FIELDS = List.of("receiptDate", "createdAt", "amount", "id");

    private final BankTransactionSearchService searchService;
    private final StatusService statusService;

    public BankTransactionController(BankTransactionSearchService searchService, StatusService statusService) {
        this.searchService = searchService;
        this.statusService = statusService;
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

    @PostMapping("/secure")
    @Operation(summary = "Secure paginated search of reconciliation bank_transaction", description = "Mandatory date range with opaque page tokens; filters by amount, dr_cr_flag, bank_account_id, bank_account_nmbr, txn_ref")
    @SecurePagination
    public ResponseEntity<?> searchTransactionsSecure(
            @Valid @RequestBody SecurePaginationRequest request,
            @RequestParam(required = false) BigDecimal amount,
            @RequestParam(required = false) String drCrFlag,
            @RequestParam(required = false) Long bankAccountId,
            @RequestParam(name = "bankAccountNmbr", required = false) String bankAccountNmbr,
            @RequestParam(name = "bankAccountNumber", required = false) String bankAccountNumberAlias,
            @RequestParam(required = false) String txnRef) {
        try {
            SecurePaginationUtil.applyPageToken(request);
            SecurePaginationUtil.ValidationResult validation = SecurePaginationUtil.validatePaginationRequest(request);
            if (!validation.isValid()) {
                return ResponseEntity.badRequest().body(SecurePaginationUtil.createErrorResponse(validation));
            }

            String bankAccountNumber = resolveAccountNumber(bankAccountNmbr, bankAccountNumberAlias);
            Sort sort = SecurePaginationUtil.createSecureSort(request, SECURE_SORT_FIELDS);
            Pageable pageable = PageRequest.of(
                    request.getPage(),
                    request.getSize(),
                    sort);

            Page<BankTransactionView> result = searchService.searchSecure(
                    validation.getStartDateTime().toLocalDate(),
                    validation.getEndDateTime().toLocalDate(),
                    amount,
                    drCrFlag,
                    bankAccountId,
                    bankAccountNumber,
                    txnRef,
                    request.getStatus(),
                    pageable);
            SecurePaginationResponse<BankTransactionView> response = SecurePaginationUtil.createSecureResponse(result,
                    request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            log.error("Failed to search bank transactions (secure paginated)", ex);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Unable to search bank transactions right now"));
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
        try {
            DateTimeFormatter formatter = new DateTimeFormatterBuilder()
                    .parseCaseInsensitive()
                    .appendPattern("yyyy-MM-dd")
                    .toFormatter(Locale.ENGLISH);
            return LocalDate.parse(txnDate, formatter);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Invalid txnDate format. Expected yyyy-MM-dd", ex);
        }
    }
}
