package com.example.paymentreconciliation.model;

import java.util.ArrayList;
import java.util.List;

public class ManualTransactionUploadBatchResponse {

    private Long runId;
    private int totalRows;
    private int insertedRows;
    private int failedRows;
    private List<ManualTransactionUploadResponse> inserted = new ArrayList<>();
    private List<String> errors = new ArrayList<>();

    public Long getRunId() {
        return runId;
    }

    public void setRunId(Long runId) {
        this.runId = runId;
    }

    public int getTotalRows() {
        return totalRows;
    }

    public void setTotalRows(int totalRows) {
        this.totalRows = totalRows;
    }

    public int getInsertedRows() {
        return insertedRows;
    }

    public void setInsertedRows(int insertedRows) {
        this.insertedRows = insertedRows;
    }

    public int getFailedRows() {
        return failedRows;
    }

    public void setFailedRows(int failedRows) {
        this.failedRows = failedRows;
    }

    public List<ManualTransactionUploadResponse> getInserted() {
        return inserted;
    }

    public void setInserted(List<ManualTransactionUploadResponse> inserted) {
        this.inserted = inserted;
    }

    public List<String> getErrors() {
        return errors;
    }

    public void setErrors(List<String> errors) {
        this.errors = errors;
    }
}
