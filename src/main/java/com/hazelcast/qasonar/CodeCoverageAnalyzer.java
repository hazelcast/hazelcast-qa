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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import static com.hazelcast.qa.Utils.getFileContentsFromGitHub;
import static org.apache.commons.io.FilenameUtils.getBaseName;

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
            String gitFileName = tableEntry.fileName;
            String baseName = getBaseName(gitFileName);

            checkEntryStatus(tableEntry);
            if (tableEntry.isQaCheckSet()) {
                continue;
            }

            checkFileName(tableEntry, gitFileName, baseName);
            if (tableEntry.isQaCheckSet()) {
                continue;
            }

            if (tableEntry.coverage == null) {
                checkFileWithoutCoverage(tableEntry, gitFileName, baseName);
            } else {
                checkCodeCoverage(tableEntry);
            }
        }
    }

    private void checkEntryStatus(TableEntry tableEntry) {
        if (tableEntry.status == GitHubStatus.REMOVED) {
            tableEntry.pass(GitHubStatus.REMOVED.toString());
        }
    }

    private void checkFileName(TableEntry tableEntry, String gitFileName, String baseName) {
        if (baseName.equals("package-info")) {
            tableEntry.pass("Package info");
        } else if (gitFileName.contains("/src/test/java/")) {
            tableEntry.pass("Test");
        } else if (!gitFileName.endsWith(".java")) {
            tableEntry.pass("no Java file");
        } else if (gitFileName.matches(".*/client/[^/]+Request\\.java")) {
            tableEntry.pass("whitelisted cross module");
        }
    }

    private void checkFileWithoutCoverage(TableEntry tableEntry, String gitFileName, String baseName) throws IOException {
        String fileContents;
        try {
            fileContents = getFileContentsFromGitHub(repo, gitFileName);
        } catch (FileNotFoundException ignored) {
            tableEntry.pass("deleted in newer PR");
            return;
        }
        if (fileContents.contains(" interface " + baseName)) {
            tableEntry.pass("Interface");
        } else if (fileContents.contains(" @interface " + baseName)) {
            tableEntry.pass("Annotation");
        } else {
            tableEntry.fail("code coverage not found");
        }
    }

    private void checkCodeCoverage(TableEntry tableEntry) {
        double minCodeCoverage = props.getMinCodeCoverage(tableEntry.status);
        if (tableEntry.numericCoverage > minCodeCoverage) {
            tableEntry.pass();
        } else if (tableEntry.comment == null) {
            tableEntry.fail("code coverage below " + minCodeCoverage + "%");
        }
    }
}
