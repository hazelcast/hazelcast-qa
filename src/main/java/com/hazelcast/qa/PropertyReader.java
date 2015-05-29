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

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class PropertyReader {

    private String host;
    private String username;
    private String password;
    private String projectResourceId;

    private String gitHubRepository;

    private double minCodeCoverage;

    public String getHost() {
        return host;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getProjectResourceId() {
        return projectResourceId;
    }

    public String getGitHubRepository() {
        return gitHubRepository;
    }

    public double getMinCodeCoverage() {
        return minCodeCoverage;
    }

    public void setMinCodeCoverage(double minCodeCoverage) {
        this.minCodeCoverage = minCodeCoverage;
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
            self.projectResourceId = props.getProperty("projectResourceId").trim();

            self.gitHubRepository = props.getProperty("gitHubRepository").trim();

            self.minCodeCoverage = Double.valueOf(props.getProperty("minCodeCoverage").trim());
        } catch (Exception e) {
            System.err.println("Could not read property file! " + e.getMessage());
            throw new IllegalStateException(e);
        }

        return self;
    }
}
