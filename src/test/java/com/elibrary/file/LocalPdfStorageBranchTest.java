package com.elibrary.file;

import com.elibrary.common.BusinessException;
import com.elibrary.config.LibraryProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalPdfStorageBranchTest {

    @TempDir
    Path tempDir;

    private LocalPdfStorage storage;

    @BeforeEach
    void setUp() throws Exception {
        LibraryProperties properties = new LibraryProperties();
        properties.getPdf().setStorageDir(tempDir.toString());
        properties.getPdf().setMaxSizeMb(1);
        storage = new LocalPdfStorage(properties);
        storage.initForTest(tempDir, 1);
    }

    @Test
    @DisplayName("保存Multipart-当合法PDF时-应写入并可通过asResource打开")
    void save_whenValidMultipart_shouldPersist() throws Exception {
        byte[] data = "%PDF-1.4 x".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
                "pdf", "book.pdf", "application/pdf", data);

        String name = storage.save(file);

        assertThat(name).endsWith(".pdf");
        assertThat(storage.asResource(name).exists()).isTrue();
        assertThat(storage.getStorageDir()).isEqualTo(tempDir.toAbsolutePath().normalize());
    }

    @Test
    @DisplayName("保存-当文件为空或contentType非法或超限时-应拒绝")
    void save_whenEmptyOrBadTypeOrTooLarge_shouldThrow() {
        MockMultipartFile empty = new MockMultipartFile(
                "pdf", "a.pdf", "application/pdf", new byte[0]);
        assertThatThrownBy(() -> storage.save(empty))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("PDF");

        assertThatThrownBy(() -> storage.save(
                "%PDF".getBytes(StandardCharsets.UTF_8), "a.pdf", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("PDF");

        assertThatThrownBy(() -> storage.save(
                "%PDF".getBytes(StandardCharsets.UTF_8), "a.pdf", "text/plain"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("PDF");

        byte[] huge = new byte[1024 * 1024 + 1];
        huge[0] = '%';
        assertThatThrownBy(() -> storage.save(huge, "big.pdf", "application/pdf"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("大小");

        assertThatThrownBy(() -> storage.save((byte[]) null, "a.pdf", "application/pdf"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("打开-当文件不存在或路径为空时-应抛业务异常")
    void open_whenMissingOrBlankPath_shouldThrow() {
        assertThatThrownBy(() -> storage.open("missing.pdf"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不存在");
        assertThatThrownBy(() -> storage.asResource("missing.pdf"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不存在");
        assertThatThrownBy(() -> storage.resolveSafe("  "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("非法");
        assertThatThrownBy(() -> storage.resolveSafe(null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("非法");
        assertThatThrownBy(() -> storage.resolveSafe("a\\b.pdf"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("非法");
    }

    @Test
    @DisplayName("保存Multipart-当文件为null时-应拒绝")
    void save_whenMultipartNull_shouldThrow() {
        assertThatThrownBy(() -> storage.save((org.springframework.web.multipart.MultipartFile) null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("PDF");
    }
}
