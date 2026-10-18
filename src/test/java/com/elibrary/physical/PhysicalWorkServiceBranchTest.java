package com.elibrary.physical;

import com.elibrary.common.BusinessException;
import com.elibrary.common.PageResult;
import com.elibrary.common.lock.RedisDistributedLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PhysicalWorkServiceBranchTest {

    @Mock
    PhysicalWorkMapper workMapper;
    @Mock
    PhysicalCopyMapper copyMapper;
    @Mock
    RedisDistributedLock lock;
    @InjectMocks
    PhysicalWorkService physicalWorkService;

    @Test
    @DisplayName("管理列表-当page或size非法时-应回退默认分页")
    void listForAdmin_whenPageOrSizeInvalid_shouldUseDefaults() {
        when(workMapper.searchAll(isNull(), eq(0), eq(10)))
                .thenReturn(Collections.<PhysicalWork>emptyList());
        when(workMapper.countAll(isNull())).thenReturn(0L);

        PageResult<PhysicalWork> result = physicalWorkService.listForAdmin("  ", 0, -1);

        assertThat(result.getPage()).isEqualTo(1);
        assertThat(result.getSize()).isEqualTo(10);
        verify(workMapper).searchAll(isNull(), eq(0), eq(10));
    }

    @Test
    @DisplayName("读者列表-当page与size为null时-应使用默认值")
    void list_whenPageAndSizeNull_shouldUseDefaults() {
        when(workMapper.searchActive(isNull(), eq(0), eq(10)))
                .thenReturn(Collections.<PhysicalWork>emptyList());
        when(workMapper.countActive(isNull())).thenReturn(0L);

        PageResult<PhysicalWork> result = physicalWorkService.list(null, null, null);

        assertThat(result.getPage()).isEqualTo(1);
        assertThat(result.getSize()).isEqualTo(10);
    }

    @Test
    @DisplayName("详情-当书目未下架时-应返回书目")
    void getActiveDetail_whenWorkActive_shouldReturn() {
        PhysicalWork work = new PhysicalWork();
        work.setId(1L);
        work.setTitle("书");
        when(workMapper.findActiveById(1L)).thenReturn(work);

        assertThat(physicalWorkService.getActiveDetail(1L).getTitle()).isEqualTo("书");
    }

    @Test
    @DisplayName("requireById-当书目不存在时-应抛业务异常")
    void requireById_whenMissing_shouldThrow() {
        when(workMapper.findById(99L)).thenReturn(null);

        assertThatThrownBy(() -> physicalWorkService.requireById(99L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("书目不存在");
    }

    @Test
    @DisplayName("创建-当作者为空或类型为空时-应抛业务异常")
    void create_whenAuthorBlankOrTypeNull_shouldThrow() {
        assertThatThrownBy(() -> physicalWorkService.create(
                "T", "  ", ContentType.BOOK, null, null, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("作者");
        assertThatThrownBy(() -> physicalWorkService.create(
                "T", "A", null, null, null, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("内容类型");
        verify(workMapper, never()).insert(any());
    }

    @Test
    @DisplayName("创建-当描述与ISBN为空串时-应存为null")
    void create_whenBlankOptionalFields_shouldTrimToNull() {
        when(lock.tryLock(any(String.class), any(Duration.class))).thenReturn(Optional.of("tok"));
        when(workMapper.findActiveByTitleAndAuthor(eq("T"), eq("A"))).thenReturn(null);
        when(workMapper.insert(any(PhysicalWork.class))).thenAnswer(inv -> {
            PhysicalWork w = inv.getArgument(0);
            w.setId(1L);
            return 1;
        });

        PhysicalWork created = physicalWorkService.create(
                "T", "A", ContentType.BOOK, "  ", "   ", 1L);

        assertThat(created.getDescription()).isNull();
        assertThat(created.getIsbnOrIssn()).isNull();
    }

    @Test
    @DisplayName("软删书目-当已下架时-应拒绝")
    void softDelete_whenAlreadyDeleted_shouldThrow() {
        PhysicalWork existing = new PhysicalWork();
        existing.setId(3L);
        existing.setDeleted(true);
        when(workMapper.findById(3L)).thenReturn(existing);

        assertThatThrownBy(() -> physicalWorkService.softDelete(3L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已下架");
        verify(workMapper, never()).softDelete(any());
    }

    @Test
    @DisplayName("列复本-当书目存在时-应返回复本列表")
    void listCopies_whenWorkExists_shouldReturnCopies() {
        PhysicalWork work = new PhysicalWork();
        work.setId(2L);
        when(workMapper.findById(2L)).thenReturn(work);
        PhysicalCopy copy = new PhysicalCopy();
        copy.setId(10L);
        when(copyMapper.listByWorkId(2L)).thenReturn(Collections.singletonList(copy));

        List<PhysicalCopy> copies = physicalWorkService.listCopies(2L);

        assertThat(copies).hasSize(1);
        assertThat(copies.get(0).getId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("添加复本-当编号为空时-应抛业务异常")
    void addCopy_whenCopyNoBlank_shouldThrow() {
        PhysicalWork work = new PhysicalWork();
        work.setId(2L);
        when(workMapper.findById(2L)).thenReturn(work);

        assertThatThrownBy(() -> physicalWorkService.addCopy(2L, "  "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("复本编号");
        assertThatThrownBy(() -> physicalWorkService.addCopy(2L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("复本编号");
        verify(copyMapper, never()).insert(any());
    }

    @Test
    @DisplayName("软删复本-当已下架时-应拒绝")
    void softDeleteCopy_whenAlreadyDeleted_shouldThrow() {
        PhysicalCopy copy = new PhysicalCopy();
        copy.setId(50L);
        copy.setDeleted(true);
        when(copyMapper.findById(50L)).thenReturn(copy);

        assertThatThrownBy(() -> physicalWorkService.softDeleteCopy(50L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("复本已下架");
        verify(copyMapper, never()).softDelete(any());
    }
}
