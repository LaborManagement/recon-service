SELECT d.txn_type,
       d.matched_txn_id,
       d.board_bank,
       d.employer_bank,
       d.txn_ref,
       d.txn_date,
       d.txn_amount,
       d.description,
       d.status,
       d.created_at,
       d.id
FROM reconciliation.transaction_search_details d
WHERE 1=1
