SELECT d.txn_type,
       d.matched_txn_id,
       d.txn_ref,
       d.request_nmbr,
       d.txn_date,
       d.txn_amount,
       d.description,
       d.status,
       d.created_at,
       d.id
FROM reconciliation.transaction_search_details d
WHERE 1=1
