package com.elibrary.file;

import com.elibrary.common.BusinessException;
import com.elibrary.config.LibraryProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalPdfStorageTest {

    @TempDir
    Path tempDir;

    private LocalPdfStorage storage;

    @BeforeEach
    void setUp() throws Exception {
        LibraryProperties properties = new LibraryProperties();
        properties.getPdf().setStorageDir(tempDir.toString());
        properties.getPdf().setMaxSizeMb(5);
        storage = new LocalPdfStorage(properties);
        storage.initForTest(tempDir, 5);
    }

    @Test
    @DisplayName("保存-当文件为PDF且未超限时-应写入storageDir下安全文件名")
    void save_whenValidPdf_shouldWriteUnderStorageDir() throws Exception {
        // Arrange
        byte[] data = "%PDF-1.4 test".getBytes(StandardCharsets.UTF_8);

        // Act
        String relative = storage.save(data, "sample.PDF", "application/pdf");

        // Assert
        assertThat(relative).endsWith(".pdf");
        assertThat(relative).doesNotContain("/");
        Path written = tempDir.resolve(relative).toAbsolutePath().normalize();
        assertThat(written.startsWith(tempDir.toAbsolutePath().normalize())).isTrue();
        assertThat(Files.exists(written)).isTrue();
        assertThat(Files.readAllBytes(written)).isEqualTo(data);
    }

    @Test
    @DisplayName("保存-当扩展名非pdf时-应拒绝")
    void save_whenExtensionNotPdf_shouldThrow() {
        // Arrange
        byte[] data = "not-pdf".getBytes(StandardCharsets.UTF_8);

        // Act & Assert
        assertThatThrownBy(() -> storage.save(data, "notes.txt", "application/pdf"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("PDF");
    }

    @Test
    @DisplayName("保存-当路径试图穿越时-解析后仍落在storageDir内或抛非法路径")
    void resolve_whenPathContainsDotDot_shouldStayInsideStorageDir() {
        // Act & Assert
        assertThatThrownBy(() -> storage.resolveSafe("../outside.pdf"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("非法");
        assertThatThrownBy(() -> storage.open("../../etc/passwd"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("打开-当文件存在时-应返回可读流")
    void open_whenFileExists_shouldReturnInputStream() throws Exception {
        // Arrange
        byte[] data = "%PDF-1.4 hello".getBytes(StandardCharsets.UTF_8);
        String relative = storage.save(data, "book.pdf", "application/pdf");

        // Act
        try (InputStream in = storage.open(relative)) {
            // Assert
            assertThat(in).isNotNull();
            byte[] read = new byte[data.length];
            int n = in.read(read);
            assertThat(n).isEqualTo(data.length);
            assertThat(read).isEqualTo(data);
        }

        Resource resource = storage.asResource(relative);
        assertThat(resource.exists()).isTrue();
        assertThat(resource.contentLength()).isEqualTo(data.length);
    }
}
