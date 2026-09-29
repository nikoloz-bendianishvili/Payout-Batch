package com.example.payout_batch_optimizer.exception;

public class BatchTooLargeException extends RuntimeException {
    public BatchTooLargeException(String message) {
        super(message);
    }
}
