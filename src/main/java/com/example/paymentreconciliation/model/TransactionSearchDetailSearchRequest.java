package com.example.paymentreconciliation.model;

import com.shared.common.dto.SecurePaginationRequest;

public class TransactionSearchDetailSearchRequest extends SecurePaginationRequest {

    private String txnRef;
    private Long uploadId;

    public TransactionSearchDetailSearchRequest() {
        super();
        this.setSortBy("receiptDate");
        this.setSortDir("desc");
    }

    public String getTxnRef() {
        return txnRef;
    }

    public void setTxnRef(String txnRef) {
        this.txnRef = txnRef;
    }

    public Long getUploadId() {
        return uploadId;
    }

    public void setUploadId(Long uploadId) {
        this.uploadId = uploadId;
    }
}
