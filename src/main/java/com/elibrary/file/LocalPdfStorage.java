package com.elibrary.file;

import com.elibrary.common.BusinessException;
import com.elibrary.config.LibraryProperties;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.UUID;

@Component
public class LocalPdfStorage {

    private static final String PDF_EXTENSION = ".pdf";
    private static final long BYTES_PER_MB = 1024L * 1024L;

    private final LibraryProperties properties;
    private Path storageDir;
    private long maxSizeBytes;

    public LocalPdfStorage(LibraryProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() throws IOException {
        storageDir = Paths.get(properties.getPdf().getStorageDir()).toAbsolutePath().normalize();
        Files.createDirectories(storageDir);
        maxSizeBytes = properties.getPdf().getMaxSizeMb() * BYTES_PER_MB;
    }

    /**
     * Package-visible for unit tests that construct without Spring lifecycle.
     */
    void initForTest(Path dir, int maxSizeMb) throws IOException {
        storageDir = dir.toAbsolutePath().normalize();
        Files.createDirectories(storageDir);
        maxSizeBytes = maxSizeMb * BYTES_PER_MB;
    }

    public String save(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请上传 PDF 文件");
        }
        try {
            return save(file.getInputStream(), file.getOriginalFilename(), file.getSize(), file.getContentType());
        } catch (IOException e) {
            throw new BusinessException("PDF 保存失败");
        }
    }

    public String save(byte[] data, String originalName, String contentType) {
        if (data == null) {
            throw new BusinessException("请上传 PDF 文件");
        }
        return save(new java.io.ByteArrayInputStream(data), originalName, data.length, contentType);
    }

    public String save(InputStream inputStream, String originalName, long size, String contentType) {
        validatePdf(originalName, contentType, size);
        String storedName = UUID.randomUUID().toString() + PDF_EXTENSION;
        Path target = resolveSafe(storedName);
        try {
            try (InputStream in = inputStream; OutputStream out = Files.newOutputStream(target)) {
                copy(in, out);
            }
        } catch (IOException e) {
            throw new BusinessException("PDF 保存失败");
        }
        return storedName;
    }

    public InputStream open(String relativeName) {
        Path path = resolveSafe(relativeName);
        if (!Files.isRegularFile(path)) {
            throw new BusinessException("PDF 文件不存在");
        }
        try {
            return Files.newInputStream(path);
        } catch (IOException e) {
            throw new BusinessException("PDF 读取失败");
        }
    }

    public Resource asResource(String relativeName) {
        Path path = resolveSafe(relativeName);
        if (!Files.isRegularFile(path)) {
            throw new BusinessException("PDF 文件不存在");
        }
        return new FileSystemResource(path.toFile());
    }

    Path resolveSafe(String relativeName) {
        if (relativeName == null || relativeName.trim().isEmpty()) {
            throw new BusinessException("非法文件路径");
        }
        String name = relativeName.trim();
        if (name.contains("/") || name.contains("\\") || name.contains("..")) {
            throw new BusinessException("非法文件路径");
        }
        Path resolved = storageDir.resolve(name).normalize();
        if (!resolved.startsWith(storageDir)) {
            throw new BusinessException("非法文件路径");
        }
        return resolved;
    }

    private void validatePdf(String originalName, String contentType, long size) {
        if (originalName == null || !originalName.toLowerCase(Locale.ROOT).endsWith(PDF_EXTENSION)) {
            throw new BusinessException("仅支持 PDF 文件");
        }
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).contains("pdf")) {
            throw new BusinessException("仅支持 PDF 文件");
        }
        if (size <= 0 || size > maxSizeBytes) {
            throw new BusinessException("PDF 文件大小超出限制");
        }
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) >= 0) {
            out.write(buffer, 0, n);
        }
    }

    Path getStorageDir() {
        return storageDir;
    }
}
