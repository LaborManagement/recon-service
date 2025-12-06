package com.example.paymentreconciliation.dto;

public class TransactionUploadResponse {
    private Long uploadId;
    private String status;
    private String filename;
    private String fileHash;
    private Integer totalRows;
    private Integer successfulRows;
    private Integer failedRows;
    private Integer matchedRows;
    private Integer notFoundRows;

    public TransactionUploadResponse(Long uploadId, String status, String filename, String fileHash,
            Integer totalRows, Integer successfulRows, Integer failedRows) {
        this(uploadId, status, filename, fileHash, totalRows, successfulRows, failedRows, null, null);
    }

    public TransactionUploadResponse(Long uploadId, String status, String filename, String fileHash,
            Integer totalRows, Integer successfulRows, Integer failedRows,
            Integer matchedRows, Integer notFoundRows) {
        this.uploadId = uploadId;
        this.status = status;
        this.filename = filename;
        this.fileHash = fileHash;
        this.totalRows = totalRows;
        this.successfulRows = successfulRows;
        this.failedRows = failedRows;
        this.matchedRows = matchedRows;
        this.notFoundRows = notFoundRows;
    }

    public Long getUploadId() {
        return uploadId;
    }

    public void setUploadId(Long uploadId) {
        this.uploadId = uploadId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getFileHash() {
        return fileHash;
    }

    public void setFileHash(String fileHash) {
        this.fileHash = fileHash;
    }

    public Integer getTotalRows() {
        return totalRows;
    }

    public void setTotalRows(Integer totalRows) {
        this.totalRows = totalRows;
    }

    public Integer getSuccessfulRows() {
        return successfulRows;
    }

    public void setSuccessfulRows(Integer successfulRows) {
        this.successfulRows = successfulRows;
    }

    public Integer getFailedRows() {
        return failedRows;
    }

    public void setFailedRows(Integer failedRows) {
        this.failedRows = failedRows;
    }

    public Integer getMatchedRows() {
        return matchedRows;
    }

    public void setMatchedRows(Integer matchedRows) {
        this.matchedRows = matchedRows;
    }

    public Integer getNotFoundRows() {
        return notFoundRows;
    }

    public void setNotFoundRows(Integer notFoundRows) {
        this.notFoundRows = notFoundRows;
    }
}
