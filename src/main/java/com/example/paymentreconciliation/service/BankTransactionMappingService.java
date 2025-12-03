package com.example.paymentreconciliation.service;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BankTransactionMappingService {

    private static final Set<String> SUPPORTED_TYPES = Set.of("MT940", "CAMT53", "VAN");

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public BankTransactionMappingService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void updateMappingStatus(String txnType, Long id, boolean isMapped) {
        if (id == null) {
            throw new IllegalArgumentException("id is required");
        }
        String normalizedType = normalizeType(txnType);
        String table = resolveTable(normalizedType);
        Boolean currentStatus = fetchCurrentStatus(table, id);
        if (currentStatus.equals(isMapped)) {
            return; // no-op
        }

        String sql = """
                UPDATE %s
                   SET is_mapped = :isMapped
                 WHERE id = :id
                   AND COALESCE(is_mapped, FALSE) <> :isMapped
                """.formatted(table);
        int updated = jdbcTemplate.update(sql, Map.of("isMapped", isMapped, "id", id));
        if (updated == 0) {
            throw new IllegalStateException("Mapping status changed concurrently for id " + id);
        }
    }

    private Boolean fetchCurrentStatus(String table, Long id) {
        String sql = "SELECT COALESCE(is_mapped, FALSE) AS is_mapped FROM %s WHERE id = :id".formatted(table);
        List<Boolean> statuses = jdbcTemplate.query(sql, Map.of("id", id),
                (rs, rowNum) -> rs.getBoolean("is_mapped"));
        if (statuses.isEmpty()) {
            throw new NoSuchElementException("Transaction not found in " + table + " with id " + id);
        }
        return statuses.get(0);
    }

    private String normalizeType(String txnType) {
        if (txnType == null || txnType.trim().isEmpty()) {
            throw new IllegalArgumentException("txnType is required");
        }
        String normalized = txnType.trim().toUpperCase();
        if (!SUPPORTED_TYPES.contains(normalized)) {
            throw new IllegalArgumentException("Unsupported txnType: " + txnType);
        }
        return normalized;
    }

    private String resolveTable(String normalizedType) {
        if ("VAN".equals(normalizedType)) {
            return "reconciliation.van_transaction";
        }
        // Default to statement transactions for MT940/CAMT53
        return "reconciliation.statement_transaction";
    }
}
