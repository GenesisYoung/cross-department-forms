package com.factory.collab.domain;

/** Effective access of a user to a sheet. Order matters: higher ordinal = more rights. */
public enum AccessLevel {
    NONE, VIEW, EDIT, OWNER;

    public boolean atLeast(AccessLevel other) {
        return this.ordinal() >= other.ordinal();
    }
}
