package com.example.paymentreconciliation.service;

import com.example.paymentreconciliation.entity.ManualTransactionUpload;
import com.example.paymentreconciliation.entity.ImportRun;
import com.example.paymentreconciliation.entity.ImportError;
import com.example.paymentreconciliation.model.ManualTransactionUploadBatchResponse;
import com.example.paymentreconciliation.model.ManualTransactionUploadRequest;
import com.example.paymentreconciliation.model.ManualTransactionUploadResponse;
import com.example.paymentreconciliation.repository.ManualTransactionUploadRepository;
import com.example.paymentreconciliation.repository.ImportRunRepository;
import com.example.paymentreconciliation.repository.ImportErrorRepository;
import com.shared.utilities.logger.LoggerFactoryProvider;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ManualTransactionUploadService {

    private static final Logger log = LoggerFactoryProvider.getLogger(ManualTransactionUploadService.class);
    private static final String TXN_TYPE = "STATEMENT_UPLOAD";
    private static final Set<String> ALLOWED_TXN_TYPES = Set.of("NEFT", "RTGS", "IMPS");
    private static final long MAX_UPLOAD_BYTES = 10 * 1024 * 1024; // 10MB
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    private final ManualTransactionUploadRepository repository;
    private final ImportRunRepository importRunRepository;
    private final ImportErrorRepository importErrorRepository;

    public ManualTransactionUploadService(ManualTransactionUploadRepository repository,
            ImportRunRepository importRunRepository,
            ImportErrorRepository importErrorRepository) {
        this.repository = repository;
        this.importRunRepository = importRunRepository;
        this.importErrorRepository = importErrorRepository;
    }

    @Transactional
    public ManualTransactionUploadResponse create(ManualTransactionUploadRequest request, String createdBy) {
        return createInternal(request, createdBy, null);
    }

    private ManualTransactionUploadResponse createInternal(ManualTransactionUploadRequest request, String createdBy,
            ImportRun importRun) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        String txnRef = trim(request.getTxnRef());
        if (request.getTxnDate() == null) {
            throw new IllegalArgumentException("txnDate is required");
        }
        BigDecimal amount = request.getTxnAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("txnAmount must be greater than zero");
        }
        String drCrFlag = normalizeDrCrFlag(request.getDrCrFlag());
        String txnType = normalizeTxnType(request.getTxnType());
        String payer = trim(request.getPayer());

        if (requiresTxnRef(txnType) && !hasText(txnRef)) {
            throw new IllegalArgumentException("txn_ref is required for txn_type " + requiredTxnTypeLabel(txnType));
        }

        ManualTransactionUpload entity = new ManualTransactionUpload();
        entity.setImportRun(importRun);
        entity.setTxnRef(txnRef);
        entity.setTxnDate(request.getTxnDate());
        entity.setTxnAmount(amount);
        entity.setDrCrFlag(drCrFlag);
        entity.setTxnType(txnType);
        entity.setPayer(payer);
        entity.setDescription(trim(request.getDescription()));
        entity.setIsMapped(Boolean.FALSE);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setCreatedBy(trim(createdBy));
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setUpdatedBy(trim(createdBy));

        try {
            entity = repository.save(entity);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateManualTransactionException(
                    "Manual transaction already exists with same txn_ref, txn_date, and txn_amount", ex);
        }

        String responseTxnType = hasText(entity.getTxnType()) ? entity.getTxnType() : TXN_TYPE;
        return new ManualTransactionUploadResponse(
                entity.getId(),
                responseTxnType,
                entity.getTxnRef(),
                entity.getTxnDate(),
                entity.getTxnAmount(),
                entity.getDrCrFlag(),
                entity.getPayer(),
                entity.getDescription());
    }

    @Transactional
    public ManualTransactionUploadBatchResponse uploadCsv(MultipartFile file, String createdBy) {
        validateFile(file);
        byte[] content = toBytes(file);
        ImportRun importRun = createImportRun(file, content);
        List<ManualTransactionUploadResponse> inserted = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();

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
                int lineNo = (int) record.getRecordNumber();
                try {
                    ManualTransactionUploadRequest request = toRequest(record);
                    String key = request.getTxnRef() + "|" + request.getTxnDate() + "|"
                            + request.getTxnAmount().stripTrailingZeros().toPlainString();
                    if (seenKeys.contains(key)) {
                        throw new IllegalArgumentException("Duplicate row in file for txn_ref/txn_date/txn_amount");
                    }
                    seenKeys.add(key);
                    ManualTransactionUploadResponse response = createWithRun(request, createdBy, importRun);
                    inserted.add(response);
                } catch (DuplicateManualTransactionException ex) {
                    errors.add("line " + lineNo + ": duplicate in database - " + ex.getMessage());
                    persistImportError(importRun, "DUPLICATE", ex.getMessage(), lineNo);
                } catch (IllegalArgumentException ex) {
                    errors.add("line " + lineNo + ": " + ex.getMessage());
                    persistImportError(importRun, "VALIDATION", ex.getMessage(), lineNo);
                }
            }
        } catch (IOException ex) {
            throw new IllegalArgumentException("Failed to read CSV: " + ex.getMessage(), ex);
        }

        ManualTransactionUploadBatchResponse response = new ManualTransactionUploadBatchResponse();
        response.setRunId(importRun.getId());
        response.setTotalRows(inserted.size() + errors.size());
        response.setInsertedRows(inserted.size());
        response.setFailedRows(errors.size());
        response.setInserted(inserted);
        response.setErrors(errors);
        log.info("Manual transaction CSV upload processed rows={}, inserted={}, errors={}",
                response.getTotalRows(), response.getInsertedRows(), response.getFailedRows());
        finalizeImportRun(importRun, response.getTotalRows(), response.getInsertedRows(), response.getFailedRows(),
                errors.isEmpty() ? null : "Some rows failed");
        return response;
    }

    @Transactional(readOnly = true)
    public List<com.example.paymentreconciliation.model.ManualUploadRunResponse> listRuns(int size) {
        int pageSize = size > 0 ? Math.min(size, 200) : 50;
        var runs = importRunRepository.findByFileTypeOrderByIdDesc("MANUAL_TXN",
                org.springframework.data.domain.PageRequest.of(0, pageSize));
        List<com.example.paymentreconciliation.model.ManualUploadRunResponse> result = new ArrayList<>();
        for (ImportRun run : runs) {
            com.example.paymentreconciliation.model.ManualUploadRunResponse dto = new com.example.paymentreconciliation.model.ManualUploadRunResponse();
            dto.setId(run.getId());
            dto.setFilename(run.getFilename());
            dto.setFileHash(run.getFileHash());
            dto.setFileSizeBytes(run.getFileSizeBytes());
            dto.setReceivedAt(run.getReceivedAt());
            dto.setStatus(run.getStatus() != null ? run.getStatus().name() : null);
            dto.setErrorMessage(run.getErrorMessage());
            dto.setTotalRecords(run.getTotalRecords());
            dto.setProcessedRecords(run.getProcessedRecords());
            dto.setFailedRecords(run.getFailedRecords());
            result.add(dto);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public Page<com.example.paymentreconciliation.model.ManualTransactionUploadDetailResponse> listTransactionsByRun(
            Long runId, int page, int size) {
        if (runId == null) {
            throw new IllegalArgumentException("runId is required");
        }
        int pageNumber = page >= 0 ? page : 0;
        int pageSize = size > 0 ? Math.min(size, 200) : 50;
        Pageable pageable = PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Direction.DESC, "id"));
        return repository.findByImportRunId(runId, pageable)
                .map(row -> {
                    com.example.paymentreconciliation.model.ManualTransactionUploadDetailResponse dto = new com.example.paymentreconciliation.model.ManualTransactionUploadDetailResponse();
                    dto.setId(row.getId());
                    dto.setImportRunId(runId);
                    dto.setTxnRef(row.getTxnRef());
                    dto.setTxnDate(row.getTxnDate());
                    dto.setTxnAmount(row.getTxnAmount());
                    dto.setDrCrFlag(row.getDrCrFlag());
                    dto.setTxnType(row.getTxnType());
                    dto.setPayer(row.getPayer());
                    dto.setDescription(row.getDescription());
                    dto.setIsMapped(row.getIsMapped());
                    dto.setCreatedAt(row.getCreatedAt());
                    dto.setCreatedBy(row.getCreatedBy());
                    return dto;
                });
    }

    private ManualTransactionUploadResponse createWithRun(ManualTransactionUploadRequest request, String createdBy,
            ImportRun importRun) {
        return createInternal(request, createdBy, importRun);
    }

    private ManualTransactionUploadRequest toRequest(CSVRecord record) {
        ManualTransactionUploadRequest request = new ManualTransactionUploadRequest();
        request.setTxnRef(optionalAny(record, "txn_ref"));
        request.setTxnDate(parseDate(requiredAny(record, "tran_date", "txn_date")));
        request.setTxnAmount(parseAmount(requiredAny(record, "amount", "txn_amount")));
        request.setDrCrFlag(requiredAny(record, "debit_credit_flag", "dr_cr_flag"));
        request.setTxnType(optionalAny(record, "txn_type"));
        request.setPayer(optionalAny(record, "payer"));
        request.setDescription(optional(record, "description"));
        return request;
    }

    private BigDecimal parseAmount(String value) {
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("amount must be a valid decimal amount");
        }
    }

    private LocalDate parseDate(String value) {
        if (!hasText(value)) {
            throw new IllegalArgumentException("tran_date is required");
        }
        String trimmed = value.trim();
        DateTimeFormatter[] fmts = new DateTimeFormatter[] { ISO_DATE, DateTimeFormatter.ofPattern("dd-MM-uuuu") };
        for (DateTimeFormatter fmt : fmts) {
            try {
                return LocalDate.parse(trimmed, fmt);
            } catch (DateTimeParseException ignored) {
                // try next
            }
        }
        throw new IllegalArgumentException("tran_date must be in format yyyy-MM-dd or dd-MM-yyyy");
    }

    private String required(CSVRecord record, String column) {
        String value = record.get(column);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(column + " is required");
        }
        return value.trim();
    }

    private String optional(CSVRecord record, String column) {
        String value = record.isMapped(column) ? record.get(column) : null;
        return value != null ? value.trim() : null;
    }

    private String requiredAny(CSVRecord record, String... columns) {
        for (String column : columns) {
            if (record.isMapped(column)) {
                String value = record.get(column);
                if (value != null && !value.trim().isEmpty()) {
                    return value.trim();
                }
            }
        }
        throw new IllegalArgumentException(String.join("/", columns) + " is required");
    }

    private String optionalAny(CSVRecord record, String... columns) {
        for (String column : columns) {
            if (record.isMapped(column)) {
                String value = record.get(column);
                if (value != null && !value.trim().isEmpty()) {
                    return value.trim();
                }
            }
        }
        return null;
    }

    private void persistImportError(ImportRun importRun, String code, String message, Integer lineNo) {
        ImportError error = new ImportError();
        error.setImportRun(importRun);
        error.setCode(code);
        error.setMessage(message);
        error.setLineNo(lineNo);
        importErrorRepository.save(error);
    }

    private ImportRun createImportRun(MultipartFile file, byte[] content) {
        String hash = sha256(content);
        // Reuse existing run if same file hash already processed/started
        var existing = importRunRepository.findByFileHash(hash);
        if (existing.isPresent()) {
            return existing.get();
        }

        ImportRun importRun = new ImportRun();
        importRun.setFilename(file.getOriginalFilename());
        importRun.setFileHash(hash);
        importRun.setFileSizeBytes(file.getSize());
        importRun.setReceivedAt(LocalDateTime.now());
        importRun.setFileType("MANUAL_TXN");
        importRun.setTotalRecords(0);
        importRun.setProcessedRecords(0);
        importRun.setFailedRecords(0);
        importRun.setStatus(ImportRun.Status.NEW);
        try {
            return importRunRepository.save(importRun);
        } catch (DataAccessException ex) {
            throw new IllegalArgumentException("Failed to create import run entry: " + ex.getMessage(), ex);
        }
    }

    private void finalizeImportRun(ImportRun importRun, int total, int inserted, int failed, String errorMessage) {
        importRun.setTotalRecords(total);
        importRun.setProcessedRecords(inserted);
        importRun.setFailedRecords(failed);
        importRun.setStatus(failed > 0 ? ImportRun.Status.PARTIAL : ImportRun.Status.IMPORTED);
        importRun.setErrorMessage(errorMessage);
        importRunRepository.save(importRun);
    }

    private boolean requiresTxnRef(String txnType) {
        return txnType != null && ALLOWED_TXN_TYPES.contains(txnType);
    }

    private String requiredTxnTypeLabel(String txnType) {
        return txnType != null ? txnType : "UNKNOWN";
    }

    private String sha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
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

    private String normalizeDrCrFlag(String rawFlag) {
        if (!hasText(rawFlag)) {
            throw new IllegalArgumentException("drCrFlag is required");
        }
        String normalized = rawFlag.trim().toUpperCase();
        if ("C".equals(normalized) || "D".equals(normalized) || "CR".equals(normalized) || "DR".equals(normalized)) {
            return normalized;
        }
        throw new IllegalArgumentException("drCrFlag must be C, D, CR or DR");
    }

    private String normalizeTxnType(String rawTxnType) {
        if (!hasText(rawTxnType)) {
            return null;
        }
        String normalized = rawTxnType.trim().toUpperCase();
        if (!ALLOWED_TXN_TYPES.contains(normalized)) {
            throw new IllegalArgumentException("txn_type must be one of NEFT, RTGS or IMPS");
        }
        return normalized;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String trim(String value) {
        return value != null ? value.trim() : null;
    }

    public static class DuplicateManualTransactionException extends RuntimeException {
        public DuplicateManualTransactionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
