package com.elibrary.digital;

import com.elibrary.common.BusinessException;
import com.elibrary.common.PageResult;
import com.elibrary.common.lock.RedisDistributedLock;
import com.elibrary.file.LocalPdfStorage;
import com.elibrary.physical.ContentType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

@Service
public class DigitalItemService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 10;
    private static final Duration UNIQ_LOCK_TTL = Duration.ofSeconds(10);
    private static final String UNIQ_LOCK_PREFIX = "uniq:digital_item:";

    private final DigitalItemMapper digitalItemMapper;
    private final LocalPdfStorage localPdfStorage;
    private final RedisDistributedLock lock;

    public DigitalItemService(DigitalItemMapper digitalItemMapper,
                              LocalPdfStorage localPdfStorage,
                              RedisDistributedLock lock) {
        this.digitalItemMapper = digitalItemMapper;
        this.localPdfStorage = localPdfStorage;
        this.lock = lock;
    }

    /**
     * 读者侧搜索：keyword 为空时不查库，返回空页。
     */
    public PageResult<DigitalItem> searchForReader(String keyword, Integer page, Integer size) {
        String kw = normalizeKeyword(keyword);
        if (kw == null) {
            int pageNum = page == null || page < 1 ? DEFAULT_PAGE : page;
            int pageSize = size == null || size < 1 ? DEFAULT_SIZE : size;
            return new PageResult<DigitalItem>(Collections.<DigitalItem>emptyList(), 0L, pageNum, pageSize);
        }
        return list(kw, page, size);
    }

    public PageResult<DigitalItem> list(String keyword, Integer page, Integer size) {
        int pageNum = page == null || page < 1 ? DEFAULT_PAGE : page;
        int pageSize = size == null || size < 1 ? DEFAULT_SIZE : size;
        int offset = (pageNum - 1) * pageSize;
        String kw = normalizeKeyword(keyword);
        List<DigitalItem> items = digitalItemMapper.searchActive(kw, offset, pageSize);
        long total = digitalItemMapper.countActive(kw);
        return new PageResult<DigitalItem>(items, total, pageNum, pageSize);
    }

    public PageResult<DigitalItem> listForAdmin(String keyword, Integer page, Integer size) {
        int pageNum = page == null || page < 1 ? DEFAULT_PAGE : page;
        int pageSize = size == null || size < 1 ? DEFAULT_SIZE : size;
        int offset = (pageNum - 1) * pageSize;
        String kw = normalizeKeyword(keyword);
        List<DigitalItem> items = digitalItemMapper.searchAll(kw, offset, pageSize);
        long total = digitalItemMapper.countAll(kw);
        return new PageResult<DigitalItem>(items, total, pageNum, pageSize);
    }

    public DigitalItem getActiveDetail(Long id) {
        return requireActive(id);
    }

    public DigitalItem requireActive(Long id) {
        DigitalItem item = digitalItemMapper.findActiveById(id);
        if (item == null) {
            throw new BusinessException("电子书不存在或已下架");
        }
        return item;
    }

    public DigitalItem requireById(Long id) {
        DigitalItem item = digitalItemMapper.findById(id);
        if (item == null) {
            throw new BusinessException("电子书不存在");
        }
        return item;
    }

    @Transactional
    public DigitalItem create(String title, String author, ContentType contentType,
                              String description, String isbnOrIssn,
                              MultipartFile pdfFile, Long createdBy) {
        validateMeta(title, author, contentType);
        if (pdfFile == null || pdfFile.isEmpty()) {
            throw new BusinessException("请上传 PDF 文件");
        }
        String trimmedTitle = title.trim();
        String trimmedAuthor = author.trim();
        String lockKey = uniqLockKey(trimmedTitle, trimmedAuthor);
        String token = lock.tryLock(lockKey, UNIQ_LOCK_TTL)
                .orElseThrow(() -> new BusinessException("操作繁忙，请稍后重试"));
        try {
            assertNoActiveDuplicate(trimmedTitle, trimmedAuthor, null);
            String storedName = localPdfStorage.save(pdfFile);
            DigitalItem item = new DigitalItem();
            item.setTitle(trimmedTitle);
            item.setAuthor(trimmedAuthor);
            item.setContentType(contentType);
            item.setDescription(trimToNull(description));
            item.setIsbnOrIssn(trimToNull(isbnOrIssn));
            item.setPdfPath(storedName);
            item.setPdfOriginalName(pdfFile.getOriginalFilename());
            item.setDeleted(false);
            item.setCreatedBy(createdBy);
            digitalItemMapper.insert(item);
            return item;
        } finally {
            lock.unlock(lockKey, token);
        }
    }

    @Transactional
    public void update(Long id, String title, String author, ContentType contentType,
                       String description, String isbnOrIssn, MultipartFile pdfFile) {
        DigitalItem existing = requireById(id);
        if (existing.isDeleted()) {
            throw new BusinessException("已下架电子书不可编辑");
        }
        validateMeta(title, author, contentType);
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
            if (pdfFile != null && !pdfFile.isEmpty()) {
                String storedName = localPdfStorage.save(pdfFile);
                existing.setPdfPath(storedName);
                existing.setPdfOriginalName(pdfFile.getOriginalFilename());
            }
            digitalItemMapper.update(existing);
        } finally {
            lock.unlock(lockKey, token);
        }
    }

    @Transactional
    public void softDelete(Long id) {
        DigitalItem existing = requireById(id);
        if (existing.isDeleted()) {
            throw new BusinessException("电子书已下架");
        }
        digitalItemMapper.softDelete(id);
        existing.setDeleted(true);
    }

    private void assertNoActiveDuplicate(String title, String author, Long excludeId) {
        DigitalItem duplicate = digitalItemMapper.findActiveByTitleAndAuthor(title, author);
        if (duplicate == null) {
            return;
        }
        if (excludeId != null && excludeId.equals(duplicate.getId())) {
            return;
        }
        throw new BusinessException("已存在相同标题与作者的电子书");
    }

    private static String uniqLockKey(String title, String author) {
        return UNIQ_LOCK_PREFIX
                + title.toLowerCase(Locale.ROOT)
                + "|"
                + author.toLowerCase(Locale.ROOT);
    }

    private static void validateMeta(String title, String author, ContentType contentType) {
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
