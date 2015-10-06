package com.hazelcast.qasonar.utils;

public class WhiteListResult {

    private final String justification;
    private final String comment;

    public WhiteListResult(String justification, String comment) {
        this.justification = justification;
        this.comment = comment;
    }

    public String getJustification() {
        return justification;
    }

    public String getComment() {
        return comment;
    }

    public boolean isJustification() {
        return (justification != null);
    }
}
