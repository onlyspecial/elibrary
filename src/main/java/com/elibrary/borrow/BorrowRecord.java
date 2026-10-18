package com.elibrary.borrow;

import java.time.LocalDateTime;

public class BorrowRecord {

    private Long id;
    private Long userId;
    private Long physicalWorkId;
    private Long physicalCopyId;
    private LocalDateTime borrowedAt;
    private LocalDateTime returnedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getPhysicalWorkId() {
        return physicalWorkId;
    }

    public void setPhysicalWorkId(Long physicalWorkId) {
        this.physicalWorkId = physicalWorkId;
    }

    public Long getPhysicalCopyId() {
        return physicalCopyId;
    }

    public void setPhysicalCopyId(Long physicalCopyId) {
        this.physicalCopyId = physicalCopyId;
    }

    public LocalDateTime getBorrowedAt() {
        return borrowedAt;
    }

    public void setBorrowedAt(LocalDateTime borrowedAt) {
        this.borrowedAt = borrowedAt;
    }

    public LocalDateTime getReturnedAt() {
        return returnedAt;
    }

    public void setReturnedAt(LocalDateTime returnedAt) {
        this.returnedAt = returnedAt;
    }
}
