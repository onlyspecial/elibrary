package com.elibrary.physical;

import com.elibrary.common.BusinessException;
import com.elibrary.common.PageResult;
import com.elibrary.common.lock.RedisDistributedLock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

@Service
public class PhysicalWorkService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 10;
    private static final Duration UNIQ_LOCK_TTL = Duration.ofSeconds(10);
    private static final String UNIQ_LOCK_PREFIX = "uniq:physical_work:";

    private final PhysicalWorkMapper workMapper;
    private final PhysicalCopyMapper copyMapper;
    private final RedisDistributedLock lock;

    public PhysicalWorkService(PhysicalWorkMapper workMapper,
                               PhysicalCopyMapper copyMapper,
                               RedisDistributedLock lock) {
        this.workMapper = workMapper;
        this.copyMapper = copyMapper;
        this.lock = lock;
    }

    /**
     * 读者侧搜索：keyword 为空时不查库，返回空页。
     */
    public PageResult<PhysicalWork> searchForReader(String keyword, Integer page, Integer size) {
        String kw = normalizeKeyword(keyword);
        if (kw == null) {
            int pageNum = page == null || page < 1 ? DEFAULT_PAGE : page;
            int pageSize = size == null || size < 1 ? DEFAULT_SIZE : size;
            return new PageResult<PhysicalWork>(Collections.<PhysicalWork>emptyList(), 0L, pageNum, pageSize);
        }
        return list(kw, page, size);
    }

    public PageResult<PhysicalWork> list(String keyword, Integer page, Integer size) {
        int pageNum = page == null || page < 1 ? DEFAULT_PAGE : page;
        int pageSize = size == null || size < 1 ? DEFAULT_SIZE : size;
        int offset = (pageNum - 1) * pageSize;
        String kw = normalizeKeyword(keyword);
        List<PhysicalWork> items = workMapper.searchActive(kw, offset, pageSize);
        long total = workMapper.countActive(kw);
        return new PageResult<PhysicalWork>(items, total, pageNum, pageSize);
    }

    public PageResult<PhysicalWork> listForAdmin(String keyword, Integer page, Integer size) {
        int pageNum = page == null || page < 1 ? DEFAULT_PAGE : page;
        int pageSize = size == null || size < 1 ? DEFAULT_SIZE : size;
        int offset = (pageNum - 1) * pageSize;
        String kw = normalizeKeyword(keyword);
        List<PhysicalWork> items = workMapper.searchAll(kw, offset, pageSize);
        long total = workMapper.countAll(kw);
        return new PageResult<PhysicalWork>(items, total, pageNum, pageSize);
    }

    public PhysicalWork getActiveDetail(Long id) {
        PhysicalWork work = workMapper.findActiveById(id);
        if (work == null) {
            throw new BusinessException("书目不存在或已下架");
        }
        return work;
    }

    public PhysicalWork requireById(Long id) {
        PhysicalWork work = workMapper.findById(id);
        if (work == null) {
            throw new BusinessException("书目不存在");
        }
        return work;
    }

    @Transactional
    public PhysicalWork create(String title, String author, ContentType contentType,
                               String description, String isbnOrIssn, Long createdBy) {
        validateWorkFields(title, author, contentType);
        String trimmedTitle = title.trim();
        String trimmedAuthor = author.trim();
        String lockKey = uniqLockKey(trimmedTitle, trimmedAuthor);
        String token = lock.tryLock(lockKey, UNIQ_LOCK_TTL)
                .orElseThrow(() -> new BusinessException("操作繁忙，请稍后重试"));
        try {
            assertNoActiveDuplicate(trimmedTitle, trimmedAuthor, null);
            PhysicalWork work = new PhysicalWork();
            work.setTitle(trimmedTitle);
            work.setAuthor(trimmedAuthor);
            work.setContentType(contentType);
            work.setDescription(trimToNull(description));
            work.setIsbnOrIssn(trimToNull(isbnOrIssn));
            work.setDeleted(false);
            work.setCreatedBy(createdBy);
            workMapper.insert(work);
            return work;
        } finally {
            lock.unlock(lockKey, token);
        }
    }

    @Transactional
    public void update(Long id, String title, String author, ContentType contentType,
                       String description, String isbnOrIssn) {
        PhysicalWork existing = requireById(id);
        if (existing.isDeleted()) {
            throw new BusinessException("已下架书目不可编辑");
        }
        validateWorkFields(title, author, contentType);
        String trimmedTitle = title.trim();
        String trimmedAuthor = author.trim();
        String lockKey = uniqLockKey(trimmedTitle, trimmedAuthor);
        String token = lock.tryLock(lockKey, UNIQ_LOCK_TTL)
                .orElseThrow(() -> new BusinessException("操作繁忙，请稍后重试"));
        try {
            assertNoActiveDuplicate(trimmedTitle, trimmedAuthor, id);
            existing.setTitle(trimmedTitle);
            existing.setAuthor(trimmedAuthor);
            existing.setContentType(contentType);
            existing.setDescription(trimToNull(description));
            existing.setIsbnOrIssn(trimToNull(isbnOrIssn));
            workMapper.update(existing);
        } finally {
            lock.unlock(lockKey, token);
        }
    }

    @Transactional
    public void softDelete(Long id) {
        PhysicalWork existing = requireById(id);
        if (existing.isDeleted()) {
            throw new BusinessException("书目已下架");
        }
        workMapper.softDelete(id);
        existing.setDeleted(true);
    }

    public List<PhysicalCopy> listCopies(Long workId) {
        requireById(workId);
        return copyMapper.listByWorkId(workId);
    }

    @Transactional
    public PhysicalCopy addCopy(Long workId, String copyNo) {
        requireById(workId);
        if (copyNo == null || copyNo.trim().isEmpty()) {
            throw new BusinessException("复本编号不能为空");
        }
        PhysicalCopy copy = new PhysicalCopy();
        copy.setWorkId(workId);
        copy.setCopyNo(copyNo.trim());
        copy.setStatus(CopyStatus.AVAILABLE);
        copy.setDeleted(false);
        copyMapper.insert(copy);
        return copy;
    }

    @Transactional
    public void softDeleteCopy(Long copyId) {
        PhysicalCopy copy = copyMapper.findById(copyId);
        if (copy == null) {
            throw new BusinessException("复本不存在");
        }
        if (copy.isDeleted()) {
            throw new BusinessException("复本已下架");
        }
        copyMapper.softDelete(copyId);
        copy.setDeleted(true);
    }

    private void assertNoActiveDuplicate(String title, String author, Long excludeId) {
        PhysicalWork duplicate = workMapper.findActiveByTitleAndAuthor(title, author);
        if (duplicate == null) {
            return;
        }
        if (excludeId != null && excludeId.equals(duplicate.getId())) {
            return;
        }
        throw new BusinessException("已存在相同标题与作者的书目");
    }

    private static String uniqLockKey(String title, String author) {
        return UNIQ_LOCK_PREFIX
                + title.toLowerCase(Locale.ROOT)
                + "|"
                + author.toLowerCase(Locale.ROOT);
    }

    private static void validateWorkFields(String title, String author, ContentType contentType) {
        if (title == null || title.trim().isEmpty()) {
            throw new BusinessException("标题不能为空");
        }
        if (author == null || author.trim().isEmpty()) {
            throw new BusinessException("作者不能为空");
        }
        if (contentType == null) {
            throw new BusinessException("内容类型不能为空");
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    static String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String trimmed = keyword.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
