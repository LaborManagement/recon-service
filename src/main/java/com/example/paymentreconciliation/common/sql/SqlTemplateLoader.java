package com.example.paymentreconciliation.common.sql;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.FileCopyUtils;

/**
 * Loads SQL templates from the classpath and caches them so analysts can edit
 * .sql files without needing Java changes.
 */
@Component
public class SqlTemplateLoader {

    private final ResourceLoader resourceLoader;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public SqlTemplateLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public String load(String location) {
        return cache.computeIfAbsent(location, this::readResource);
    }

    private String readResource(String location) {
        Resource resource = resourceLoader.getResource("classpath:" + location);
        if (!resource.exists()) {
            throw new IllegalArgumentException("SQL template not found on classpath: " + location);
        }
        try (var reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
            return FileCopyUtils.copyToString(reader);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read SQL template: " + location, ex);
        }
    }
}
