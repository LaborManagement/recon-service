package com.example.paymentreconciliation.model;

public class MatchedTxnView {
    private Long matchedTxnId;
    private String txnType;
    private String requestNmbr;

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

    public String getRequestNmbr() {
        return requestNmbr;
    }

    public void setRequestNmbr(String requestNmbr) {
        this.requestNmbr = requestNmbr;
    }
}
