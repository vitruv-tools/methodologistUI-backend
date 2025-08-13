package com.vitruv.methodologist.exception;

public class NotFoundException extends RuntimeException {
    public static final String messageTemplate = "%s not found!";

    public NotFoundException(String objectName) {
        super(String.format(messageTemplate, objectName));
    }
}
