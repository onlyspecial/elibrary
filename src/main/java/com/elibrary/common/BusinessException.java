package com.elibrary.common;

public class BusinessException extends RuntimeException {

    private final ExceptionLevel level;

    public BusinessException(String message) {
        this(ExceptionLevel.WARN, message);
    }

    public BusinessException(ExceptionLevel level, String message) {
        super(message);
        this.level = level == null ? ExceptionLevel.WARN : level;
    }

    public BusinessException(ExceptionLevel level, String message, Throwable cause) {
        super(message, cause);
        this.level = level == null ? ExceptionLevel.WARN : level;
    }

    public ExceptionLevel getLevel() {
        return level;
    }
}
