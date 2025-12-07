SELECT d.request_nmbr,
       d.status,
       COUNT(*) AS total_transactions,
       COALESCE(SUM(d.txn_amount), 0) AS total_amount
FROM reconciliation.transaction_search_details d
WHERE 1=1
