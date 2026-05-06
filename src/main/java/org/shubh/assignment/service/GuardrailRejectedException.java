package org.shubh.assignment.service;

public class GuardrailRejectedException extends RuntimeException {
    public GuardrailRejectedException(String message) {
        super(message);
    }
}
