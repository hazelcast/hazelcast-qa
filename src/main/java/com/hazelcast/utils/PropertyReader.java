/*
 * Copyright (c) 2008-2017, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PropertyReader {

    private final List<String> projectResourceIds = new ArrayList<>();
    private final Map<GitHubStatus, Double> minCodeCoverage = new HashMap<>();
    private int minThresholdModified;

    private final String host;
    private final String username;
    private final String password;

    private String localGitRoot;
    private String gitHubLogin;
    private String gitHubToken;
    private String gitHubRepository;
    private boolean gitHubRepositoryOverwritten;

    private String defaultModule;
    private boolean throwExceptionOnMissingModule;

    private String outputFile;

    private String milestone;

    public PropertyReader(String host, String username, String password) {
        this.host = host;
        this.username = username;
        this.password = password;
    }

    public String getHost() {
        return host;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public List<String> getProjectResourceIds() {
        return projectResourceIds;
    }

    public void addProjectResourceId(String projectResourceId) {
        projectResourceIds.add(projectResourceId);
    }

    public String getLocalGitRoot() {
        return localGitRoot;
    }

    public void setLocalGitRoot(String localGitRoot) {
        this.localGitRoot = localGitRoot;
    }

    public String getGitHubLogin() {
        return gitHubLogin;
    }

    public void setGitHubLogin(String gitHubLogin) {
        this.gitHubLogin = gitHubLogin;
    }

    public String getGitHubToken() {
        return gitHubToken;
    }

    public void setGitHubToken(String gitHubToken) {
        this.gitHubToken = gitHubToken;
    }

    public String getGitHubRepository() {
        return gitHubRepository;
    }


    public void setGitHubRepository(String gitHubRepository) {
        if (this.gitHubRepository != null) {
            gitHubRepositoryOverwritten = true;
        }
        this.gitHubRepository = gitHubRepository;
    }

    public boolean isGitHubRepositoryOverwritten() {
        return gitHubRepositoryOverwritten;
    }

    public String getDefaultModule() {
        return defaultModule;
    }

    public void setDefaultModule(String defaultModule) {
        this.defaultModule = defaultModule;
    }

    public boolean isDefaultModuleSet() {
        return (defaultModule != null);
    }

    public boolean throwExceptionOnMissingModule() {
        return throwExceptionOnMissingModule;
    }

    public void setThrowExceptionOnMissingModule(boolean throwExceptionOnMissingModule) {
        this.throwExceptionOnMissingModule = throwExceptionOnMissingModule;
    }

    public double getMinCodeCoverage(GitHubStatus gitHubStatus) {
        return minCodeCoverage.get(gitHubStatus);
    }

    public void setMinCodeCoverage(Double codeCoverage, boolean setModifyingStateOnly) {
        for (GitHubStatus gitHubStatus : GitHubStatus.values()) {
            if (setModifyingStateOnly && !gitHubStatus.isModifiedStatus()) {
                continue;
            }
            minCodeCoverage.put(gitHubStatus, codeCoverage);
        }
    }

    public int getMinThresholdModified() {
        return minThresholdModified;
    }

    public void setMinThresholdModified(int minThresholdModified) {
        this.minThresholdModified = minThresholdModified;
    }

    public String getOutputFile() {
        return outputFile;
    }

    public void setOutputFile(String outputFile) {
        this.outputFile = outputFile;
    }

    public String getMilestone() {
        return milestone;
    }

    public void setMilestone(String milestone) {
        this.milestone = milestone;
    }
}
