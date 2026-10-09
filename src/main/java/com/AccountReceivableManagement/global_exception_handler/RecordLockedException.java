package com.AccountReceivableManagement.global_exception_handler;

public class RecordLockedException extends RuntimeException {
    public RecordLockedException(String message) {
        super(message);
    }
}
