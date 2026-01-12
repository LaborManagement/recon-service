package com.example.paymentreconciliation.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public class ManualTransactionUploadResponse {
    private Long id;
    private String txnType;
    private String txnRef;
    private LocalDate txnDate;
    private BigDecimal txnAmount;
    private String drCrFlag;
    private String payer;
    private String description;

    public ManualTransactionUploadResponse() {
    }

    public ManualTransactionUploadResponse(Long id, String txnType, String txnRef, LocalDate txnDate,
            BigDecimal txnAmount, String drCrFlag, String payer, String description) {
        this.id = id;
        this.txnType = txnType;
        this.txnRef = txnRef;
        this.txnDate = txnDate;
        this.txnAmount = txnAmount;
        this.drCrFlag = drCrFlag;
        this.payer = payer;
        this.description = description;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTxnType() {
        return txnType;
    }

    public void setTxnType(String txnType) {
        this.txnType = txnType;
    }

    public String getTxnRef() {
        return txnRef;
    }

    public void setTxnRef(String txnRef) {
        this.txnRef = txnRef;
    }

    public LocalDate getTxnDate() {
        return txnDate;
    }

    public void setTxnDate(LocalDate txnDate) {
        this.txnDate = txnDate;
    }

    public BigDecimal getTxnAmount() {
        return txnAmount;
    }

    public void setTxnAmount(BigDecimal txnAmount) {
        this.txnAmount = txnAmount;
    }

    public String getDrCrFlag() {
        return drCrFlag;
    }

    public void setDrCrFlag(String drCrFlag) {
        this.drCrFlag = drCrFlag;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getPayer() {
        return payer;
    }

    public void setPayer(String payer) {
        this.payer = payer;
    }
}
