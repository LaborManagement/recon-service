package com.example.paymentreconciliation.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
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
import com.example.paymentreconciliation.model.BankTransactionSearchCriteria;
import com.example.paymentreconciliation.model.BankTransactionView;
import com.shared.utilities.logger.LoggerFactoryProvider;

@Repository
public class BankTransactionSearchDao {

    private static final Logger log = LoggerFactoryProvider.getLogger(BankTransactionSearchDao.class);
    private static final String BASE_SELECT_TEMPLATE = "sql/reconciliation/bank_transactions_base_select.sql";
    private static final Map<String, String> SORT_COLUMN_MAP = Map.of(
            "receiptDate", "bt.txn_date",
            "createdAt", "bt.created_at",
            "amount", "bt.amount",
            "id", "bt.source_txn_id");

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final SqlTemplateLoader sqlTemplates;

    public BankTransactionSearchDao(NamedParameterJdbcTemplate namedParameterJdbcTemplate,
            SqlTemplateLoader sqlTemplates) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
        this.sqlTemplates = sqlTemplates;
    }

    public List<BankTransactionView> search(BankTransactionSearchCriteria criteria, Integer limit) {
        String baseSql = sqlTemplates.load(BASE_SELECT_TEMPLATE);
        StringBuilder sql = new StringBuilder(baseSql);
        Map<String, Object> params = new HashMap<>();

        if (criteria.getTxnDate() != null) {
            sql.append(" AND bt.txn_date = :txnDate");
            params.put("txnDate", criteria.getTxnDate());
        }
        if (criteria.getAmount() != null) {
            sql.append(" AND bt.amount = :amount");
            params.put("amount", criteria.getAmount());
        }
        if (hasText(criteria.getDrCrFlag())) {
            sql.append(" AND UPPER(bt.dr_cr_flag) = :drCrFlag");
            params.put("drCrFlag", criteria.getDrCrFlag().trim().toUpperCase());
        }
        if (criteria.getBankAccountId() != null) {
            sql.append(" AND bt.bank_account_id = :bankAccountId");
            params.put("bankAccountId", criteria.getBankAccountId());
        }
        if (hasText(criteria.getBankAccountNumber())) {
            sql.append(" AND ba.account_no = :bankAccountNumber");
            params.put("bankAccountNumber", criteria.getBankAccountNumber().trim());
        }
        if (hasText(criteria.getTxnRef())) {
            sql.append(" AND bt.txn_ref = :txnRef");
            params.put("txnRef", criteria.getTxnRef().trim());
        }
        if (criteria.getStatusId() != null) {
            sql.append(" AND bt.status_id = :statusId");
            params.put("statusId", criteria.getStatusId());
        }

        sql.append(" ORDER BY bt.txn_date DESC, bt.created_at DESC");
        if (limit != null && limit > 0) {
            sql.append(" LIMIT :limit");
            params.put("limit", limit);
        }

        log.debug("Executing bank transaction search SQL: {} with params {}", sql, params);
        List<BankTransactionView> results = namedParameterJdbcTemplate.query(
                sql.toString(),
                params,
                new BankTransactionRowMapper());
        log.debug("Fetched {} transactions for criteria {}", results.size(), criteria);
        return results;
    }

    public Page<BankTransactionView> searchPaginated(BankTransactionSearchCriteria criteria,
            LocalDate startDate, LocalDate endDate, Pageable pageable) {
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("startDate and endDate are required for secure pagination");
        }

        String baseSql = sqlTemplates.load(BASE_SELECT_TEMPLATE);
        StringBuilder sql = new StringBuilder(baseSql);
        Map<String, Object> params = new HashMap<>();

        sql.append(" AND bt.txn_date BETWEEN :startDate AND :endDate");
        params.put("startDate", startDate);
        params.put("endDate", endDate);

        if (criteria.getTxnDate() != null) {
            sql.append(" AND bt.txn_date = :txnDate");
            params.put("txnDate", criteria.getTxnDate());
        }
        if (criteria.getAmount() != null) {
            sql.append(" AND bt.amount = :amount");
            params.put("amount", criteria.getAmount());
        }
        if (hasText(criteria.getDrCrFlag())) {
            sql.append(" AND UPPER(bt.dr_cr_flag) = :drCrFlag");
            params.put("drCrFlag", criteria.getDrCrFlag().trim().toUpperCase());
        }
        if (criteria.getBankAccountId() != null) {
            sql.append(" AND bt.bank_account_id = :bankAccountId");
            params.put("bankAccountId", criteria.getBankAccountId());
        }
        if (hasText(criteria.getBankAccountNumber())) {
            sql.append(" AND ba.account_no = :bankAccountNumber");
            params.put("bankAccountNumber", criteria.getBankAccountNumber().trim());
        }
        if (hasText(criteria.getTxnRef())) {
            sql.append(" AND bt.txn_ref = :txnRef");
            params.put("txnRef", criteria.getTxnRef().trim());
        }
        if (criteria.getStatusId() != null) {
            sql.append(" AND bt.status_id = :statusId");
            params.put("statusId", criteria.getStatusId());
        }

        Sort sort = pageable != null ? pageable.getSort() : Sort.unsorted();
        appendOrderBy(sql, sort, SORT_COLUMN_MAP);

        if (pageable != null) {
            sql.append(" LIMIT :limit OFFSET :offset");
            params.put("limit", pageable.getPageSize());
            params.put("offset", (long) pageable.getPageNumber() * pageable.getPageSize());
        }

        String countSql = "SELECT COUNT(*) FROM (" + baseSql + sql.substring(baseSql.length()) + ") AS count_base";

        log.debug("Executing paginated bank transaction search SQL: {} with params {}", sql, params);
        List<BankTransactionView> results = namedParameterJdbcTemplate.query(
                sql.toString(),
                params,
                new BankTransactionRowMapper());
        long total = namedParameterJdbcTemplate.queryForObject(countSql, params, Long.class);
        return new PageImpl<>(results, pageable, total);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private void appendOrderBy(StringBuilder sql, Sort sort, Map<String, String> sortColumnMap) {
        if (sort == null || sort.isUnsorted()) {
            sql.append(" ORDER BY txn_date DESC, created_at DESC");
            return;
        }
        sql.append(" ORDER BY ");
        boolean first = true;
        for (Sort.Order order : sort) {
            if (!first) {
                sql.append(", ");
            }
            String column = sortColumnMap.getOrDefault(order.getProperty(), "bt.created_at");
            sql.append(column).append(order.isAscending() ? " ASC" : " DESC");
            first = false;
        }
    }

    private static class BankTransactionRowMapper implements RowMapper<BankTransactionView> {
        @Override
        public BankTransactionView mapRow(ResultSet rs, int rowNum) throws SQLException {
            BankTransactionView view = new BankTransactionView();
            view.setType(rs.getString("type"));
            view.setSourceTxnId(rs.getString("source_txn_id"));
            long bankAccountId = rs.getLong("bank_account_id");
            if (!rs.wasNull()) {
                view.setBankAccountId((int) bankAccountId);
            }
            view.setBankAccountNumber(rs.getString("bank_account_number"));
            view.setTxnRef(rs.getString("txn_ref"));

            java.sql.Date txnDate = rs.getDate("txn_date");
            if (txnDate != null) {
                view.setTxnDate(txnDate.toLocalDate());
            }

            view.setAmount(rs.getBigDecimal("amount"));
            view.setDrCrFlag(rs.getString("dr_cr_flag"));
            view.setDescription(rs.getString("description"));
            Boolean isMapped = rs.getObject("is_mapped", Boolean.class);
            view.setIsMapped(isMapped != null ? isMapped : Boolean.FALSE);

            java.sql.Timestamp createdAt = rs.getTimestamp("created_at");
            if (createdAt != null) {
                view.setCreatedAt(createdAt.toLocalDateTime());
            }

            Integer statusId = rs.getObject("status_id", Integer.class);
            view.setStatusId(statusId);
            return view;
        }
    }
}
