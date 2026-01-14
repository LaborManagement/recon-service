package com.example.paymentreconciliation.service;

import com.shared.utilities.logger.LoggerFactoryProvider;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ManualTransactionPdfConversionService {

    private static final Logger log = LoggerFactoryProvider.getLogger(ManualTransactionPdfConversionService.class);
    private static final long MAX_UPLOAD_BYTES = 10 * 1024 * 1024; // 10MB
    private static final Set<String> ALLOWED_BANKS = Set.of("pnb", "bom", "bob");
    private static final Set<String> ALLOWED_STRATEGIES = Set.of("lines", "text");
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);

    private final String pythonExecutable;
    private final Path scriptsDir;

    public ManualTransactionPdfConversionService(
            @Value("${recon.pdf.python.executable:python3}") String pythonExecutable) {
        this.pythonExecutable = pythonExecutable;
        this.scriptsDir = resolveScriptsDirectory();
    }

    public CsvConversionResult convertToCsv(MultipartFile file, String bank, String strategy) {
        validate(file, bank, strategy);
        Path pdfPath = null;
        Path csvPath = null;
        try {
            pdfPath = Files.createTempFile("manual-upload-", ".pdf");
            csvPath = Files.createTempFile("manual-upload-", ".csv");
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, pdfPath, StandardCopyOption.REPLACE_EXISTING);
            }

            List<String> command = buildCommand(bank, strategy, pdfPath, csvPath);
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(scriptsDir.toFile());
            pb.redirectErrorStream(true);

            long start = System.nanoTime();
            Process process = pb.start();
            boolean finished = process.waitFor(DEFAULT_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("PDF conversion timed out");
            }
            if (process.exitValue() != 0) {
                throw new IllegalStateException("PDF conversion failed: " + trimOutput(output));
            }
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            log.info("PDF to CSV conversion completed in {} ms (bank={}, strategy={})", durationMs, bank, strategy);

            byte[] csvBytes = Files.readAllBytes(csvPath);
            String downloadName = deriveCsvFileName(file.getOriginalFilename());
            return new CsvConversionResult(csvBytes, downloadName);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Failed to process uploaded PDF: " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("PDF conversion interrupted", ex);
        } finally {
            deleteQuietly(pdfPath);
            deleteQuietly(csvPath);
        }
    }

    private void validate(MultipartFile file, String bank, String strategy) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("PDF file is required");
        }
        if (file.getSize() > MAX_UPLOAD_BYTES) {
            throw new IllegalArgumentException("File exceeds max allowed size of " + MAX_UPLOAD_BYTES + " bytes");
        }
        if (bank != null && !bank.isBlank() && !ALLOWED_BANKS.contains(bank.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("bank must be one of: " + ALLOWED_BANKS);
        }
        if (strategy != null && !ALLOWED_STRATEGIES.contains(strategy.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("strategy must be one of: " + ALLOWED_STRATEGIES);
        }
    }

    private List<String> buildCommand(String bank, String strategy, Path pdfPath, Path csvPath) {
        List<String> command = new ArrayList<>();
        command.add(pythonExecutable);
        command.add("pdf_to_csv.py");
        command.add(pdfPath.toString());
        command.add(csvPath.toString());
        String normalizedStrategy = strategy != null ? strategy.toLowerCase(Locale.ROOT) : "lines";
        if (ALLOWED_STRATEGIES.contains(normalizedStrategy)) {
            command.add("--strategy");
            command.add(normalizedStrategy);
        }
        if (bank != null && !bank.isBlank()) {
            String normalizedBank = bank.toLowerCase(Locale.ROOT);
            if (ALLOWED_BANKS.contains(normalizedBank)) {
                command.add("--bank");
                command.add(normalizedBank);
            }
        }
        return command;
    }

    private Path resolveScriptsDirectory() {
        Path classpathDir = locateClasspathScriptsDirectory();
        if (classpathDir != null) {
            log.info("Using classpath PDF script directory: {}", classpathDir);
            return classpathDir;
        }

        try {
            Path tempDir = Files.createTempDirectory("recon-pdf-scripts-");
            copyScript(tempDir, "pdf_to_csv.py");
            copyScript(tempDir, "pnb_parser.py");
            copyScript(tempDir, "bom_parser.py");
            copyScript(tempDir, "bob_parser.py");
            log.info("Copied PDF scripts to temp directory: {}", tempDir);
            return tempDir;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to prepare PDF parsing scripts", ex);
        }
    }

    private void copyScript(Path targetDir, String fileName) throws IOException {
        ClassPathResource resource = new ClassPathResource("scripts/" + fileName);
        if (!resource.exists()) {
            throw new IllegalStateException("Missing script resource: " + fileName);
        }
        try (InputStream in = resource.getInputStream()) {
            Files.copy(in, targetDir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Path locateClasspathScriptsDirectory() {
        try {
            ClassPathResource resource = new ClassPathResource("scripts");
            if (resource.exists() && resource.getFile().isDirectory()) {
                Path path = resource.getFile().toPath();
                validateScriptDirectory(path, "classpath");
                return path;
            }
        } catch (IOException ex) {
            // Resource may be inside a jar; ignore and fallback to temp copy.
        }
        return null;
    }

    private void validateScriptDirectory(Path path, String label) {
        if (!Files.isDirectory(path)) {
            throw new IllegalStateException("PDF script " + label + " path is not a directory: " + path);
        }
        for (String required : List.of("pdf_to_csv.py", "pnb_parser.py", "bom_parser.py", "bob_parser.py")) {
            if (!Files.isRegularFile(path.resolve(required))) {
                throw new IllegalStateException("Missing required script " + required + " in " + label + " path: " + path);
            }
        }
    }

    private void deleteQuietly(Path path) {
        if (path != null) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
                // best-effort cleanup
            }
        }
    }

    private String deriveCsvFileName(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            return "converted.csv";
        }
        int lastDot = originalName.lastIndexOf('.');
        String base = lastDot > 0 ? originalName.substring(0, lastDot) : originalName;
        return base + ".csv";
    }

    private String trimOutput(String output) {
        if (output == null) {
            return "";
        }
        String trimmed = output.strip();
        return trimmed.length() > 1000 ? trimmed.substring(0, 1000) + "..." : trimmed;
    }

    public record CsvConversionResult(byte[] csvBytes, String downloadFileName) {
    }
}
