package com.elibrary.digital;

import com.elibrary.common.BusinessException;
import com.elibrary.common.PageResult;
import com.elibrary.common.lock.RedisDistributedLock;
import com.elibrary.file.LocalPdfStorage;
import com.elibrary.physical.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DigitalItemServiceBranchTest {

    @Mock
    DigitalItemMapper digitalItemMapper;
    @Mock
    LocalPdfStorage localPdfStorage;
    @Mock
    RedisDistributedLock lock;
    @InjectMocks
    DigitalItemService digitalItemService;

    @Test
    @DisplayName("管理列表-当page非法且关键词空串时-应默认分页且kw为null")
    void listForAdmin_whenInvalidPageAndBlankKeyword_shouldNormalize() {
        when(digitalItemMapper.searchAll(isNull(), eq(0), eq(10)))
                .thenReturn(Collections.<DigitalItem>emptyList());
        when(digitalItemMapper.countAll(isNull())).thenReturn(0L);

        PageResult<DigitalItem> result = digitalItemService.listForAdmin("   ", null, 0);

        assertThat(result.getPage()).isEqualTo(1);
        assertThat(result.getSize()).isEqualTo(10);
        verify(digitalItemMapper).searchAll(isNull(), eq(0), eq(10));
    }

    @Test
    @DisplayName("requireById-当不存在时-应抛业务异常")
    void requireById_whenMissing_shouldThrow() {
        when(digitalItemMapper.findById(9L)).thenReturn(null);

        assertThatThrownBy(() -> digitalItemService.requireById(9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("电子书不存在");
    }

    @Test
    @DisplayName("创建-当元数据非法或文件为null时-应抛业务异常")
    void create_whenMetaInvalidOrFileNull_shouldThrow() {
        MultipartFile file = mock(MultipartFile.class);

        assertThatThrownBy(() -> digitalItemService.create(
                " ", "A", ContentType.BOOK, null, null, file, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("标题");
        assertThatThrownBy(() -> digitalItemService.create(
                "T", null, ContentType.BOOK, null, null, file, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("作者");
        assertThatThrownBy(() -> digitalItemService.create(
                "T", "A", null, null, null, file, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("内容类型");
        assertThatThrownBy(() -> digitalItemService.create(
                "T", "A", ContentType.BOOK, null, null, null, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("PDF");
        verify(localPdfStorage, never()).save(any(MultipartFile.class));
    }

    @Test
    @DisplayName("创建-当可选字段为空串时-应存为null")
    void create_whenBlankOptional_shouldTrimToNull() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("a.pdf");
        when(lock.tryLock(any(String.class), any(Duration.class))).thenReturn(Optional.of("tok"));
        when(digitalItemMapper.findActiveByTitleAndAuthor(eq("T"), eq("A"))).thenReturn(null);
        when(localPdfStorage.save(file)).thenReturn("stored.pdf");
        when(digitalItemMapper.insert(any(DigitalItem.class))).thenAnswer(inv -> {
            DigitalItem item = inv.getArgument(0);
            item.setId(1L);
            return 1;
        });

        DigitalItem created = digitalItemService.create(
                "T", "A", ContentType.BOOK, "  ", "", file, 1L);

        assertThat(created.getDescription()).isNull();
        assertThat(created.getIsbnOrIssn()).isNull();
    }

    @Test
    @DisplayName("更新-当电子书已下架时-应拒绝")
    void update_whenDeleted_shouldThrow() {
        DigitalItem existing = new DigitalItem();
        existing.setId(4L);
        existing.setDeleted(true);
        when(digitalItemMapper.findById(4L)).thenReturn(existing);

        assertThatThrownBy(() -> digitalItemService.update(
                4L, "T", "A", ContentType.BOOK, null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不可编辑");
        verify(digitalItemMapper, never()).update(any());
    }

    @Test
    @DisplayName("更新-当新PDF为空文件时-应保留旧路径")
    void update_whenEmptyNewPdf_shouldKeepPath() {
        DigitalItem existing = new DigitalItem();
        existing.setId(4L);
        existing.setTitle("Old");
        existing.setAuthor("A");
        existing.setContentType(ContentType.BOOK);
        existing.setPdfPath("old.pdf");
        existing.setDeleted(false);
        when(digitalItemMapper.findById(4L)).thenReturn(existing);
        when(lock.tryLock(any(String.class), any(Duration.class))).thenReturn(Optional.of("tok"));
        when(digitalItemMapper.findActiveByTitleAndAuthor(eq("New"), eq("B"))).thenReturn(null);
        MultipartFile empty = mock(MultipartFile.class);
        when(empty.isEmpty()).thenReturn(true);

        digitalItemService.update(4L, "New", "B", ContentType.BOOK, null, null, empty);

        assertThat(existing.getPdfPath()).isEqualTo("old.pdf");
        verify(localPdfStorage, never()).save(any(MultipartFile.class));
        verify(digitalItemMapper).update(existing);
    }
}
