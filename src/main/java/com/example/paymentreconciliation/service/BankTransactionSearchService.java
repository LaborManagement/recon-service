package com.example.paymentreconciliation.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.slf4j.Logger;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.paymentreconciliation.dao.BankTransactionSearchDao;
import com.example.paymentreconciliation.model.BankTransactionSearchCriteria;
import com.example.paymentreconciliation.model.BankTransactionView;
import com.shared.utilities.logger.LoggerFactoryProvider;

@Service
@Transactional(readOnly = true)
public class BankTransactionSearchService {

    private static final Logger log = LoggerFactoryProvider.getLogger(BankTransactionSearchService.class);

    private final BankTransactionSearchDao dao;
    private final StatusService statusService;

    public BankTransactionSearchService(BankTransactionSearchDao dao, StatusService statusService) {
        this.dao = dao;
        this.statusService = statusService;
    }

    public List<BankTransactionView> search(LocalDate txnDate,
            BigDecimal amount,
            String drCrFlag,
            Long bankAccountId,
            String bankAccountNumber,
            String txnRef,
            Integer limit) {
        BankTransactionSearchCriteria criteria = new BankTransactionSearchCriteria();
        criteria.setTxnDate(txnDate);
        criteria.setAmount(amount);
        criteria.setDrCrFlag(drCrFlag);
        criteria.setBankAccountId(bankAccountId);
        criteria.setBankAccountNumber(bankAccountNumber);
        criteria.setTxnRef(txnRef);

        if (!criteria.hasAnyFilter()) {
            throw new IllegalArgumentException(
                    "At least one filter (txnDate, amount, drCrFlag, bankAccountId, bankAccountNmbr, txnRef) must be provided.");
        }

        log.info(
                "Searching bank transactions with criteria txnDate={}, amount={}, drCrFlag={}, bankAccountId={}, bankAccountNumber={}, txnRef={}, limit={}",
                txnDate, amount, drCrFlag, bankAccountId, bankAccountNumber, txnRef, limit);
        return dao.search(criteria, limit);
    }

    public Page<BankTransactionView> searchSecure(LocalDate startDate,
            LocalDate endDate,
            BigDecimal amount,
            String drCrFlag,
            Long bankAccountId,
            String bankAccountNumber,
            String txnRef,
            String statusCode,
            Pageable pageable) {
        BankTransactionSearchCriteria criteria = new BankTransactionSearchCriteria();
        criteria.setAmount(amount);
        criteria.setDrCrFlag(drCrFlag);
        criteria.setBankAccountId(bankAccountId);
        criteria.setBankAccountNumber(bankAccountNumber);
        criteria.setTxnRef(txnRef);
        Integer resolvedStatusId = statusCode != null && !statusCode.isBlank()
                ? statusService.requireStatusId("bank_transaction", statusCode.trim())
                : null;
        criteria.setStatusId(resolvedStatusId);

        log.info(
                "Secure paginated search for bank transactions startDate={}, endDate={}, amount={}, drCrFlag={}, bankAccountId={}, bankAccountNumber={}, txnRef={}, statusId={}, page={}, size={}",
                startDate, endDate, amount, drCrFlag, bankAccountId, bankAccountNumber, txnRef, resolvedStatusId,
                pageable != null ? pageable.getPageNumber() : null,
                pageable != null ? pageable.getPageSize() : null);
        Page<BankTransactionView> result = dao.searchPaginated(criteria, startDate, endDate, pageable);
        result.forEach(v -> {
            if (v.getStatusId() != null) {
                v.setStatus(statusService.resolveStatusCode("bank_transaction", v.getStatusId()));
            }
        });
        return result;
    }
}
