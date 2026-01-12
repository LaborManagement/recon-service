package com.example.paymentreconciliation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "manual_transaction_upload")
public class ManualTransactionUpload {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @jakarta.persistence.ManyToOne
    @jakarta.persistence.JoinColumn(name = "import_run_id")
    private ImportRun importRun;

    @Column(name = "txn_ref", length = 255)
    private String txnRef;

    @Column(name = "txn_date", nullable = false)
    private LocalDate txnDate;

    @Column(name = "txn_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal txnAmount;

    @Column(name = "dr_cr_flag", nullable = false, length = 2)
    private String drCrFlag;

    @Column(name = "txn_type", length = 64)
    private String txnType;

    @Column(name = "payer", length = 255)
    private String payer;

    @Column(name = "description")
    private String description;

    @Column(name = "is_mapped")
    private Boolean isMapped = Boolean.FALSE;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "created_by", length = 128)
    private String createdBy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by", length = 128)
    private String updatedBy;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ImportRun getImportRun() {
        return importRun;
    }

    public void setImportRun(ImportRun importRun) {
        this.importRun = importRun;
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

    public String getTxnType() {
        return txnType;
    }

    public void setTxnType(String txnType) {
        this.txnType = txnType;
    }

    public String getPayer() {
        return payer;
    }

    public void setPayer(String payer) {
        this.payer = payer;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Boolean getIsMapped() {
        return isMapped;
    }

    public void setIsMapped(Boolean isMapped) {
        this.isMapped = isMapped;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }
}
