package com.goodda.jejuday.auth.util.exception;

public class EmailSendingException extends RuntimeException {
    public EmailSendingException(String message, Throwable cause) {
        super(message, cause);
    }

    public EmailSendingException(String message) {
        super(message);
    }
}
