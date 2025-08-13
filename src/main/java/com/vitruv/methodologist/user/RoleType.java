package com.vitruv.methodologist.user;

import com.fasterxml.jackson.annotation.JsonValue;

public enum RoleType {
    USER("user");

    private final String name;
    private RoleType(final String name) {
        this.name = name;
    }

    @JsonValue
    public String getName() { return name; }
}