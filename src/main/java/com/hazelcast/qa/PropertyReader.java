/*
 * Copyright (c) 2008-2015, Hazelcast, Inc. All Rights Reserved.
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

package com.hazelcast.qa;

import com.hazelcast.qasonar.GitHubStatus;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static java.util.Arrays.asList;

public class PropertyReader {

    private final List<String> projectResourceIds = new ArrayList<String>();
    private final Map<GitHubStatus, Double> minCodeCoverage = new HashMap<GitHubStatus, Double>();

    private String host;
    private String username;
    private String password;

    private String gitHubRepository;
    private boolean gitHubRepositoryOverwritten;

    private String outputFile;

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

    public String getGitHubRepository() {
        return gitHubRepository;
    }

    public void setGitHubRepository(String gitHubRepository) {
        this.gitHubRepository = gitHubRepository;
        gitHubRepositoryOverwritten = true;
    }

    public boolean isGitHubRepositoryOverwritten() {
        return gitHubRepositoryOverwritten;
    }

    public double getMinCodeCoverage(GitHubStatus gitHubStatus) {
        return minCodeCoverage.get(gitHubStatus);
    }

    public void setMinCodeCoverage(Double codeCoverage) {
        for (GitHubStatus gitHubStatus : GitHubStatus.values()) {
            minCodeCoverage.put(gitHubStatus, codeCoverage);
        }
    }

    public void setMinCodeCoverage(GitHubStatus status, Double codeCoverage) {
        minCodeCoverage.put(status, codeCoverage);
    }

    public String getOutputFile() {
        return outputFile;
    }

    public void setOutputFile(String outputFile) {
        this.outputFile = outputFile;
    }

    public static PropertyReader fromPropertyFile() throws IOException {
        File homeDir = new File(System.getProperty("user.home"));
        File propertyFile = new File(homeDir, ".hazelcast-qa");
        return fromPropertyFile(propertyFile.getPath());
    }

    public static PropertyReader fromPropertyFile(String propertyFileName) throws IOException {
        Properties props = new Properties();
        FileInputStream in = null;
        try {
            in = new FileInputStream(propertyFileName);
            props.load(in);
        } finally {
            IOUtils.closeQuietly(in);
        }

        return fromProperties(props);
    }

    public static PropertyReader fromProperties(Properties props) {
        PropertyReader self = new PropertyReader();

        try {
            self.host = props.getProperty("host").trim();
            self.username = props.getProperty("username").trim();
            self.password = props.getProperty("password").trim();
            addProjectResourceIds(self, props.getProperty("projectResourceIds").trim());

            self.gitHubRepository = props.getProperty("gitHubRepository").trim();

            double minCodeCoverage = Double.valueOf(props.getProperty("minCodeCoverage").trim());
            for (GitHubStatus gitHubStatus : GitHubStatus.values()) {
                self.minCodeCoverage.put(gitHubStatus, minCodeCoverage);
            }

            if (props.getProperty("minCodeCoverageModified") != null) {
                minCodeCoverage = Double.valueOf(props.getProperty("minCodeCoverageModified").trim());
                self.minCodeCoverage.put(GitHubStatus.MODIFIED, minCodeCoverage);
                self.minCodeCoverage.put(GitHubStatus.RENAMED, minCodeCoverage);
            }
        } catch (Exception e) {
            System.err.println("Could not read property file! " + e.getMessage());
            throw new IllegalStateException(e);
        }

        return self;
    }

    private static void addProjectResourceIds(PropertyReader props, String projectResourceIdString) {
        if (!projectResourceIdString.contains(",")) {
            props.projectResourceIds.add(projectResourceIdString);
            return;
        }

        for (String projectResourceId : asList(projectResourceIdString.split("\\s*,\\s*"))) {
            props.projectResourceIds.add(projectResourceId);
        }
    }
}
