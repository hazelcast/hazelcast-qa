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

import static com.hazelcast.qasonar.codecoverage.FileContainer.CoverageType.IDEA;
import static com.hazelcast.qasonar.codecoverage.FileContainer.CoverageType.SONAR;
import static com.hazelcast.qasonar.utils.Utils.debug;
import static com.hazelcast.qasonar.utils.Utils.getFileContentsFromGitHub;
import static java.lang.String.format;
import static org.apache.commons.io.FilenameUtils.getBaseName;

public class CodeCoverageAnalyzer {

    private static final double MIN_IDEA_COVERAGE_DIFF = 0.5;
    private static final double COVERAGE_MARGIN = 0.01;

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

            if (fileContainer.coverage == null || fileContainer.numericCoverage < COVERAGE_MARGIN) {
                checkFileWithoutCoverage(fileContainer, gitFileName);
            } else {
                checkCodeCoverage(fileContainer);
            }
        }
    }

    private void checkEntryStatus(FileContainer fileContainer) {
        if (fileContainer.status == GitHubStatus.REMOVED) {
            fileContainer.pass("deleted");
            return;
        }
        if (fileContainer.status == GitHubStatus.RENAMED && fileContainer.gitHubChanges == 0) {
            fileContainer.pass("renamed");
            return;
        }
        if (fileContainer.status == GitHubStatus.CHANGED && fileContainer.gitHubChanges == 0) {
            fileContainer.pass("no changes");
        }
    }

    private void checkFileName(FileContainer fileContainer, String gitFileName) {
        if (!gitFileName.endsWith(".java")) {
            fileContainer.pass("no Java file");
            return;
        }
        if (gitFileName.endsWith("package-info.java")) {
            fileContainer.pass("Package info");
            return;
        }
        if (gitFileName.contains("/src/test/java/")) {
            fileContainer.pass("Test");
            return;
        }
        WhiteListResult whiteListResult = whiteList.getWhitelistResultOrNull(gitFileName);
        if (whiteListResult != null) {
            if (whiteListResult.isJustification()) {
                fileContainer.pass("whitelisted\n" + whiteListResult.getJustification());
            } else {
                fileContainer.comment = whiteListResult.getComment();
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
            return;
        }
        if (fileContents.contains(" enum " + baseName)) {
            fileContainer.pass("Enum");
            return;
        }
        if (fileContents.contains(" @interface " + baseName)) {
            fileContainer.pass("Annotation");
            return;
        }
        if (fileContainer.coverage == null && fileContainer.ideaCoverage < COVERAGE_MARGIN) {
            fileContainer.fail("code coverage not found");
            debug(format("Failed with no coverage %s", gitFileName));
            return;
        }
        checkCodeCoverage(fileContainer);
    }

    private void checkCodeCoverage(FileContainer fileContainer) {
        double minCodeCoverage = props.getMinCodeCoverage(fileContainer.status);
        double sonarCoverage = fileContainer.numericCoverage;
        double ideaCoverage = fileContainer.ideaCoverage;
        if (sonarCoverage >= minCodeCoverage) {
            fileContainer.useForCoverageCalculation(SONAR);
            fileContainer.pass();
            return;
        }
        if (ideaCoverage >= minCodeCoverage && isIdeaCoverageSignificantlyHigher(fileContainer)) {
            fileContainer.useForCoverageCalculation(IDEA);
            fileContainer.pass(format("IDEA coverage: %.1f%%", ideaCoverage));
            debug(format("Passed with IDEA coverage %5.1f%% (%+6.1f%%) %s", ideaCoverage, (ideaCoverage - sonarCoverage),
                    fileContainer.fileName));
            return;
        }
        double failedCoverage = isIdeaCoverageSignificantlyHigher(fileContainer) ? ideaCoverage : sonarCoverage;
        fileContainer.fail("code coverage below " + minCodeCoverage + "%");
        debug((format("Failed with code coverage %5.1f%% (%6.1f%%) %s", failedCoverage, failedCoverage - minCodeCoverage,
                fileContainer.fileName)));
    }

    private boolean isIdeaCoverageSignificantlyHigher(FileContainer fileContainer) {
        return (fileContainer.ideaCoverage > fileContainer.numericLineCoverage + MIN_IDEA_COVERAGE_DIFF);
    }
}
