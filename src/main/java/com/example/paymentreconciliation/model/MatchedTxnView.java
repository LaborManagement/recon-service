package com.example.paymentreconciliation.model;

public class MatchedTxnView {
    private Long matchedTxnId;
    private String txnType;

    public Long getMatchedTxnId() {
        return matchedTxnId;
    }

    public void setMatchedTxnId(Long matchedTxnId) {
        this.matchedTxnId = matchedTxnId;
    }

    public String getTxnType() {
        return txnType;
    }

    public void setTxnType(String txnType) {
        this.txnType = txnType;
    }
}
