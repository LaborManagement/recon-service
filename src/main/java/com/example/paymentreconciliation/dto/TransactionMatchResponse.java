package com.example.paymentreconciliation.dto;

public class TransactionMatchResponse {
    private Long uploadId;
    private int matched;
    private int markedNotFound;

    public TransactionMatchResponse(Long uploadId, int matched, int markedNotFound) {
        this.uploadId = uploadId;
        this.matched = matched;
        this.markedNotFound = markedNotFound;
    }

    public Long getUploadId() {
        return uploadId;
    }

    public void setUploadId(Long uploadId) {
        this.uploadId = uploadId;
    }

    public int getMatched() {
        return matched;
    }

    public void setMatched(int matched) {
        this.matched = matched;
    }

    public int getMarkedNotFound() {
        return markedNotFound;
    }

    public void setMarkedNotFound(int markedNotFound) {
        this.markedNotFound = markedNotFound;
    }
}
