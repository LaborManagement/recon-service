package com.example.paymentreconciliation.model;

import java.util.List;

public class TransactionSearchDetailSearchResponse {

    private List<TransactionSearchDetailView> summary;
    private List<MatchedTxnView> matchedTxns;

    public List<TransactionSearchDetailView> getSummary() {
        return summary;
    }

    public void setSummary(List<TransactionSearchDetailView> summary) {
        this.summary = summary;
    }

    public List<MatchedTxnView> getMatchedTxns() {
        return matchedTxns;
    }

    public void setMatchedTxns(List<MatchedTxnView> matchedTxns) {
        this.matchedTxns = matchedTxns;
    }
}
