package com.elibrary.physical;

import com.elibrary.common.BusinessException;
import com.elibrary.common.PageResult;
import com.elibrary.common.lock.RedisDistributedLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Optional;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PhysicalWorkServiceTest {

    @Mock
    PhysicalWorkMapper workMapper;

    @Mock
    PhysicalCopyMapper copyMapper;

    @Mock
    RedisDistributedLock lock;

    @InjectMocks
    PhysicalWorkService physicalWorkService;

    @Test
    @DisplayName("读者搜索-当关键词为空时-不应查询数据库")
    void searchForReader_whenKeywordBlank_shouldNotQuery() {
        PageResult<PhysicalWork> result = physicalWorkService.searchForReader("  ", 1, 10);
        assertThat(result.getItems()).isEmpty();
        assertThat(result.getTotal()).isEqualTo(0L);
        verify(workMapper, never()).searchActive(org.mockito.ArgumentMatchers.<String>any(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("列表-当存在未删除书目时-应返回可借册数与总册数")
    void list_whenWorksExist_shouldReturnAvailabilityCounts() {
        // Arrange
        PhysicalWork work = new PhysicalWork();
        work.setId(1L);
        work.setTitle("Java 实战");
        work.setAuthor("作者A");
        work.setDeleted(false);
        work.setAvailableCount(2);
        work.setTotalCount(5);

        when(workMapper.searchActive(eq("Java"), eq(0), eq(10)))
                .thenReturn(Collections.singletonList(work));
        when(workMapper.countActive(eq("Java"))).thenReturn(1L);

        // Act
        PageResult<PhysicalWork> result = physicalWorkService.list("Java", 1, 10);

        // Assert
        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getAvailableCount()).isEqualTo(2);
        assertThat(result.getItems().get(0).getTotalCount()).isEqualTo(5);
        assertThat(result.getItems().get(0).isDeleted()).isFalse();
        assertThat(result.getTotal()).isEqualTo(1L);
        verify(workMapper).searchActive(eq("Java"), eq(0), eq(10));
        verify(workMapper).countActive(eq("Java"));
    }

    @Test
    @DisplayName("详情-当书目已下架时-应对读者抛业务异常")
    void getDetail_whenWorkDeleted_shouldThrowForReader() {
        // Arrange
        when(workMapper.findActiveById(9L)).thenReturn(null);

        // Act & Assert
        assertThatThrownBy(() -> physicalWorkService.getActiveDetail(9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不存在或已下架");
    }

    @Test
    @DisplayName("列表-当带关键词分页时-应计算正确 offset")
    void list_whenKeywordAndPage_shouldPassOffsetAndKeyword() {
        // Arrange
        when(workMapper.searchActive(eq("Java"), eq(10), eq(10)))
                .thenReturn(Collections.<PhysicalWork>emptyList());
        when(workMapper.countActive(eq("Java"))).thenReturn(0L);

        // Act
        PageResult<PhysicalWork> result = physicalWorkService.list("  Java  ", 2, 10);

        // Assert
        assertThat(result.getPage()).isEqualTo(2);
        assertThat(result.getSize()).isEqualTo(10);
        verify(workMapper).searchActive(eq("Java"), eq(10), eq(10));
        verify(workMapper).countActive(eq("Java"));
    }

    @Test
    @DisplayName("创建-当字段合法时-应插入未删除书目")
    void create_whenValidFields_shouldInsertActiveWork() {
        // Arrange
        when(lock.tryLock(any(String.class), any(Duration.class))).thenReturn(Optional.of("tok"));
        when(workMapper.findActiveByTitleAndAuthor(eq("Clean Code"), eq("Robert"))).thenReturn(null);
        when(workMapper.insert(any(PhysicalWork.class))).thenAnswer(inv -> {
            PhysicalWork w = inv.getArgument(0);
            w.setId(11L);
            return 1;
        });

        // Act
        PhysicalWork created = physicalWorkService.create(
                "  Clean Code  ", "Robert", ContentType.BOOK, "desc", "978-1", 7L);

        // Assert
        assertThat(created.getId()).isEqualTo(11L);
        assertThat(created.getTitle()).isEqualTo("Clean Code");
        assertThat(created.getAuthor()).isEqualTo("Robert");
        assertThat(created.getContentType()).isEqualTo(ContentType.BOOK);
        assertThat(created.isDeleted()).isFalse();
        assertThat(created.getCreatedBy()).isEqualTo(7L);
        ArgumentCaptor<PhysicalWork> captor = ArgumentCaptor.forClass(PhysicalWork.class);
        verify(workMapper).insert(captor.capture());
        assertThat(captor.getValue().getIsbnOrIssn()).isEqualTo("978-1");
        verify(lock).unlock(any(String.class), eq("tok"));
    }

    @Test
    @DisplayName("创建-当标题作者已存在时-应拒绝")
    void create_whenTitleAuthorExists_shouldThrow() {
        when(lock.tryLock(any(String.class), any(Duration.class))).thenReturn(Optional.of("tok"));
        PhysicalWork dup = new PhysicalWork();
        dup.setId(99L);
        when(workMapper.findActiveByTitleAndAuthor(eq("Same"), eq("Author"))).thenReturn(dup);

        assertThatThrownBy(() -> physicalWorkService.create(
                "Same", "Author", ContentType.BOOK, null, null, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已存在");
        verify(workMapper, never()).insert(any());
        verify(lock).unlock(any(String.class), eq("tok"));
    }

    @Test
    @DisplayName("创建-当标题为空时-应抛业务异常")
    void create_whenTitleBlank_shouldThrow() {
        assertThatThrownBy(() -> physicalWorkService.create(
                "  ", "Author", ContentType.BOOK, null, null, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("标题");
        verify(workMapper, never()).insert(any());
    }

    @Test
    @DisplayName("更新-当书目存在且未下架时-应更新字段")
    void update_whenWorkActive_shouldUpdateFields() {
        // Arrange
        PhysicalWork existing = new PhysicalWork();
        existing.setId(5L);
        existing.setTitle("Old");
        existing.setAuthor("A");
        existing.setContentType(ContentType.BOOK);
        existing.setDeleted(false);
        when(workMapper.findById(5L)).thenReturn(existing);
        when(lock.tryLock(any(String.class), any(Duration.class))).thenReturn(Optional.of("tok"));
        when(workMapper.findActiveByTitleAndAuthor(eq("New Title"), eq("New Author"))).thenReturn(null);

        // Act
        physicalWorkService.update(5L, "New Title", "New Author", ContentType.JOURNAL, "d", "issn");

        // Assert
        assertThat(existing.getTitle()).isEqualTo("New Title");
        assertThat(existing.getAuthor()).isEqualTo("New Author");
        assertThat(existing.getContentType()).isEqualTo(ContentType.JOURNAL);
        verify(workMapper).update(existing);
    }

    @Test
    @DisplayName("更新-当书目已下架时-应拒绝编辑")
    void update_whenWorkDeleted_shouldThrow() {
        // Arrange
        PhysicalWork existing = new PhysicalWork();
        existing.setId(5L);
        existing.setDeleted(true);
        when(workMapper.findById(5L)).thenReturn(existing);

        // Act & Assert
        assertThatThrownBy(() -> physicalWorkService.update(
                5L, "T", "A", ContentType.BOOK, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不可编辑");
        verify(workMapper, never()).update(any());
    }

    @Test
    @DisplayName("软删书目-当书目未下架时-应标记 deleted")
    void softDelete_whenWorkActive_shouldMarkDeleted() {
        // Arrange
        PhysicalWork existing = new PhysicalWork();
        existing.setId(3L);
        existing.setDeleted(false);
        when(workMapper.findById(3L)).thenReturn(existing);

        // Act
        physicalWorkService.softDelete(3L);

        // Assert
        assertThat(existing.isDeleted()).isTrue();
        verify(workMapper).softDelete(3L);
    }

    @Test
    @DisplayName("添加复本-当书目存在时-应以 AVAILABLE 插入")
    void addCopy_whenWorkExists_shouldInsertAvailableCopy() {
        // Arrange
        PhysicalWork work = new PhysicalWork();
        work.setId(2L);
        work.setDeleted(false);
        when(workMapper.findById(2L)).thenReturn(work);
        when(copyMapper.insert(any(PhysicalCopy.class))).thenAnswer(inv -> {
            PhysicalCopy c = inv.getArgument(0);
            c.setId(100L);
            return 1;
        });

        // Act
        PhysicalCopy copy = physicalWorkService.addCopy(2L, " C-01 ");

        // Assert
        assertThat(copy.getId()).isEqualTo(100L);
        assertThat(copy.getWorkId()).isEqualTo(2L);
        assertThat(copy.getCopyNo()).isEqualTo("C-01");
        assertThat(copy.getStatus()).isEqualTo(CopyStatus.AVAILABLE);
        assertThat(copy.isDeleted()).isFalse();
        verify(copyMapper).insert(any(PhysicalCopy.class));
    }

    @Test
    @DisplayName("软删复本-当复本为借出状态时-仍应允许软删")
    void softDeleteCopy_whenBorrowed_shouldStillSoftDelete() {
        // Arrange
        PhysicalCopy copy = new PhysicalCopy();
        copy.setId(50L);
        copy.setWorkId(2L);
        copy.setStatus(CopyStatus.BORROWED);
        copy.setDeleted(false);
        when(copyMapper.findById(50L)).thenReturn(copy);

        // Act
        physicalWorkService.softDeleteCopy(50L);

        // Assert
        assertThat(copy.isDeleted()).isTrue();
        verify(copyMapper).softDelete(50L);
    }

    @Test
    @DisplayName("软删复本-当复本不存在时-应抛业务异常")
    void softDeleteCopy_whenMissing_shouldThrow() {
        // Arrange
        when(copyMapper.findById(99L)).thenReturn(null);

        // Act & Assert
        assertThatThrownBy(() -> physicalWorkService.softDeleteCopy(99L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("复本不存在");
        verify(copyMapper, never()).softDelete(any());
    }
}
