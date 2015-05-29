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

package com.hazelcast.qasonar;

import com.hazelcast.qa.PropertyReader;
import org.kohsuke.github.GHRepository;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import static com.hazelcast.qa.Utils.getFileContentsFromGitHub;
import static org.apache.commons.io.FilenameUtils.removeExtension;

public class CodeCoverageAnalyzer {

    private final Map<String, TableEntry> tableEntries;
    private final PropertyReader props;
    private final GHRepository repo;

    public CodeCoverageAnalyzer(Map<String, TableEntry> tableEntries, PropertyReader props, GHRepository repo) {
        this.tableEntries = tableEntries;
        this.props = props;
        this.repo = repo;
    }

    public Map<String, TableEntry> getTableEntries() {
        return Collections.unmodifiableMap(tableEntries);
    }

    public void run() throws IOException {
        for (TableEntry tableEntry : tableEntries.values()) {
            String resourceId = tableEntry.resourceId;
            String gitFileName = tableEntry.fileName;
            String simpleName = tableEntry.simpleName;

            if (resourceId == null) {
                checkFileNotFoundInSonar(tableEntry, gitFileName);
                continue;
            }

            if (gitFileName.startsWith("hazelcast-client-new")) {
                tableEntry.fail("new client is not in SonarQube");
                continue;
            }

            if (!simpleName.endsWith(".java") || simpleName.equals("package-info.java")) {
                tableEntry.pass("no Java class");
                continue;
            }

            if (tableEntry.coverage == null) {
                checkFileWithoutCoverage(tableEntry, gitFileName, simpleName);
                continue;
            }

            checkCodeCoverage(tableEntry);
        }
    }

    private void checkFileNotFoundInSonar(TableEntry tableEntry, String gitFileName) {
        if (gitFileName.endsWith(".java")) {
            tableEntry.pass("deleted");
        } else {
            tableEntry.pass("no Java file");
        }
    }

    private void checkFileWithoutCoverage(TableEntry tableEntry, String gitFileName, String simpleName) throws IOException {
        String fileContents = getFileContentsFromGitHub(repo, gitFileName);
        String baseName = removeExtension(simpleName);
        if (fileContents.contains(" interface " + baseName)) {
            tableEntry.pass("Interface");
        } else if (fileContents.contains("@RunWith")) {
            tableEntry.pass("Test");
        } else {
            tableEntry.fail("code coverage not found");
        }
    }

    private void checkCodeCoverage(TableEntry tableEntry) {
        if (tableEntry.numericCoverage > props.getMinCodeCoverage()) {
            tableEntry.pass();
        } else if (tableEntry.comment == null) {
            tableEntry.fail("code coverage below " + props.getMinCodeCoverage() + "%");
        }
    }
}
