package com.example.paymentreconciliation.model;

import java.math.BigDecimal;

public class TransactionSearchDetailView {

    private String requestNmbr;
    private Long totalTransactions;
    private BigDecimal totalAmount;
    private String status;

    public String getRequestNmbr() {
        return requestNmbr;
    }

    public void setRequestNmbr(String requestNmbr) {
        this.requestNmbr = requestNmbr;
    }

    public Long getTotalTransactions() {
        return totalTransactions;
    }

    public void setTotalTransactions(Long totalTransactions) {
        this.totalTransactions = totalTransactions;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
