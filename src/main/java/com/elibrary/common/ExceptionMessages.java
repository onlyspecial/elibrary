package com.elibrary.common;

public final class ExceptionMessages {

    public static final String NETWORK_ERROR = "网络异常，请稍后重试";

    private ExceptionMessages() {
    }

    public static String resolveUserMessage(BusinessException ex) {
        if (ex.getLevel() == ExceptionLevel.ERROR) {
            return NETWORK_ERROR;
        }
        return ex.getMessage();
    }

    public static String resolveUserMessage(Throwable ex) {
        if (ex instanceof BusinessException) {
            return resolveUserMessage((BusinessException) ex);
        }
        return NETWORK_ERROR;
    }
}
