
    package com.example.paymentreconciliation.service;

import com.shared.utilities.logger.LoggerFactoryProvider;
import org.slf4j.Logger;

import java.io.File;
import java.util.List;
import java.util.ArrayList;

/**
 * MT940 Ingestion Service
 * Implements the processing logic as per the design spec (see mt_940_ingestion_spec.md).
 */
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.paymentreconciliation.config.Mt940IngestionProperties;

import com.example.paymentreconciliation.entity.*;
import com.example.paymentreconciliation.repository.*;

@Service
public class Mt940IngestionService extends BaseIngestionService {
    private static final Logger log = LoggerFactoryProvider.getLogger(Mt940IngestionService.class);
    @Autowired
    private BankAccountRepository bankAccountRepository;
    @Autowired
    private ImportRunRepository importRunRepository;
    @Autowired
    private StatementFileRepository statementFileRepository;
    @Autowired
    private StatementBalanceRepository statementBalanceRepository;
    @Autowired
    private StatementTransactionRepository statementTransactionRepository;

    @Autowired
    private RawStatementLineRepository rawStatementLineRepository;
    @Autowired
    private Transaction86SegmentRepository transaction86SegmentRepository;

    @Autowired
    private ImportErrorRepository importErrorRepository;

    @Autowired
    private Mt940IngestionProperties mt940Props;

    @Override
    protected String getInboxDir() {
        return mt940Props.getInboxDir();
    }

    @Override
    protected String getProcessingDir() {
        return mt940Props.getProcessingDir();
    }

    @Override
    protected String getArchiveDir() {
        return mt940Props.getArchiveDir();
    }

    @Override
    protected String getQuarantineDir() {
        return mt940Props.getQuarantineDir();
    }

    @Override
    protected String getFileExtension() {
        return ".mt940";
    }

    /**
     * Main entry point for polling and processing files.
     */
    @Override
    public void pollAndProcessInbox() {
        log.info("Polling MT940 inbox directory: {}", mt940Props.getInboxDir());
        List<File> files = discoverStableFiles(mt940Props.getInboxDir());
        log.info("Discovered {} stable file(s) for ingestion", files.size());
        for (File file : files) {
            try {
                log.info("Processing file: {}", file.getAbsolutePath());
                processFile(file);
            } catch (Exception e) {
                log.error("Error processing file: {}. Moving to quarantine. Error: {}", file.getAbsolutePath(), e.getMessage(), e);
                moveToQuarantine(file, "Unhandled error: " + e.getMessage());
            }
        }
    }

    /**
     * Process a single file: move, hash, decompress, parse, validate, persist, archive/quarantine.
     */
    @Override
    protected void processFile(File file) {
        log.info("Starting processing for file: {}", file.getAbsolutePath());
        // 1. Move file to PROCESSING with GUID suffix
    File processingFile = moveToProcessing(file);
    log.debug("Moved file to processing: {}", processingFile.getAbsolutePath());
        if (processingFile.length() > MAX_FILE_SIZE_BYTES) {
            log.warn("File exceeds max size policy ({} bytes): {}", processingFile.length(), processingFile.getAbsolutePath());
            moveToQuarantine(processingFile, "File exceeds max size policy");
            return;
        }

        // 2. Compute sha256 and size, check for duplicates
    String fileHash = computeSha256(processingFile);
    log.debug("Computed SHA-256 hash for file {}: {}", processingFile.getName(), fileHash);
        long fileSize = processingFile.length();
        if (isDuplicate(fileHash)) {
            log.warn("Duplicate file detected: {} (hash={})", processingFile.getName(), fileHash);
            moveToArchive(processingFile);
            return;
        }

        // 3. Decompress if needed
    List<File> mt940Files = decompressIfNeeded(processingFile);
    log.debug("Decompressed/collected {} MT940 file(s) from: {}", mt940Files.size(), processingFile.getName());

        // 4. For each MT940 document: parse, validate, persist (one DB transaction per file)
        boolean allSuccess = true;
        for (File mt940 : mt940Files) {
            try {
                log.info("Parsing and persisting MT940 file: {}", mt940.getAbsolutePath());
                parseValidatePersist(mt940, fileHash, fileSize);
            } catch (Exception e) {
                allSuccess = false;
                log.error("Error parsing/persisting MT940 file: {}. Error: {}", mt940.getAbsolutePath(), e.getMessage(), e);
                persistImportError(null, fileHash, "PARSING_ERROR", "Failed to ingest " + mt940.getName() + ": " + e.getMessage(), null, null);
            }
        }

        // 5. On success: move original to ARCHIVE/YYYY/MM/DD
        // 6. On failure: move to QUARANTINE and persist error details
        if (allSuccess) {
            log.info("Successfully processed file: {}. Moving to archive.", processingFile.getAbsolutePath());
            moveToArchive(processingFile);
        } else {
            log.warn("Processing failed for file: {}. Moving to quarantine.", processingFile.getAbsolutePath());
            moveToQuarantine(processingFile, "One or more statements failed to import");
        }
    }

    /**
     * Check DB for existing file hash (stubbed to always return false).
     * Replace with actual DB lookup for import_run.file_hash.
     */
    private boolean isDuplicate(String fileHash) {
        log.debug("Checking for duplicate file hash: {}", fileHash);
        return importRunRepository.findByFileHash(fileHash).isPresent();
    }

    /**
     * If file is a .zip, extract to temp subfolder under PROCESSING, return list of .mt940/.sta files.
     */
    private List<File> decompressIfNeeded(File file) {
        log.debug("Checking if file needs decompression: {}", file.getAbsolutePath());
        String name = file.getName().toLowerCase();
        if (name.endsWith(".zip")) {
            File tempDir = new File(mt940Props.getProcessingDir(), "tmp_" + System.currentTimeMillis());
            if (!tempDir.mkdirs()) {
                throw new RuntimeException("Failed to create temp dir: " + tempDir.getAbsolutePath());
            }
            List<File> extracted = new ArrayList<>();
            try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(new java.io.FileInputStream(file))) {
                java.util.zip.ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    String entryName = entry.getName();
                    if (entry.isDirectory()) continue;
                    if (!(entryName.endsWith(".mt940") || entryName.endsWith(".sta"))) continue;
                    File outFile = new File(tempDir, new File(entryName).getName());
                    try (java.io.FileOutputStream fos = new java.io.FileOutputStream(outFile)) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                    extracted.add(outFile);
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to decompress zip: " + file.getAbsolutePath(), e);
            }
            if (extracted.isEmpty()) {
                throw new RuntimeException("No MT940/STA files found in zip: " + file.getName());
            }
            return extracted;
        } else {
            return List.of(file);
        }
    }

    /**
     * Parse, validate, and persist all statements/transactions in a DB transaction.
     * This is a stub: actual parsing/validation logic should be implemented as per spec.
     */
    private void parseValidatePersist(File mt940File, String fileHash, long fileSize) {
        log.debug("Parsing and validating MT940 file: {}", mt940File.getAbsolutePath());
        Mt940Parser parser = new Mt940Parser();
        List<Mt940Parser.Statement> statements;
        try {
            statements = parser.parse(mt940File);
        } catch (Mt940Parser.Mt940ParseException e) {
            throw new RuntimeException("MT940 parse error: " + e.getMessage(), e);
        }
        persistParsedStatements(statements, mt940File.getName(), fileHash, fileSize);
    }

    @Transactional
    public void persistParsedStatements(List<Mt940Parser.Statement> statements, String filename, String fileHash, long fileSize) {
        log.info("Persisting parsed statements for file: {} (hash={})", filename, fileHash);
        // 1. Create ImportRun
        ImportRun importRun = new ImportRun();
        importRun.setFilename(filename);
        importRun.setFileHash(fileHash);
        importRun.setFileSizeBytes(fileSize);
        importRun.setReceivedAt(java.time.LocalDateTime.now());
        importRun.setFileType("MT940");
        importRun.setStatus(ImportRun.Status.PARSED);
        importRunRepository.save(importRun);

        int totalStatements = statements.size();
        int processedStatements = 0;
        int failedStatements = 0;

        for (Mt940Parser.Statement stmt : statements) {
            // Validation: accountNo, currency, balances, transactions
            if (stmt.accountNo == null || stmt.accountNo.trim().isEmpty()) {
                log.error("Statement missing account number. Skipping statement: {}", stmt);
                persistImportError(importRun, fileHash, "VALIDATION_ERROR", "Missing account number in statement: " + stmt, null, null);
                failedStatements++;
                continue;
            }
            if (stmt.currency == null || stmt.currency.trim().isEmpty()) {
                log.error("Statement missing currency. Skipping statement: {}", stmt);
                persistImportError(importRun, fileHash, "VALIDATION_ERROR", "Missing currency in statement: " + stmt, null, null);
                failedStatements++;
                continue;
            }
            if (stmt.openingBalance == null || stmt.closingBalance == null) {
                log.error("Statement missing opening/closing balance. Skipping statement: {}", stmt);
                persistImportError(importRun, fileHash, "VALIDATION_ERROR", "Missing opening/closing balance in statement: " + stmt, null, null);
                failedStatements++;
                continue;
            }
            if (stmt.openingBalance.amount == null || stmt.closingBalance.amount == null) {
                log.error("Statement missing opening/closing balance amount. Skipping statement: {}", stmt);
                persistImportError(importRun, fileHash, "VALIDATION_ERROR", "Missing opening/closing balance amount in statement: " + stmt, null, null);
                failedStatements++;
                continue;
            }
            if (stmt.transactions == null || stmt.transactions.isEmpty()) {
                log.error("Statement missing transactions. Skipping statement: {}", stmt);
                persistImportError(importRun, fileHash, "VALIDATION_ERROR", "Missing transactions in statement: " + stmt, null, null);
                failedStatements++;
                continue;
            }

            // Normalize and validate currency (must be 3-letter code)
            String normalizedCurrency = normalizeCurrency(stmt.currency);
            String openingCurrency = normalizeCurrency(stmt.openingBalance.currency);
            String closingCurrency = normalizeCurrency(stmt.closingBalance.currency);
            if (normalizedCurrency == null || openingCurrency == null || closingCurrency == null) {
                log.error("Invalid currency code (must be 3 letters). stmt={}, open={}, close={}",
                        stmt.currency, stmt.openingBalance.currency, stmt.closingBalance.currency);
                persistImportError(importRun, fileHash, "VALIDATION_ERROR",
                        "Invalid currency code (must be 3 letters)", null, null);
                failedStatements++;
                continue;
            }
            stmt.currency = normalizedCurrency;
            stmt.openingBalance.currency = openingCurrency;
            stmt.closingBalance.currency = closingCurrency;

            // 2. Find or create BankAccount
            log.debug("Finding or creating BankAccount for accountNo={}, currency={}", stmt.accountNo, stmt.currency);
            BankAccount acct = bankAccountRepository.findByAccountNoAndCurrency(stmt.accountNo, stmt.currency)
                .orElseGet(() -> {
                    BankAccount ba = new BankAccount();
                    ba.setAccountNo(stmt.accountNo);
                    ba.setCurrency(stmt.currency);
                    ba.setIsActive(true);
                    return bankAccountRepository.save(ba);
                });

            // 3. Validate currency match
            if (!stmt.openingBalance.currency.equals(stmt.currency) || !stmt.closingBalance.currency.equals(stmt.currency)) {
                log.error("Currency mismatch in statement: {}", stmt.stmtRef20);
                persistImportError(importRun, fileHash, "VALIDATION_ERROR", "Currency mismatch in statement: " + stmt.stmtRef20, null, null);
                failedStatements++;
                continue;
            }

            // 4. Validate opening + sum(signed transactions) == closing (allow small rounding delta)
            java.math.BigDecimal opening = new java.math.BigDecimal(stmt.openingBalance.amount.replace(",", "."));
            java.math.BigDecimal closing = new java.math.BigDecimal(stmt.closingBalance.amount.replace(",", "."));
            java.math.BigDecimal sumTxns = java.math.BigDecimal.ZERO;
            for (Mt940Parser.Transaction txn : stmt.transactions) {
                sumTxns = sumTxns.add(new java.math.BigDecimal(txn.signedAmount.replace(",", ".")));
            }
            java.math.BigDecimal expectedClosing = opening.add(sumTxns);
            if (expectedClosing.subtract(closing).abs().compareTo(new java.math.BigDecimal("0.02")) > 0) {
                log.error("Opening + sum(transactions) != closing for statement: {}", stmt.stmtRef20);
                persistImportError(importRun, fileHash, "VALIDATION_ERROR", "Opening + sum(transactions) != closing for statement: " + stmt.stmtRef20, null, null);
                failedStatements++;
                continue;
            }

            // Now process the statement since validations passed
            processedStatements++;

            // 5. Create StatementFile
            StatementFile sf = new StatementFile();
            sf.setImportRun(importRun);
            sf.setBankAccount(acct);
            sf.setStmtRef20(stmt.stmtRef20);
            sf.setSeq28c(stmt.seq28c);
            sf.setStatementDate(java.time.LocalDate.now()); // You may want to parse actual date
            sf.setOpeningDc(stmt.openingBalance.dc);
            sf.setOpeningAmount(opening);
            sf.setClosingDc(stmt.closingBalance.dc);
            sf.setClosingAmount(closing);
            sf.setCurrency(stmt.currency);
            sf.setIsInterim(stmt.isInterim);
            sf.setCreatedAt(java.time.LocalDateTime.now());
            statementFileRepository.save(sf);

            // 6. Persist balances
            StatementBalance ob = new StatementBalance();
            ob.setStatementFile(sf);
            ob.setBalType("OPENING");
            ob.setDc(stmt.openingBalance.dc);
            ob.setBalDate(java.time.LocalDate.now());
            ob.setCurrency(stmt.openingBalance.currency);
            ob.setAmount(opening);
            statementBalanceRepository.save(ob);

            StatementBalance cb = new StatementBalance();
            cb.setStatementFile(sf);
            cb.setBalType("CLOSING");
            cb.setDc(stmt.closingBalance.dc);
            cb.setBalDate(java.time.LocalDate.now());
            cb.setCurrency(stmt.closingBalance.currency);
            cb.setAmount(closing);
            statementBalanceRepository.save(cb);

            // Other balances
            if (stmt.otherBalances != null) {
                for (Mt940Parser.Balance bal : stmt.otherBalances) {
                    bal.currency = normalizeCurrency(bal.currency);
                    if (bal.currency == null) {
                        log.warn("Skipping balance with invalid currency: {}", bal);
                        continue;
                    }
                    StatementBalance b = new StatementBalance();
                    b.setStatementFile(sf);
                    b.setBalType(bal.type);
                    b.setDc(bal.dc);
                    b.setBalDate(java.time.LocalDate.now());
                    b.setCurrency(bal.currency);
                    b.setAmount(new java.math.BigDecimal(bal.amount.replace(",", ".")));
                    statementBalanceRepository.save(b);
                }
            }

            // 7. Persist transactions
            for (Mt940Parser.Transaction txn : stmt.transactions) {
                txn.currency = normalizedCurrency;
                StatementTransaction st = new StatementTransaction();
                st.setStatementFile(sf);
                st.setLineNo(txn.lineNo);
                st.setValueDate(java.time.LocalDate.now()); // Parse actual date if available
                st.setEntryDate(null); // Parse if available
                st.setDc(txn.dc);
                st.setAmount(new java.math.BigDecimal(txn.amount.replace(",", ".")));
                st.setSignedAmount(new java.math.BigDecimal(txn.signedAmount.replace(",", ".")));
                st.setCurrency(normalizedCurrency);
                st.setTxnTypeCode(txn.txnTypeCode);
                st.setBankReference(txn.bankReference);
                st.setCustomerReference(txn.customerReference);
                st.setEntryReference(txn.entryReference);
                st.setNarrative(txn.narrative);
                st.setNarrativeTokens(null); // Optionally serialize txn.narrativeTokens
                st.setExtIdempotencyHash(txn.extIdempotencyHash);
                st.setCreatedAt(java.time.LocalDateTime.now());
                statementTransactionRepository.save(st);

                // Persist Transaction86Segment for each narrative token (or the full narrative if no tokens)
                if (txn.narrativeTokens != null && !txn.narrativeTokens.isEmpty()) {
                    int segSeq = 1;
                    for (var entry : txn.narrativeTokens.entrySet()) {
                        Transaction86Segment seg = new Transaction86Segment();
                        seg.setStatementTransaction(st);
                        seg.setSegKey(entry.getKey());
                        seg.setSegValue(entry.getValue());
                        seg.setSegSeq(segSeq++);
                        transaction86SegmentRepository.save(seg);
                    }
                } else if (txn.narrative != null) {
                    Transaction86Segment seg = new Transaction86Segment();
                    seg.setStatementTransaction(st);
                    seg.setSegKey("FULL");
                    seg.setSegValue(txn.narrative);
                    seg.setSegSeq(1);
                    transaction86SegmentRepository.save(seg);
                }

                // Persist RawStatementLine if available (from stmt.rawLines)
                if (stmt.rawLines != null) {
                    for (Mt940Parser.RawLine rawLine : stmt.rawLines) {
                        if (rawLine.lineNo == txn.lineNo) { // match by line number
                            RawStatementLine rsl = new RawStatementLine();
                            rsl.setStatementFile(sf);
                            rsl.setLineNo(rawLine.lineNo);
                            rsl.setTag(rawLine.tag);
                            rsl.setRawText(rawLine.rawText);
                            rawStatementLineRepository.save(rsl);
                        }
                    }
                }
            }
        }

        // Update ImportRun with counts and final status
        importRun.setTotalRecords(totalStatements);
        importRun.setProcessedRecords(processedStatements);
        importRun.setFailedRecords(failedStatements);
        if (processedStatements > 0 && failedStatements == 0) {
            importRun.setStatus(ImportRun.Status.IMPORTED);
        } else if (processedStatements > 0) {
            importRun.setStatus(ImportRun.Status.PARTIAL);
        } else {
            importRun.setStatus(ImportRun.Status.FAILED);
        }
        importRunRepository.save(importRun);
    }
    
    private String normalizeCurrency(String currency) {
        if (currency == null) return null;
        // Keep only letters; MT940 currency should be 3-letter ISO code, but some feeds add trailing chars
        String lettersOnly = currency.trim().toUpperCase().replaceAll("[^A-Z]", "");
        if (lettersOnly.length() < 3) {
            return null;
        }
        return lettersOnly.substring(0, 3);
    }


    /**
     * Persist import error details (stub).
     */
    private void persistImportError(ImportRun importRun,
                                    String fileHash,
                                    String code,
                                    String errorMsg,
                                    StatementFile statementFile,
                                    Integer lineNo) {
        ImportRun targetRun = importRun;
        if (targetRun == null && fileHash != null) {
            targetRun = importRunRepository.findByFileHash(fileHash).orElse(null);
        }

        if (targetRun == null) {
            log.warn("Unable to persist import error because import run could not be resolved (hash={}, message={})",
                    fileHash, errorMsg);
            return;
        }

        ImportError error = new ImportError();
        error.setImportRun(targetRun);
        error.setStatementFile(statementFile);
        error.setLineNo(lineNo);
        error.setCode(code != null ? code : "VALIDATION_ERROR");
        error.setMessage(errorMsg);
        importErrorRepository.save(error);
    }

    // Additional methods for reporting, operator checks, idempotency, etc. can be added here
}
