package com.example.paymentreconciliation.model;

import jakarta.validation.constraints.NotBlank;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Request DTO for downloading transaction search details.
 * Filters records by request_nmbr and status.
 */
@Schema(description = "Request for downloading transaction search details filtered by request number and status")
public class TransactionSearchDetailDownloadRequest {

    @NotBlank(message = "Request number is required")
    @Schema(description = "Request number to filter by", example = "REQ-20251209-001", required = true)
    private String requestNmbr;

    @NotBlank(message = "Status is required")
    @Schema(description = "Transaction status to filter by (PENDING, FOUND, NOTFOUND, CLAIMED, FAILED)", example = "FOUND", required = true)
    private String status;

    public TransactionSearchDetailDownloadRequest() {
    }

    public TransactionSearchDetailDownloadRequest(String requestNmbr, String status) {
        this.requestNmbr = requestNmbr;
        this.status = status;
    }

    public String getRequestNmbr() {
        return requestNmbr;
    }

    public void setRequestNmbr(String requestNmbr) {
        this.requestNmbr = requestNmbr;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return "TransactionSearchDetailDownloadRequest{" +
                "requestNmbr='" + requestNmbr + '\'' +
                ", status='" + status + '\'' +
                '}';
    }
}
