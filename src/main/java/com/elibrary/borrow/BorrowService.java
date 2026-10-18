package com.elibrary.borrow;

import com.elibrary.common.BusinessException;
import com.elibrary.common.lock.RedisDistributedLock;
import com.elibrary.physical.CopyStatus;
import com.elibrary.physical.PhysicalCopy;
import com.elibrary.physical.PhysicalCopyMapper;
import com.elibrary.physical.PhysicalWork;
import com.elibrary.physical.PhysicalWorkMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class BorrowService {

    private static final Duration LOCK_TTL = Duration.ofSeconds(10);
    private static final String LOCK_KEY_PREFIX = "borrow:copy:";

    private final RedisDistributedLock lock;
    private final PhysicalCopyMapper copyMapper;
    private final PhysicalWorkMapper workMapper;
    private final BorrowRecordMapper borrowRecordMapper;

    public BorrowService(RedisDistributedLock lock,
                         PhysicalCopyMapper copyMapper,
                         PhysicalWorkMapper workMapper,
                         BorrowRecordMapper borrowRecordMapper) {
        this.lock = lock;
        this.copyMapper = copyMapper;
        this.workMapper = workMapper;
        this.borrowRecordMapper = borrowRecordMapper;
    }

    @Transactional
    public void borrowByWork(Long userId, Long workId) {
        PhysicalWork work = workMapper.findActiveById(workId);
        if (work == null) {
            throw new BusinessException("书目不存在或已下架");
        }
        PhysicalCopy candidate = copyMapper.findFirstAvailableByWorkId(workId);
        if (candidate == null) {
            throw new BusinessException("暂无可用复本");
        }
        String key = LOCK_KEY_PREFIX + candidate.getId();
        String token = lock.tryLock(key, LOCK_TTL)
                .orElseThrow(() -> new BusinessException("借阅繁忙，请稍后重试"));
        try {
            PhysicalCopy copy = copyMapper.findByIdForUpdate(candidate.getId());
            if (copy == null || copy.isDeleted() || copy.getStatus() != CopyStatus.AVAILABLE) {
                throw new BusinessException("暂无可用复本");
            }
            BorrowRecord record = new BorrowRecord();
            record.setUserId(userId);
            record.setPhysicalWorkId(work.getId());
            record.setPhysicalCopyId(copy.getId());
            record.setBorrowedAt(LocalDateTime.now());
            borrowRecordMapper.insert(record);
            copyMapper.updateStatus(copy.getId(), CopyStatus.BORROWED);
        } finally {
            lock.unlock(key, token);
        }
    }

    @Transactional
    public void returnByRecordId(Long userId, Long recordId) {
        BorrowRecord record = borrowRecordMapper.findById(recordId);
        if (record == null || record.getReturnedAt() != null) {
            throw new BusinessException("借阅记录不存在或已归还");
        }
        if (!record.getUserId().equals(userId)) {
            throw new BusinessException("只能归还自己的借阅");
        }
        String key = LOCK_KEY_PREFIX + record.getPhysicalCopyId();
        String token = lock.tryLock(key, LOCK_TTL)
                .orElseThrow(() -> new BusinessException("归还繁忙，请稍后重试"));
        try {
            borrowRecordMapper.markReturned(recordId, LocalDateTime.now());
            copyMapper.updateStatus(record.getPhysicalCopyId(), CopyStatus.AVAILABLE);
        } finally {
            lock.unlock(key, token);
        }
    }

    public List<BorrowRecordView> listMyBorrows(Long userId) {
        return borrowRecordMapper.listOpenAndRecentByUserId(userId);
    }
}
