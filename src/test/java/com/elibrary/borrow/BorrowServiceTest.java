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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BorrowServiceTest {

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
    @DisplayName("借阅-当抢锁失败时-应提示借阅繁忙")
    void borrowByWork_whenLockNotAcquired_shouldThrowBusy() {
        // Arrange
        PhysicalCopy copy = availableCopy(10L, 1L);
        when(workMapper.findActiveById(1L)).thenReturn(activeWork(1L));
        when(copyMapper.findFirstAvailableByWorkId(1L)).thenReturn(copy);
        when(lock.tryLock("borrow:copy:10", TTL)).thenReturn(Optional.<String>empty());

        // Act & Assert
        assertThatThrownBy(() -> borrowService.borrowByWork(100L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("借阅繁忙");
        verify(borrowRecordMapper, never()).insert(any(BorrowRecord.class));
        verify(lock, never()).unlock(any(String.class), any(String.class));
    }

    @Test
    @DisplayName("借阅-当复本可用且获锁时-应写入记录并标记BORROWED")
    void borrowByWork_whenCopyAvailableAndLocked_shouldInsertAndMarkBorrowed() {
        // Arrange
        PhysicalCopy copy = availableCopy(10L, 1L);
        when(workMapper.findActiveById(1L)).thenReturn(activeWork(1L));
        when(copyMapper.findFirstAvailableByWorkId(1L)).thenReturn(copy);
        when(lock.tryLock("borrow:copy:10", TTL)).thenReturn(Optional.of("tok"));
        when(copyMapper.findByIdForUpdate(10L)).thenReturn(copy);

        // Act
        borrowService.borrowByWork(100L, 1L);

        // Assert
        verify(borrowRecordMapper).insert(argThat(r ->
                r.getUserId().equals(100L)
                        && r.getPhysicalWorkId().equals(1L)
                        && r.getPhysicalCopyId().equals(10L)));
        verify(copyMapper).updateStatus(10L, CopyStatus.BORROWED);
        verify(lock).unlock("borrow:copy:10", "tok");
    }

    @Test
    @DisplayName("借阅-当锁内发现无可用时-应释放锁并提示暂无可用复本")
    void borrowByWork_whenInsideLockCopyNotAvailable_shouldUnlockAndThrow() {
        // Arrange
        PhysicalCopy candidate = availableCopy(10L, 1L);
        PhysicalCopy locked = availableCopy(10L, 1L);
        locked.setStatus(CopyStatus.BORROWED);
        when(workMapper.findActiveById(1L)).thenReturn(activeWork(1L));
        when(copyMapper.findFirstAvailableByWorkId(1L)).thenReturn(candidate);
        when(lock.tryLock("borrow:copy:10", TTL)).thenReturn(Optional.of("tok"));
        when(copyMapper.findByIdForUpdate(10L)).thenReturn(locked);

        // Act & Assert
        assertThatThrownBy(() -> borrowService.borrowByWork(100L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("暂无可用复本");
        verify(borrowRecordMapper, never()).insert(any(BorrowRecord.class));
        verify(copyMapper, never()).updateStatus(any(Long.class), any(CopyStatus.class));
        verify(lock).unlock(eq("borrow:copy:10"), eq("tok"));
    }

    @Test
    @DisplayName("归还-当当前用户为借阅人时-应置returned_at并恢复AVAILABLE")
    void returnCopy_whenCurrentBorrower_shouldMarkReturnedAndAvailable() {
        // Arrange
        BorrowRecord record = openRecord(50L, 100L, 10L);
        when(borrowRecordMapper.findById(50L)).thenReturn(record);
        when(lock.tryLock("borrow:copy:10", TTL)).thenReturn(Optional.of("tok"));

        // Act
        borrowService.returnByRecordId(100L, 50L);

        // Assert
        ArgumentCaptor<LocalDateTime> timeCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(borrowRecordMapper).markReturned(eq(50L), timeCaptor.capture());
        assertThat(timeCaptor.getValue()).isNotNull();
        verify(copyMapper).updateStatus(10L, CopyStatus.AVAILABLE);
        verify(lock).unlock("borrow:copy:10", "tok");
    }

    @Test
    @DisplayName("归还-当非借阅人时-应拒绝")
    void returnCopy_whenNotBorrower_shouldThrow() {
        // Arrange
        BorrowRecord record = openRecord(50L, 100L, 10L);
        when(borrowRecordMapper.findById(50L)).thenReturn(record);

        // Act & Assert
        assertThatThrownBy(() -> borrowService.returnByRecordId(999L, 50L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只能归还自己的借阅");
        verify(lock, never()).tryLock(any(String.class), any(Duration.class));
        verify(borrowRecordMapper, never()).markReturned(any(Long.class), any(LocalDateTime.class));
        verify(lock, never()).unlock(any(String.class), any(String.class));
    }

    @Test
    @DisplayName("我的借阅-当下架但仍未还时-仍应返回该记录")
    void listMyBorrows_whenCopySoftDeletedButOpen_shouldStillInclude() {
        // Arrange
        BorrowRecordView view = new BorrowRecordView();
        view.setId(50L);
        view.setUserId(100L);
        view.setPhysicalCopyId(10L);
        view.setBorrowedAt(LocalDateTime.now().minusDays(1));
        view.setReturnedAt(null);
        view.setTitle("已下架书目");
        view.setCopyNo("C001");
        view.setCopyDeleted(true);
        view.setWorkDeleted(true);
        when(borrowRecordMapper.listOpenAndRecentByUserId(100L))
                .thenReturn(Collections.singletonList(view));

        // Act
        List<BorrowRecordView> result = borrowService.listMyBorrows(100L);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).isOpen()).isTrue();
        assertThat(result.get(0).isCopyDeleted()).isTrue();
        assertThat(result.get(0).getTitle()).isEqualTo("已下架书目");
        verify(borrowRecordMapper).listOpenAndRecentByUserId(100L);
    }

    private static PhysicalWork activeWork(Long id) {
        PhysicalWork work = new PhysicalWork();
        work.setId(id);
        work.setTitle("测试书目");
        work.setDeleted(false);
        return work;
    }

    private static PhysicalCopy availableCopy(Long id, Long workId) {
        PhysicalCopy copy = new PhysicalCopy();
        copy.setId(id);
        copy.setWorkId(workId);
        copy.setCopyNo("C001");
        copy.setStatus(CopyStatus.AVAILABLE);
        copy.setDeleted(false);
        return copy;
    }

    private static BorrowRecord openRecord(Long id, Long userId, Long copyId) {
        BorrowRecord record = new BorrowRecord();
        record.setId(id);
        record.setUserId(userId);
        record.setPhysicalCopyId(copyId);
        record.setBorrowedAt(LocalDateTime.now().minusDays(1));
        record.setReturnedAt(null);
        return record;
    }
}
