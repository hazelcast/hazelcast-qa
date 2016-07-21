package com.hazelcast.qasonar.utils;

import java.util.List;

import static java.util.Arrays.asList;

public enum Repository {

    OS("Open Source", asList("opensource", "os")),
    EE("Enterprise", asList("enterprise", "ee")),
    MC("MC", asList("mancenter", "mc")),
    JCLOUDS("jclouds", asList("jclouds", "jc"));

    private final String description;
    private final List<String> suffixList;

    Repository(String description, List<String> suffixList) {
        this.description = description;
        this.suffixList = suffixList;
    }

    @Override
    public String toString() {
        return description;
    }

    public static String getSuffixes(String separator) {
        String sep = "";
        StringBuilder sb = new StringBuilder();
        for (Repository repository : values()) {
            for (String suffix : repository.suffixList) {
                sb.append(sep).append(suffix);
                sep = separator;
            }
        }
        return sb.toString();
    }

    public static Repository fromSuffix(String suffix) {
        for (Repository repository : values()) {
            for (String suf : repository.suffixList) {
                if (suf.equals(suffix)) {
                    return repository;
                }
            }
        }
        return null;
    }
}
