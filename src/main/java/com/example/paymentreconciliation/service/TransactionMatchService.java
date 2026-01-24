package com.example.paymentreconciliation.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.paymentreconciliation.dto.TransactionMatchResponse;
import com.shared.common.dao.TenantAccessDao;
import com.shared.utilities.logger.LoggerFactoryProvider;

@Service
public class TransactionMatchService {

    private static final Logger log = LoggerFactoryProvider.getLogger(TransactionMatchService.class);

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final TenantAccessDao tenantAccessDao;

    public TransactionMatchService(NamedParameterJdbcTemplate jdbcTemplate, TenantAccessDao tenantAccessDao) {
        this.jdbcTemplate = jdbcTemplate;
        this.tenantAccessDao = tenantAccessDao;
    }

    @Transactional
    public TransactionMatchResponse matchUpload(Long uploadId) {
        if (uploadId == null) {
            throw new IllegalArgumentException("uploadId is required");
        }
        TenantAccessDao.TenantAccess ta = requireTenantAccess();
        Map<String, Object> params = new HashMap<>();
        params.put("uploadId", uploadId);
        params.put("boardId", ta.boardId);
        params.put("employerId", ta.employerId);
        params.put("toliId", ta.toliId);

        int matched = updateFound(params);
        int notFound = updateNotFound(params);

        log.info("Matched upload_id={}, matched={}, notFound={}", uploadId, matched, notFound);
        return new TransactionMatchResponse(uploadId, matched, notFound);
    }

    private int updateFound(Map<String, Object> params) {
        String sql = """
                WITH candidates AS (
                    SELECT d.id AS detail_id,
                           b.source_txn_id,
                           b.type AS txn_type,
                           b.description,
                           ROW_NUMBER() OVER (PARTITION BY d.id ORDER BY b.txn_date DESC, b.created_at DESC) AS rn
                      FROM reconciliation.transaction_search_details d
                      JOIN reconciliation.vw_all_bank_transactions b
                        ON b.amount = d.txn_amount
                       AND b.txn_date = d.txn_date
                       AND COALESCE(TRIM(UPPER(b.txn_ref)),'') = COALESCE(TRIM(UPPER(d.txn_ref)),'')
                     WHERE d.status = 'PENDING'
                       AND d.upload_id = :uploadId
                       AND d.board_id = :boardId
                       AND d.employer_id = :employerId
                       AND COALESCE(d.toli_id, 0) = COALESCE(:toliId, 0)
                )
                UPDATE reconciliation.transaction_search_details d
                   SET status = 'FOUND',
                       matched_txn_id = c.source_txn_id,
                       txn_type = CASE
                                      WHEN c.txn_type IS NULL OR UPPER(c.txn_type) = 'NA' THEN d.txn_type
                                      ELSE c.txn_type
                                  END,
                       description = c.description,
                       checked_at = NOW(),
                       error = NULL
                  FROM candidates c
                 WHERE d.id = c.detail_id
                   AND c.rn = 1
                RETURNING d.id;
                """;
        List<Long> updated = jdbcTemplate.query(sql, new MapSqlParameterSource(params),
                (rs, rowNum) -> rs.getLong("id"));
        return updated.size();
    }

    private int updateNotFound(Map<String, Object> params) {
        String sql = """
                UPDATE reconciliation.transaction_search_details d
                   SET status = 'NOTFOUND',
                       checked_at = NOW(),
                       error = NULL
                 WHERE d.status = 'PENDING'
                   AND d.upload_id = :uploadId
                   AND d.board_id = :boardId
                   AND d.employer_id = :employerId
                   AND COALESCE(d.toli_id, 0) = COALESCE(:toliId, 0)
                """;
        return jdbcTemplate.update(sql, params);
    }

    private TenantAccessDao.TenantAccess requireTenantAccess() {
        TenantAccessDao.TenantAccess ta = tenantAccessDao.getFirstAccessibleTenant();
        if (ta == null || ta.boardId == null || ta.employerId == null) {
            throw new IllegalStateException("User has no tenant access (board/employer/toli) for matching");
        }
        return ta;
    }
}
