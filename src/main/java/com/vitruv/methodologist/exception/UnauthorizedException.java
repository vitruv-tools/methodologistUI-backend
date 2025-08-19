package com.vitruv.methodologist.exception;

public class UnauthorizedException extends RuntimeException {
    public static final String messageTemplate = "Unauthorized access";

    public UnauthorizedException() {
        super(messageTemplate);
    }
}