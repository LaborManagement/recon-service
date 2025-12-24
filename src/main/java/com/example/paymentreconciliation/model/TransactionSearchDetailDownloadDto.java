package com.example.paymentreconciliation.model;

import java.math.BigDecimal;
import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO for transaction search details download (CSV export).
 * Contains only the columns needed for the download file.
 */
@Schema(description = "Transaction search detail record for CSV download")
public class TransactionSearchDetailDownloadDto {

    @Schema(description = "Request number", example = "REQ-20251209-001")
    private String requestNmbr;

    @Schema(description = "Transaction date", example = "2025-12-09")
    private LocalDate txnDate;

    @Schema(description = "Transaction reference", example = "TXN-123456")
    private String txnRef;

    @Schema(description = "Transaction amount", example = "1500.50")
    private BigDecimal txnAmount;

    @Schema(description = "Transaction status", example = "FOUND")
    private String status;

    public TransactionSearchDetailDownloadDto() {
    }

    public TransactionSearchDetailDownloadDto(String requestNmbr, LocalDate txnDate, String txnRef,
            BigDecimal txnAmount, String status) {
        this.requestNmbr = requestNmbr;
        this.txnDate = txnDate;
        this.txnRef = txnRef;
        this.txnAmount = txnAmount;
        this.status = status;
    }

    public String getRequestNmbr() {
        return requestNmbr;
    }

    public void setRequestNmbr(String requestNmbr) {
        this.requestNmbr = requestNmbr;
    }

    public LocalDate getTxnDate() {
        return txnDate;
    }

    public void setTxnDate(LocalDate txnDate) {
        this.txnDate = txnDate;
    }

    public String getTxnRef() {
        return txnRef;
    }

    public void setTxnRef(String txnRef) {
        this.txnRef = txnRef;
    }

    public BigDecimal getTxnAmount() {
        return txnAmount;
    }

    public void setTxnAmount(BigDecimal txnAmount) {
        this.txnAmount = txnAmount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return "TransactionSearchDetailDownloadDto{" +
                "requestNmbr='" + requestNmbr + '\'' +
                ", txnDate=" + txnDate +
                ", txnRef='" + txnRef + '\'' +
                ", txnAmount=" + txnAmount +
                ", status='" + status + '\'' +
                '}';
    }
}
