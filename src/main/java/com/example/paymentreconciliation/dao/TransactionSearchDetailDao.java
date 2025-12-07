package com.example.paymentreconciliation.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.example.paymentreconciliation.common.sql.SqlTemplateLoader;
import com.example.paymentreconciliation.model.TransactionSearchDetailSearchRequest;
import com.example.paymentreconciliation.model.TransactionSearchDetailView;
import com.shared.common.dao.TenantAccessDao;
import com.shared.utilities.logger.LoggerFactoryProvider;

@Repository
public class TransactionSearchDetailDao {

    private static final Logger log = LoggerFactoryProvider.getLogger(TransactionSearchDetailDao.class);
    private static final String SUMMARY_SELECT_TEMPLATE = "sql/reconciliation/transaction_search_details_summary.sql";

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final SqlTemplateLoader sqlTemplates;

    public TransactionSearchDetailDao(NamedParameterJdbcTemplate jdbcTemplate, SqlTemplateLoader sqlTemplates) {
        this.jdbcTemplate = jdbcTemplate;
        this.sqlTemplates = sqlTemplates;
    }

    public List<TransactionSearchDetailView> searchSummary(TransactionSearchDetailSearchRequest request,
            TenantAccessDao.TenantAccess tenant, LocalDate startDate, LocalDate endDate) {
        String baseSql = sqlTemplates.load(SUMMARY_SELECT_TEMPLATE);
        StringBuilder sql = new StringBuilder(baseSql);
        Map<String, Object> params = new HashMap<>();

        sql.append(" AND d.board_id = :boardId");
        sql.append(" AND d.employer_id = :employerId");
        sql.append(" AND COALESCE(d.toli_id, 0) = COALESCE(:toliId, 0)");
        params.put("boardId", tenant.boardId);
        params.put("employerId", tenant.employerId);
        params.put("toliId", tenant.toliId);

        sql.append(" AND d.txn_date BETWEEN :startDate AND :endDate");
        params.put("startDate", startDate);
        params.put("endDate", endDate);

        if (hasText(request.getRequestNmbr())) {
            sql.append(" AND d.request_nmbr = :requestNmbr");
            params.put("requestNmbr", request.getRequestNmbr().trim());
        }
        if (hasText(request.getStatus())) {
            sql.append(" AND UPPER(d.status) = :status");
            params.put("status", request.getStatus().trim().toUpperCase());
        }
        if (request.getUploadId() != null) {
            sql.append(" AND d.upload_id = :uploadId");
            params.put("uploadId", request.getUploadId());
        }

        sql.append(" GROUP BY d.request_nmbr, d.status");
        sql.append(" ORDER BY d.request_nmbr NULLS FIRST, d.status");

        log.debug("Executing summary transaction_search_details SQL: {} with params {}", sql, params);
        return jdbcTemplate.query(
                sql.toString(),
                params,
                new TransactionSearchDetailSummaryRowMapper());
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static class TransactionSearchDetailSummaryRowMapper implements RowMapper<TransactionSearchDetailView> {
        @Override
        public TransactionSearchDetailView mapRow(ResultSet rs, int rowNum) throws SQLException {
            TransactionSearchDetailView view = new TransactionSearchDetailView();
            view.setRequestNmbr(rs.getString("request_nmbr"));
            view.setTotalTransactions(rs.getLong("total_transactions"));
            view.setTotalAmount(rs.getBigDecimal("total_amount"));
            view.setStatus(rs.getString("status"));
            return view;
        }
    }
}
