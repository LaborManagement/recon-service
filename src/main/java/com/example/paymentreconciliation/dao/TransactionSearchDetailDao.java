package com.example.paymentreconciliation.dao;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
    private static final String BASE_SELECT_TEMPLATE = "sql/reconciliation/transaction_search_details_base_select.sql";
    private static final Map<String, String> SORT_COLUMN_MAP = Map.of(
            "receiptDate", "d.txn_date",
            "createdAt", "d.created_at",
            "amount", "d.txn_amount",
            "id", "d.id");

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final SqlTemplateLoader sqlTemplates;

    public TransactionSearchDetailDao(NamedParameterJdbcTemplate jdbcTemplate, SqlTemplateLoader sqlTemplates) {
        this.jdbcTemplate = jdbcTemplate;
        this.sqlTemplates = sqlTemplates;
    }

    public Page<TransactionSearchDetailView> searchPaginated(TransactionSearchDetailSearchRequest request,
            TenantAccessDao.TenantAccess tenant, Pageable pageable, LocalDate startDate, LocalDate endDate) {
        String baseSql = sqlTemplates.load(BASE_SELECT_TEMPLATE);
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

        if (hasText(request.getTxnRef())) {
            sql.append(" AND d.txn_ref = :txnRef");
            params.put("txnRef", request.getTxnRef().trim());
        }
        if (hasText(request.getStatus())) {
            sql.append(" AND UPPER(d.status) = :status");
            params.put("status", request.getStatus().trim().toUpperCase());
        }
        if (request.getUploadId() != null) {
            sql.append(" AND d.upload_id = :uploadId");
            params.put("uploadId", request.getUploadId());
        }

        String filteredSql = sql.toString();
        String countSql = "SELECT COUNT(*) FROM (" + filteredSql + ") AS count_base";

        Sort sort = pageable != null ? pageable.getSort() : Sort.unsorted();
        appendOrderBy(sql, sort, SORT_COLUMN_MAP);

        if (pageable != null) {
            sql.append(" LIMIT :limit OFFSET :offset");
            params.put("limit", pageable.getPageSize());
            params.put("offset", (long) pageable.getPageNumber() * pageable.getPageSize());
        }

        log.debug("Executing paginated transaction_search_details SQL: {} with params {}", sql, params);
        List<TransactionSearchDetailView> results = jdbcTemplate.query(
                sql.toString(),
                params,
                new TransactionSearchDetailRowMapper());
        long total = jdbcTemplate.queryForObject(countSql, params, Long.class);
        return new PageImpl<>(results, pageable, total);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private void appendOrderBy(StringBuilder sql, Sort sort, Map<String, String> sortColumnMap) {
        if (sort == null || sort.isUnsorted()) {
            sql.append(" ORDER BY d.txn_date DESC, d.created_at DESC, d.id DESC");
            return;
        }
        sql.append(" ORDER BY ");
        boolean first = true;
        for (Sort.Order order : sort) {
            if (!first) {
                sql.append(", ");
            }
            String column = sortColumnMap.getOrDefault(order.getProperty(), "d.created_at");
            sql.append(column).append(order.isAscending() ? " ASC" : " DESC");
            first = false;
        }
    }

    private static class TransactionSearchDetailRowMapper implements RowMapper<TransactionSearchDetailView> {
        @Override
        public TransactionSearchDetailView mapRow(ResultSet rs, int rowNum) throws SQLException {
            TransactionSearchDetailView view = new TransactionSearchDetailView();
            view.setType(rs.getString("txn_type"));

            Long matchedId = rs.getObject("matched_txn_id", Long.class);
            view.setSourceTxnId(matchedId != null ? matchedId.toString() : null);
            view.setBankAccountId(0);
            view.setBankAccountNumber(rs.getString("board_bank"));
            view.setTxnRef(rs.getString("txn_ref"));

            Date txnDate = rs.getDate("txn_date");
            if (txnDate != null) {
                view.setTxnDate(txnDate.toLocalDate());
            }

            view.setAmount(rs.getBigDecimal("txn_amount"));
            view.setDrCrFlag("C");
            String desc = rs.getString("description");
            if (desc == null || desc.isBlank()) {
                desc = rs.getString("employer_bank");
            }
            if (desc == null || desc.isBlank()) {
                desc = rs.getString("txn_type");
            }
            view.setDescription(desc);
            view.setStatus(rs.getString("status"));

            view.setIsMapped(matchedId != null);

            Timestamp createdAt = rs.getTimestamp("created_at");
            if (createdAt != null) {
                view.setCreatedAt(createdAt.toLocalDateTime());
            }

            return view;
        }
    }
}
