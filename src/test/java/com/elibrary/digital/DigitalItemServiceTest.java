package com.elibrary.digital;

import com.elibrary.common.BusinessException;
import com.elibrary.common.PageResult;
import com.elibrary.common.lock.RedisDistributedLock;
import com.elibrary.file.LocalPdfStorage;
import com.elibrary.physical.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.Optional;
import java.time.Duration;

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
class DigitalItemServiceTest {

    @Mock
    DigitalItemMapper digitalItemMapper;

    @Mock
    LocalPdfStorage localPdfStorage;

    @Mock
    RedisDistributedLock lock;

    @InjectMocks
    DigitalItemService digitalItemService;

    @Test
    @DisplayName("读者搜索-当关键词为空时-不应查询数据库")
    void searchForReader_whenKeywordBlank_shouldNotQuery() {
        PageResult<DigitalItem> result = digitalItemService.searchForReader(null, 1, 10);
        assertThat(result.getItems()).isEmpty();
        verify(digitalItemMapper, never()).searchActive(org.mockito.ArgumentMatchers.<String>any(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("列表-当存在未删除电子书时-应返回列表且不含已下架")
    void list_whenActiveItemsExist_shouldReturnOnlyActive() {
        // Arrange
        DigitalItem item = new DigitalItem();
        item.setId(1L);
        item.setTitle("Effective Java");
        item.setAuthor("Joshua Bloch");
        item.setContentType(ContentType.BOOK);
        item.setDeleted(false);
        item.setPdfPath("abc.pdf");

        when(digitalItemMapper.searchActive(eq("Effective"), eq(0), eq(10)))
                .thenReturn(Collections.singletonList(item));
        when(digitalItemMapper.countActive(eq("Effective"))).thenReturn(1L);

        // Act
        PageResult<DigitalItem> result = digitalItemService.list("Effective", 1, 10);

        // Assert
        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).isDeleted()).isFalse();
        assertThat(result.getItems().get(0).getTitle()).isEqualTo("Effective Java");
        assertThat(result.getTotal()).isEqualTo(1L);
        verify(digitalItemMapper).searchActive(eq("Effective"), eq(0), eq(10));
        verify(digitalItemMapper).countActive(eq("Effective"));
    }

    @Test
    @DisplayName("详情-当电子书已下架时-应对读者抛业务异常")
    void getActiveDetail_whenItemDeleted_shouldThrow() {
        // Arrange
        when(digitalItemMapper.findActiveById(9L)).thenReturn(null);

        // Act & Assert
        assertThatThrownBy(() -> digitalItemService.getActiveDetail(9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不存在或已下架");
        assertThatThrownBy(() -> digitalItemService.requireActive(9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不存在或已下架");
    }

    @Test
    @DisplayName("详情-当电子书未删除时-应返回详情")
    void getActiveDetail_whenItemActive_shouldReturnItem() {
        // Arrange
        DigitalItem item = new DigitalItem();
        item.setId(3L);
        item.setTitle("Clean Code");
        item.setDeleted(false);
        when(digitalItemMapper.findActiveById(3L)).thenReturn(item);

        // Act
        DigitalItem result = digitalItemService.getActiveDetail(3L);

        // Assert
        assertThat(result.getId()).isEqualTo(3L);
        assertThat(result.getTitle()).isEqualTo("Clean Code");
        assertThat(result.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("列表-当带关键词分页时-应计算正确 offset")
    void list_whenKeywordAndPage_shouldPassOffsetAndKeyword() {
        // Arrange
        when(digitalItemMapper.searchActive(eq("Java"), eq(10), eq(10)))
                .thenReturn(Collections.<DigitalItem>emptyList());
        when(digitalItemMapper.countActive(eq("Java"))).thenReturn(0L);

        // Act
        PageResult<DigitalItem> result = digitalItemService.list("  Java  ", 2, 10);

        // Assert
        assertThat(result.getPage()).isEqualTo(2);
        assertThat(result.getSize()).isEqualTo(10);
        verify(digitalItemMapper).searchActive(eq("Java"), eq(10), eq(10));
        verify(digitalItemMapper).countActive(eq("Java"));
    }

    @Test
    @DisplayName("上传创建-当PDF合法时-应保存并写入路径")
    void create_whenValidPdf_shouldSaveAndPersistPaths() {
        // Arrange
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("book.pdf");
        when(lock.tryLock(any(String.class), any(Duration.class))).thenReturn(Optional.of("tok"));
        when(digitalItemMapper.findActiveByTitleAndAuthor(eq("Title"), eq("Author"))).thenReturn(null);
        when(localPdfStorage.save(file)).thenReturn("stored-uuid.pdf");
        when(digitalItemMapper.insert(any(DigitalItem.class))).thenAnswer(inv -> {
            DigitalItem item = inv.getArgument(0);
            item.setId(20L);
            return 1;
        });

        // Act
        DigitalItem created = digitalItemService.create(
                "Title", "Author", ContentType.BOOK, "desc", null, file, 8L);

        // Assert
        assertThat(created.getId()).isEqualTo(20L);
        assertThat(created.getPdfPath()).isEqualTo("stored-uuid.pdf");
        assertThat(created.getPdfOriginalName()).isEqualTo("book.pdf");
        assertThat(created.isDeleted()).isFalse();
        assertThat(created.getCreatedBy()).isEqualTo(8L);
        verify(localPdfStorage).save(file);
        ArgumentCaptor<DigitalItem> captor = ArgumentCaptor.forClass(DigitalItem.class);
        verify(digitalItemMapper).insert(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("Title");
        verify(lock).unlock(any(String.class), eq("tok"));
    }

    @Test
    @DisplayName("上传创建-当未选择文件时-应抛业务异常")
    void create_whenFileMissing_shouldThrow() {
        // Arrange
        MultipartFile empty = mock(MultipartFile.class);
        when(empty.isEmpty()).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> digitalItemService.create(
                "T", "A", ContentType.BOOK, null, null, empty, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("PDF");
        verify(localPdfStorage, never()).save(any(MultipartFile.class));
        verify(digitalItemMapper, never()).insert(any());
    }

    @Test
    @DisplayName("更新-当提供新PDF时-应替换路径且不删旧文件")
    void update_whenNewPdfProvided_shouldReplacePath() {
        // Arrange
        DigitalItem existing = new DigitalItem();
        existing.setId(4L);
        existing.setTitle("Old");
        existing.setAuthor("A");
        existing.setContentType(ContentType.BOOK);
        existing.setPdfPath("old.pdf");
        existing.setPdfOriginalName("old-name.pdf");
        existing.setDeleted(false);
        when(digitalItemMapper.findById(4L)).thenReturn(existing);
        when(lock.tryLock(any(String.class), any(Duration.class))).thenReturn(Optional.of("tok"));
        when(digitalItemMapper.findActiveByTitleAndAuthor(eq("New Title"), eq("New Author"))).thenReturn(null);

        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("new.pdf");
        when(localPdfStorage.save(file)).thenReturn("new-stored.pdf");

        // Act
        digitalItemService.update(4L, "New Title", "New Author", ContentType.JOURNAL,
                "d", "isbn", file);

        // Assert
        assertThat(existing.getTitle()).isEqualTo("New Title");
        assertThat(existing.getPdfPath()).isEqualTo("new-stored.pdf");
        assertThat(existing.getPdfOriginalName()).isEqualTo("new.pdf");
        verify(localPdfStorage).save(file);
        verify(digitalItemMapper).update(existing);
    }

    @Test
    @DisplayName("更新-当未提供新PDF时-应仅更新元数据")
    void update_whenNoNewPdf_shouldKeepExistingPath() {
        // Arrange
        DigitalItem existing = new DigitalItem();
        existing.setId(4L);
        existing.setTitle("Old");
        existing.setAuthor("A");
        existing.setContentType(ContentType.BOOK);
        existing.setPdfPath("old.pdf");
        existing.setPdfOriginalName("old-name.pdf");
        existing.setDeleted(false);
        when(digitalItemMapper.findById(4L)).thenReturn(existing);
        when(lock.tryLock(any(String.class), any(Duration.class))).thenReturn(Optional.of("tok"));
        when(digitalItemMapper.findActiveByTitleAndAuthor(eq("New"), eq("B"))).thenReturn(null);

        // Act
        digitalItemService.update(4L, "New", "B", ContentType.BOOK, null, null, null);

        // Assert
        assertThat(existing.getTitle()).isEqualTo("New");
        assertThat(existing.getPdfPath()).isEqualTo("old.pdf");
        verify(localPdfStorage, never()).save(any(MultipartFile.class));
        verify(digitalItemMapper).update(existing);
    }

    @Test
    @DisplayName("软删-当电子书未下架时-应标记 deleted 且不删磁盘文件")
    void softDelete_whenActive_shouldMarkDeletedWithoutTouchingDisk() {
        // Arrange
        DigitalItem existing = new DigitalItem();
        existing.setId(6L);
        existing.setPdfPath("keep.pdf");
        existing.setDeleted(false);
        when(digitalItemMapper.findById(6L)).thenReturn(existing);

        // Act
        digitalItemService.softDelete(6L);

        // Assert
        assertThat(existing.isDeleted()).isTrue();
        verify(digitalItemMapper).softDelete(6L);
        verify(localPdfStorage, never()).save(any(MultipartFile.class));
    }

    @Test
    @DisplayName("软删-当电子书已下架时-应抛业务异常")
    void softDelete_whenAlreadyDeleted_shouldThrow() {
        // Arrange
        DigitalItem existing = new DigitalItem();
        existing.setId(6L);
        existing.setDeleted(true);
        when(digitalItemMapper.findById(6L)).thenReturn(existing);

        // Act & Assert
        assertThatThrownBy(() -> digitalItemService.softDelete(6L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已下架");
        verify(digitalItemMapper, never()).softDelete(any());
    }
}
