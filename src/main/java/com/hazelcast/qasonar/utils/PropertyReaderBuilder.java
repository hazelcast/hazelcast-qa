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

package com.hazelcast.qasonar.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

import static java.lang.Double.valueOf;
import static java.util.Arrays.asList;
import static org.apache.commons.io.IOUtils.closeQuietly;

public final class PropertyReaderBuilder {

    private static final double DEFAULT_MIN_CODE_COVERAGE = 87.5;

    private PropertyReaderBuilder() {
    }

    public static PropertyReader fromPropertyFile() throws IOException {
        File homeDir = new File(System.getProperty("user.home"));
        File propertyFile = new File(homeDir, ".hazelcast-qa");
        return fromPropertyFile(propertyFile.getPath());
    }

    private static PropertyReader fromPropertyFile(String propertyFileName) throws IOException {
        Properties props = new Properties();
        FileInputStream in = null;
        try {
            in = new FileInputStream(propertyFileName);
            props.load(in);
        } finally {
            closeQuietly(in);
        }

        return fromProperties(props);
    }

    private static PropertyReader fromProperties(Properties props) {
        try {
            PropertyReader propertyReader = new PropertyReader(
                    getProperty(props, "host"),
                    getProperty(props, "username"),
                    getProperty(props, "password"));

            addProjectResourceIds(propertyReader, getProperty(props, "projectResourceIds"));

            propertyReader.setLocalGitRoot(getProperty(props, "localGitRoot"));
            propertyReader.setGitHubLogin(getProperty(props, "gitHubLogin"));
            propertyReader.setGitHubToken(getProperty(props, "gitHubToken"));
            propertyReader.setGitHubRepository(getProperty(props, "gitHubRepository"));

            String minCodeCoverageString = getProperty(props, "minCodeCoverage");
            double minCodeCoverage = (minCodeCoverageString == null) ? DEFAULT_MIN_CODE_COVERAGE : valueOf(minCodeCoverageString);
            propertyReader.setMinCodeCoverage(minCodeCoverage, false);

            String minCodeCoverageModifiedString = props.getProperty("minCodeCoverageModified");
            if (minCodeCoverageModifiedString != null) {
                propertyReader.setMinCodeCoverage(valueOf(minCodeCoverageModifiedString), true);
            }

            return propertyReader;
        } catch (Exception e) {
            throw new IllegalStateException("Could not read property file!", e.getCause());
        }
    }

    private static String getProperty(Properties props, String key) {
        String value = props.getProperty(key);
        return (value == null) ? null : value.trim();
    }

    private static void addProjectResourceIds(PropertyReader propertyReader, String projectResourceIdString) {
        if (!projectResourceIdString.contains(",")) {
            propertyReader.addProjectResourceId(projectResourceIdString);
            return;
        }

        asList(projectResourceIdString.split("\\s*,\\s*")).forEach(propertyReader::addProjectResourceId);
    }
}
