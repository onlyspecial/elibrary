package com.elibrary.borrow;

import com.elibrary.common.BusinessException;
import com.elibrary.common.lock.RedisDistributedLock;
import com.elibrary.physical.CopyStatus;
import com.elibrary.physical.PhysicalCopy;
import com.elibrary.physical.PhysicalCopyMapper;
import com.elibrary.physical.PhysicalWork;
import com.elibrary.physical.PhysicalWorkMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BorrowServiceBranchTest {

    private static final Duration TTL = Duration.ofSeconds(10);

    @Mock
    RedisDistributedLock lock;
    @Mock
    PhysicalCopyMapper copyMapper;
    @Mock
    PhysicalWorkMapper workMapper;
    @Mock
    BorrowRecordMapper borrowRecordMapper;
    @InjectMocks
    BorrowService borrowService;

    @Test
    @DisplayName("借阅-当书目不存在时-应抛业务异常")
    void borrowByWork_whenWorkMissing_shouldThrow() {
        when(workMapper.findActiveById(1L)).thenReturn(null);

        assertThatThrownBy(() -> borrowService.borrowByWork(100L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不存在或已下架");
        verify(lock, never()).tryLock(any(String.class), any(Duration.class));
    }

    @Test
    @DisplayName("借阅-当无可用复本时-应抛业务异常")
    void borrowByWork_whenNoAvailableCopy_shouldThrow() {
        PhysicalWork work = new PhysicalWork();
        work.setId(1L);
        when(workMapper.findActiveById(1L)).thenReturn(work);
        when(copyMapper.findFirstAvailableByWorkId(1L)).thenReturn(null);

        assertThatThrownBy(() -> borrowService.borrowByWork(100L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("暂无可用复本");
    }

    @Test
    @DisplayName("借阅-当锁内复本已删除时-应释放锁并提示无可用")
    void borrowByWork_whenInsideLockCopyDeleted_shouldUnlockAndThrow() {
        PhysicalCopy candidate = available(10L);
        PhysicalCopy locked = available(10L);
        locked.setDeleted(true);
        when(workMapper.findActiveById(1L)).thenReturn(activeWork());
        when(copyMapper.findFirstAvailableByWorkId(1L)).thenReturn(candidate);
        when(lock.tryLock("borrow:copy:10", TTL)).thenReturn(Optional.of("tok"));
        when(copyMapper.findByIdForUpdate(10L)).thenReturn(locked);

        assertThatThrownBy(() -> borrowService.borrowByWork(100L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("暂无可用复本");
        verify(lock).unlock("borrow:copy:10", "tok");
        verify(borrowRecordMapper, never()).insert(any(BorrowRecord.class));
    }

    @Test
    @DisplayName("借阅-当锁内查不到复本时-应提示无可用")
    void borrowByWork_whenInsideLockCopyNull_shouldThrow() {
        PhysicalCopy candidate = available(10L);
        when(workMapper.findActiveById(1L)).thenReturn(activeWork());
        when(copyMapper.findFirstAvailableByWorkId(1L)).thenReturn(candidate);
        when(lock.tryLock("borrow:copy:10", TTL)).thenReturn(Optional.of("tok"));
        when(copyMapper.findByIdForUpdate(10L)).thenReturn(null);

        assertThatThrownBy(() -> borrowService.borrowByWork(100L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("暂无可用复本");
        verify(lock).unlock("borrow:copy:10", "tok");
    }

    @Test
    @DisplayName("归还-当记录不存在或已归还时-应拒绝")
    void returnByRecordId_whenMissingOrAlreadyReturned_shouldThrow() {
        when(borrowRecordMapper.findById(1L)).thenReturn(null);
        assertThatThrownBy(() -> borrowService.returnByRecordId(100L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不存在或已归还");

        BorrowRecord returned = new BorrowRecord();
        returned.setId(2L);
        returned.setUserId(100L);
        returned.setPhysicalCopyId(10L);
        returned.setReturnedAt(LocalDateTime.now());
        when(borrowRecordMapper.findById(2L)).thenReturn(returned);
        assertThatThrownBy(() -> borrowService.returnByRecordId(100L, 2L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不存在或已归还");
    }

    @Test
    @DisplayName("归还-当抢锁失败时-应提示归还繁忙")
    void returnByRecordId_whenLockFails_shouldThrowBusy() {
        BorrowRecord record = new BorrowRecord();
        record.setId(50L);
        record.setUserId(100L);
        record.setPhysicalCopyId(10L);
        record.setReturnedAt(null);
        when(borrowRecordMapper.findById(50L)).thenReturn(record);
        when(lock.tryLock("borrow:copy:10", TTL)).thenReturn(Optional.<String>empty());

        assertThatThrownBy(() -> borrowService.returnByRecordId(100L, 50L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("归还繁忙");
        verify(borrowRecordMapper, never()).markReturned(any(Long.class), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("借阅视图-当已归还时-isOpen应为false")
    void borrowRecordView_whenReturned_shouldNotBeOpen() {
        BorrowRecordView view = new BorrowRecordView();
        view.setReturnedAt(LocalDateTime.now());
        assertThat(view.isOpen()).isFalse();
        view.setReturnedAt(null);
        assertThat(view.isOpen()).isTrue();
    }

    private static PhysicalWork activeWork() {
        PhysicalWork work = new PhysicalWork();
        work.setId(1L);
        work.setDeleted(false);
        return work;
    }

    private static PhysicalCopy available(Long id) {
        PhysicalCopy copy = new PhysicalCopy();
        copy.setId(id);
        copy.setWorkId(1L);
        copy.setStatus(CopyStatus.AVAILABLE);
        copy.setDeleted(false);
        return copy;
    }
}
