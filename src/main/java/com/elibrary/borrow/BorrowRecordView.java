package com.elibrary.borrow;

import java.time.LocalDateTime;

public class BorrowRecordView {

    private Long id;
    private Long userId;
    private Long physicalCopyId;
    private LocalDateTime borrowedAt;
    private LocalDateTime returnedAt;
    private String title;
    private String copyNo;
    private boolean copyDeleted;
    private boolean workDeleted;

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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCopyNo() {
        return copyNo;
    }

    public void setCopyNo(String copyNo) {
        this.copyNo = copyNo;
    }

    public boolean isCopyDeleted() {
        return copyDeleted;
    }

    public void setCopyDeleted(boolean copyDeleted) {
        this.copyDeleted = copyDeleted;
    }

    public boolean isWorkDeleted() {
        return workDeleted;
    }

    public void setWorkDeleted(boolean workDeleted) {
        this.workDeleted = workDeleted;
    }

    public boolean isOpen() {
        return returnedAt == null;
    }
}
