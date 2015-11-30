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

package com.hazelcast.qasonar.codecoverage;

import com.hazelcast.qasonar.utils.GitHubStatus;
import com.hazelcast.qasonar.utils.PropertyReader;
import com.hazelcast.qasonar.utils.WhiteList;
import com.hazelcast.qasonar.utils.WhiteListResult;
import org.kohsuke.github.GHRepository;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import static com.hazelcast.qasonar.utils.Utils.getFileContentsFromGitHub;
import static org.apache.commons.io.FilenameUtils.getBaseName;

public class CodeCoverageAnalyzer {

    private final Map<String, FileContainer> files;
    private final PropertyReader props;
    private final GHRepository repo;
    private final WhiteList whiteList;

    public CodeCoverageAnalyzer(Map<String, FileContainer> files, PropertyReader props, GHRepository repo, WhiteList whiteList) {
        this.files = files;
        this.props = props;
        this.repo = repo;
        this.whiteList = whiteList;
    }

    public Map<String, FileContainer> getFiles() {
        return Collections.unmodifiableMap(files);
    }

    public void run() throws IOException {
        for (FileContainer fileContainer : files.values()) {
            String gitFileName = fileContainer.fileName;

            checkEntryStatus(fileContainer);
            if (fileContainer.isQaCheckSet()) {
                continue;
            }

            checkFileName(fileContainer, gitFileName);
            if (fileContainer.isQaCheckSet()) {
                continue;
            }

            if (fileContainer.coverage == null || fileContainer.numericCoverage < 0.01) {
                checkFileWithoutCoverage(fileContainer, gitFileName);
            } else {
                checkCodeCoverage(fileContainer);
            }
        }
    }

    private void checkEntryStatus(FileContainer fileContainer) {
        if (fileContainer.status == GitHubStatus.REMOVED) {
            fileContainer.pass("deleted");
        }
        if (fileContainer.status == GitHubStatus.RENAMED) {
            fileContainer.pass("renamed");
        }
    }

    private void checkFileName(FileContainer fileContainer, String gitFileName) {
        if (gitFileName.endsWith("package-info.java")) {
            fileContainer.pass("Package info");
        } else if (gitFileName.contains("/src/test/java/")) {
            fileContainer.pass("Test");
        } else if (!gitFileName.endsWith(".java")) {
            fileContainer.pass("no Java file");
        } else {
            WhiteListResult whiteListResult = whiteList.getWhitelistResultOrNull(gitFileName);
            if (whiteListResult != null) {
                if (whiteListResult.isJustification()) {
                    fileContainer.pass("whitelisted\n" + whiteListResult.getJustification());
                } else {
                    fileContainer.comment = whiteListResult.getComment();
                }
            }
        }
    }

    private void checkFileWithoutCoverage(FileContainer fileContainer, String gitFileName) throws IOException {
        String fileContents;
        try {
            fileContents = getFileContentsFromGitHub(repo, gitFileName);
        } catch (FileNotFoundException ignored) {
            fileContainer.pass("deleted in newer PR");
            return;
        } catch (Exception e) {
            throw new IOException("Could not get contents for file " + gitFileName, e.getCause());
        }

        String baseName = getBaseName(gitFileName);
        if (fileContents.contains(" interface " + baseName)) {
            fileContainer.pass("Interface");
        } else if (fileContents.contains(" enum " + baseName)) {
            fileContainer.pass("Enum");
        } else if (fileContents.contains(" @interface " + baseName)) {
            fileContainer.pass("Annotation");
        } else if (fileContainer.coverage == null) {
            fileContainer.fail("code coverage not found");
        } else {
            double minCodeCoverage = props.getMinCodeCoverage(fileContainer.status);
            fileContainer.fail("code coverage below " + minCodeCoverage + "%");
        }
    }

    private void checkCodeCoverage(FileContainer fileContainer) {
        double minCodeCoverage = props.getMinCodeCoverage(fileContainer.status);
        if (fileContainer.numericCoverage < minCodeCoverage) {
            fileContainer.fail("code coverage below " + minCodeCoverage + "%");
        } else {
            fileContainer.pass();
        }
    }
}
