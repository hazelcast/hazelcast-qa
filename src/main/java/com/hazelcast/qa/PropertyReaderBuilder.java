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
import java.util.Properties;

import static java.util.Arrays.asList;

public final class PropertyReaderBuilder {

    private PropertyReaderBuilder() {
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
        try {
            PropertyReader propertyReader = new PropertyReader(
                    getProperty(props, "host"),
                    getProperty(props, "username"),
                    getProperty(props, "password"));

            addProjectResourceIds(propertyReader, getProperty(props, "projectResourceIds"));

            propertyReader.setGitHubRepository(getProperty(props, "gitHubRepository"));

            double minCodeCoverage = Double.valueOf(getProperty(props, "minCodeCoverage"));
            propertyReader.setMinCodeCoverage(minCodeCoverage);

            if (props.getProperty("minCodeCoverageModified") != null) {
                minCodeCoverage = Double.valueOf(getProperty(props, "minCodeCoverageModified"));
                propertyReader.setMinCodeCoverage(GitHubStatus.MODIFIED, minCodeCoverage);
                propertyReader.setMinCodeCoverage(GitHubStatus.RENAMED, minCodeCoverage);
            }

            return propertyReader;
        } catch (Exception e) {
            throw new IllegalStateException("Could not read property file!", e.getCause());
        }
    }

    private static String getProperty(Properties props, String key) {
        return props.getProperty(key).trim();
    }

    private static void addProjectResourceIds(PropertyReader propertyReader, String projectResourceIdString) {
        if (!projectResourceIdString.contains(",")) {
            propertyReader.addProjectResourceId(projectResourceIdString);
            return;
        }

        for (String projectResourceId : asList(projectResourceIdString.split("\\s*,\\s*"))) {
            propertyReader.addProjectResourceId(projectResourceId);
        }
    }
}
