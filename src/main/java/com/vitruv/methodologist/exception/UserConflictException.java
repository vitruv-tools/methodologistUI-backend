package com.vitruv.methodologist.exception;

public class UserConflictException extends RuntimeException {
    public static final String USER_CONFLICT_ERROR = "USER_CONFLICT";
    public static final String messageTemplate = "%s %s is already in use!";

    public UserConflictException(String email, String conflictColumn) {
        super(String.format(messageTemplate, email, conflictColumn));
    }
}
