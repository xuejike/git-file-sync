package com.github.xuejike.sync.file.exception;

/**
 * @author xuejike
 * @date 2020/11/27
 */
public class FileSyncException  extends RuntimeException{
    public FileSyncException() {
    }

    public FileSyncException(String message) {
        super(message);
    }

    public FileSyncException(String message, Throwable cause) {
        super(message, cause);
    }

    public FileSyncException(Throwable cause) {
        super(cause);
    }

    public FileSyncException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
