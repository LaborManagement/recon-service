package com.example.paymentreconciliation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "transaction_search_details")
public class TransactionSearchDetail {

    public enum Status {
        PENDING,
        FOUND,
        NOTFOUND,
        CLAIMED,
        FAILED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "upload_id", nullable = false)
    private TransactionUpload upload;

    @Column(name = "line_no")
    private Integer lineNo;

    @Column(name = "board_id")
    private Long boardId;

    @Column(name = "employer_id")
    private Long employerId;

    @Column(name = "toli_id")
    private Long toliId;

    @Column(name = "board_bank")
    private String boardBank;

    @Column(name = "employer_bank")
    private String employerBank;

    @Column(name = "txn_type")
    private String txnType;

    @Column(name = "txn_date")
    private LocalDate txnDate;

    @Column(name = "txn_ref")
    private String txnRef;

    @Column(name = "txn_amount")
    private BigDecimal txnAmount;

    @Column(name = "matched_txn_id")
    private Long matchedTxnId;

    @Column(name = "description")
    private String description;

    @Column(name = "claim_id")
    private String claimId;

    @Column(name = "checked_at")
    private LocalDateTime checkedAt;

    @Column(name = "claimed_at")
    private LocalDateTime claimedAt;

    @Column(name = "error")
    private String error;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private Status status = Status.PENDING;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public TransactionUpload getUpload() {
        return upload;
    }

    public void setUpload(TransactionUpload upload) {
        this.upload = upload;
    }

    public Integer getLineNo() {
        return lineNo;
    }

    public void setLineNo(Integer lineNo) {
        this.lineNo = lineNo;
    }

    public Long getBoardId() {
        return boardId;
    }

    public void setBoardId(Long boardId) {
        this.boardId = boardId;
    }

    public Long getEmployerId() {
        return employerId;
    }

    public void setEmployerId(Long employerId) {
        this.employerId = employerId;
    }

    public Long getToliId() {
        return toliId;
    }

    public void setToliId(Long toliId) {
        this.toliId = toliId;
    }

    public String getBoardBank() {
        return boardBank;
    }

    public void setBoardBank(String boardBank) {
        this.boardBank = boardBank;
    }

    public String getEmployerBank() {
        return employerBank;
    }

    public void setEmployerBank(String employerBank) {
        this.employerBank = employerBank;
    }

    public String getTxnType() {
        return txnType;
    }

    public void setTxnType(String txnType) {
        this.txnType = txnType;
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

    public Long getMatchedTxnId() {
        return matchedTxnId;
    }

    public void setMatchedTxnId(Long matchedTxnId) {
        this.matchedTxnId = matchedTxnId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getClaimId() {
        return claimId;
    }

    public void setClaimId(String claimId) {
        this.claimId = claimId;
    }

    public LocalDateTime getCheckedAt() {
        return checkedAt;
    }

    public void setCheckedAt(LocalDateTime checkedAt) {
        this.checkedAt = checkedAt;
    }

    public LocalDateTime getClaimedAt() {
        return claimedAt;
    }

    public void setClaimedAt(LocalDateTime claimedAt) {
        this.claimedAt = claimedAt;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
