package com.hazelcast.qasonar.utils;

import java.util.List;

import static java.util.Arrays.asList;

public enum Repository {

    OS(
            "Open Source",
            "hazelcast",
            null,
            asList("opensource", "os")
    ),
    EE(
            "Enterprise",
            "hazelcast-enterprise",
            null,
            asList("enterprise", "ee")
    ),
    MC(
            "MC",
            "management-center",
            "mancenter",
            asList("mancenter", "mc")
    ),
    JCLOUDS(
            "jclouds",
            "hazelcast-jclouds",
            "hazelcast-jclouds",
            asList("jclouds", "jc")
    );

    private final String description;
    private final String repositoryName;
    private final String defaultModule;
    private final List<String> suffixList;

    Repository(String description, String repositoryName, String defaultModule, List<String> suffixList) {
        this.description = description;
        this.repositoryName = repositoryName;
        this.defaultModule = defaultModule;
        this.suffixList = suffixList;
    }

    public boolean hasDefaultModule() {
        return (defaultModule != null);
    }

    public String getDefaultModule() {
        return defaultModule;
    }

    @Override
    public String toString() {
        return description;
    }

    public static Repository fromRepositoryName(String name) {
        for (Repository repository : values()) {
            if (repository.repositoryName.equals(name)) {
                return repository;
            }
        }
        throw new IllegalArgumentException("Unknown repository: " + name);
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
}
