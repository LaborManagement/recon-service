SELECT bt.type AS type,
       bt.source_system,
       bt.source_txn_id,
       bt.bank_account_id,
       ba.account_no AS bank_account_number,
       bt.txn_ref,
       bt.txn_date,
       bt.amount,
       bt.dr_cr_flag,
       bt.description,
       bt.is_mapped,
       bt.created_at
FROM reconciliation.vw_all_bank_transactions bt
LEFT JOIN reconciliation.bank_account ba ON ba.id = bt.bank_account_id
WHERE 1=1