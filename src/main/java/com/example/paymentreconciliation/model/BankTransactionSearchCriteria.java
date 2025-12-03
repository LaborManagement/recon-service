package com.example.paymentreconciliation.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public class BankTransactionSearchCriteria {
    private LocalDate txnDate;
    private BigDecimal amount;
    private String drCrFlag;
    private Long bankAccountId;
    private String bankAccountNumber;
    private String txnRef;
    private Integer statusId;

    public boolean hasAnyFilter() {
        return txnDate != null || amount != null || hasText(drCrFlag) || bankAccountId != null
                || hasText(bankAccountNumber) || hasText(txnRef) || statusId != null;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    public LocalDate getTxnDate() {
        return txnDate;
    }

    public void setTxnDate(LocalDate txnDate) {
        this.txnDate = txnDate;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getDrCrFlag() {
        return drCrFlag;
    }

    public void setDrCrFlag(String drCrFlag) {
        this.drCrFlag = drCrFlag;
    }

    public Long getBankAccountId() {
        return bankAccountId;
    }

    public void setBankAccountId(Long bankAccountId) {
        this.bankAccountId = bankAccountId;
    }

    public String getBankAccountNumber() {
        return bankAccountNumber;
    }

    public void setBankAccountNumber(String bankAccountNumber) {
        this.bankAccountNumber = bankAccountNumber;
    }

    public String getTxnRef() {
        return txnRef;
    }

    public void setTxnRef(String txnRef) {
        this.txnRef = txnRef;
    }

    public Integer getStatusId() {
        return statusId;
    }

    public void setStatusId(Integer statusId) {
        this.statusId = statusId;
    }
}
