package com.example.paymentreconciliation.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.example.paymentreconciliation.dto.TransactionUploadResponse;
import com.example.paymentreconciliation.entity.TransactionSearchDetail;
import com.example.paymentreconciliation.entity.TransactionUpload;
import com.example.paymentreconciliation.repository.TransactionSearchDetailRepository;
import com.example.paymentreconciliation.repository.TransactionUploadRepository;
import com.shared.common.dao.TenantAccessDao;
import com.shared.utilities.logger.LoggerFactoryProvider;

@Service
public class TransactionUploadService {

    private static final Logger log = LoggerFactoryProvider.getLogger(TransactionUploadService.class);
    private static final long MAX_UPLOAD_BYTES = 50 * 1024 * 1024; // 50MB

    private final TransactionUploadRepository uploadRepository;
    private final TransactionSearchDetailRepository detailRepository;
    private final TenantAccessDao tenantAccessDao;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public TransactionUploadService(TransactionUploadRepository uploadRepository,
            TransactionSearchDetailRepository detailRepository,
            TenantAccessDao tenantAccessDao,
            NamedParameterJdbcTemplate jdbcTemplate) {
        this.uploadRepository = uploadRepository;
        this.detailRepository = detailRepository;
        this.tenantAccessDao = tenantAccessDao;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public TransactionUploadResponse upload(MultipartFile file, String uploadedBy) {
        validateFile(file);
        byte[] content = toBytes(file);
        String fileHash = sha256(content);

        TenantAccessDao.TenantAccess tenantAccess = requireTenantAccess();
        Long boardId = tenantAccess.boardId != null ? tenantAccess.boardId.longValue() : null;
        Long employerId = tenantAccess.employerId != null ? tenantAccess.employerId.longValue() : null;
        Long toliId = tenantAccess.toliId != null ? tenantAccess.toliId.longValue() : null;

        Optional<TransactionUpload> duplicate = uploadRepository.findByFileHash(fileHash);
        if (duplicate.isPresent()) {
            throw new DuplicateFileException(duplicate.get().getId(), "Duplicate file by hash, upload already exists");
        }

        TransactionUpload upload = new TransactionUpload();
        upload.setFilename(file.getOriginalFilename());
        upload.setFileHash(fileHash);
        upload.setFileSizeBytes(file.getSize());
        upload.setUploadedAt(LocalDateTime.now());
        upload.setUploadedBy((uploadedBy));
        upload.setBoardId(boardId);
        upload.setEmployerId(employerId);
        upload.setToliId(toliId);
        upload.setStatus(TransactionUpload.Status.RECEIVED);
        upload = uploadRepository.save(upload);

        int failedRows = 0;
        int invalidRequestRows = 0;
        boolean rejectUpload = false;
        List<TransactionSearchDetail> details = new ArrayList<>();
        try (Reader reader = new InputStreamReader(new ByteArrayInputStream(content), StandardCharsets.UTF_8);
                CSVParser parser = CSVFormat.DEFAULT
                        .withFirstRecordAsHeader()
                        .withIgnoreHeaderCase()
                        .withTrim()
                        .withIgnoreEmptyLines()
                        .parse(reader)) {

            if (parser.getHeaderMap() == null || parser.getHeaderMap().isEmpty()) {
                throw new IllegalArgumentException("CSV header row is missing");
            }

            for (CSVRecord record : parser) {
                TransactionSearchDetail detail = toDetail(record, upload, boardId, employerId, toliId);
                if (detail.getStatus() != TransactionSearchDetail.Status.FAILED
                        && hasText(detail.getRequestNmbr())
                        && !wageListExists(detail.getRequestNmbr(), boardId, employerId)) {
                    detail.setStatus(TransactionSearchDetail.Status.FAILED);
                    detail.setError("Invalid wage_list '" + detail.getRequestNmbr() + "'; worker receipt not found");
                    invalidRequestRows++;
                    rejectUpload = true;
                }
                if (detail.getStatus() == TransactionSearchDetail.Status.FAILED) {
                    failedRows++;
                }
                details.add(detail);
            }
        } catch (IOException e) {
            upload.setStatus(TransactionUpload.Status.FAILED);
            upload.setErrorMessage("Failed to read CSV: " + e.getMessage());
            uploadRepository.save(upload);
            throw new IllegalArgumentException("Unable to read CSV file", e);
        }

        if (details.isEmpty()) {
            upload.setStatus(TransactionUpload.Status.FAILED);
            upload.setErrorMessage("No records found in CSV");
            uploadRepository.save(upload);
            throw new IllegalArgumentException("No records found in CSV");
        }

        if (rejectUpload) {
            for (TransactionSearchDetail detail : details) {
                if (detail.getStatus() != TransactionSearchDetail.Status.FAILED) {
                    detail.setStatus(TransactionSearchDetail.Status.FAILED);
                    detail.setError("Upload rejected because wage_list not found");
                }
            }
            failedRows = details.size();
        }

        detailRepository.saveAll(details);
        upload.setTotalRows(details.size());
        if (rejectUpload) {
            upload.setStatus(TransactionUpload.Status.FAILED);
            upload.setErrorMessage("Invalid wage_list found; upload rejected");
        } else {
            upload.setStatus(TransactionUpload.Status.LOADED);
            upload.setErrorMessage(failedRows > 0 ? "Some rows failed validation" : null);
        }
        uploadRepository.save(upload);

        int successfulRows = rejectUpload ? 0 : details.size() - failedRows;
        TransactionUploadResponse response = new TransactionUploadResponse(
                upload.getId(),
                upload.getStatus().name(),
                upload.getFilename(),
                upload.getFileHash(),
                upload.getTotalRows(),
                successfulRows,
                failedRows);
        response.setErrorMessage(upload.getErrorMessage());
        return response;
    }

    private TransactionSearchDetail toDetail(CSVRecord record, TransactionUpload upload, Long defaultBoardId,
            Long defaultEmployerId, Long defaultToliId) {
        int lineNo = (int) record.getRecordNumber();
        TransactionSearchDetail detail = new TransactionSearchDetail();
        detail.setUpload(upload);
        detail.setLineNo(lineNo);
        // Enforce tenant from context to satisfy RLS
        detail.setBoardId(defaultBoardId);
        detail.setEmployerId(defaultEmployerId);
        detail.setToliId(defaultToliId);
        detail.setDescription(coalesce(record, "description", null));
        detail.setTxnType(coalesce(record, "txn_type", "UPI"));
        detail.setTxnRef(required(record, "txn_ref", lineNo));
        detail.setRequestNmbr(coalesce(record, "wage_list", null));
        detail.setCreatedAt(LocalDateTime.now());

        try {
            detail.setTxnDate(parseDate(record.get("txn_date")));
            detail.setTxnAmount(parseAmount(record.get("txn_amount")));
            detail.setStatus(TransactionSearchDetail.Status.PENDING);
        } catch (IllegalArgumentException ex) {
            detail.setStatus(TransactionSearchDetail.Status.FAILED);
            detail.setError(ex.getMessage());
        }
        return detail;
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is required");
        }
        if (file.getSize() > MAX_UPLOAD_BYTES) {
            throw new IllegalArgumentException("File exceeds max allowed size of " + MAX_UPLOAD_BYTES + " bytes");
        }
    }

    private byte[] toBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read uploaded file", e);
        }
    }

    private String sha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private String required(CSVRecord record, String column, int lineNo) {
        String value = coalesce(record, column, null);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required column '" + column + "' at line " + lineNo);
        }
        return value;
    }

    private String coalesce(CSVRecord record, String column, String defaultValue) {
        String value = record.isMapped(column) ? record.get(column) : null;
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value.trim();
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("txn_date is required");
        }
        String trimmed = value.trim();
        DateTimeFormatter iso = DateTimeFormatter.ISO_LOCAL_DATE;
        DateTimeFormatter dMmmYyyy = new DateTimeFormatterBuilder()
                .parseCaseInsensitive()
                .appendPattern("d-MMM-uuuu")
                .toFormatter(Locale.ENGLISH);
        for (DateTimeFormatter fmt : new DateTimeFormatter[] { iso, dMmmYyyy }) {
            try {
                return LocalDate.parse(trimmed, fmt);
            } catch (DateTimeParseException ignored) {
                // try next format
            }
        }
        throw new IllegalArgumentException("Invalid txn_date format. Use yyyy-MM-dd or d-MMM-yyyy");
    }

    private BigDecimal parseAmount(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("txn_amount is required");
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid txn_amount: " + value);
        }
    }

    private boolean wageListExists(String wageList, Long boardId, Long employerId) {
        if (!hasText(wageList) || boardId == null || employerId == null) {
            return false;
        }
        String sql = """
                SELECT EXISTS(
                    SELECT 1
                      FROM payment_flow.worker_payment_receipts
                     WHERE receipt_number = :wageList
                       AND board_id = :boardId
                       AND employer_id = :employerId)
                """;
        Map<String, Object> params = new HashMap<>();
        params.put("wageList", wageList.trim());
        params.put("boardId", boardId);
        params.put("employerId", employerId);
        Boolean exists = jdbcTemplate.queryForObject(sql, params, Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private TenantAccessDao.TenantAccess requireTenantAccess() {
        try {
            var all = tenantAccessDao.getAccessibleTenants();
            log.info("Upload tenant access count={}", all != null ? all.size() : 0);
            if (all == null || all.isEmpty()) {
                throw new IllegalStateException("User has no tenant access (board/employer/toli) assigned for upload");
            }
            return all.stream()
                    .filter(ta -> Boolean.TRUE.equals(ta.canWrite))
                    .findFirst()
                    .map(ta -> {
                        log.debug("Using tenant access boardId={}, employerId={}, toliId={}, canWrite={}", ta.boardId,
                                ta.employerId, ta.toliId, ta.canWrite);
                        if (ta.boardId == null || ta.employerId == null) {
                            throw new IllegalStateException("User tenant access missing board or employer for upload");
                        }
                        return ta;
                    })
                    .orElseThrow(() -> new IllegalStateException(
                            "User has tenant access but none with write permission; cannot upload"));
        } catch (RuntimeException ex) {
            log.error("Failed to resolve tenant access for upload", ex);
            throw ex;
        }
    }

    public static class DuplicateFileException extends RuntimeException {
        private final Long existingUploadId;

        public DuplicateFileException(Long existingUploadId, String message) {
            super(message);
            this.existingUploadId = existingUploadId;
        }

        public Long getExistingUploadId() {
            return existingUploadId;
        }
    }
}
