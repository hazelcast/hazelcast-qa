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

package com.hazelcast.qasonar.codecoverage;

import com.hazelcast.qasonar.utils.WhiteList;
import com.hazelcast.qasonar.utils.WhiteListResult;
import com.hazelcast.utils.GitHubStatus;
import com.hazelcast.utils.PropertyReader;
import com.hazelcast.utils.Repository;
import org.kohsuke.github.GHRepository;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Map;

import static com.hazelcast.qasonar.codecoverage.FileContainer.CoverageType.IDEA;
import static com.hazelcast.qasonar.codecoverage.FileContainer.CoverageType.SONAR;
import static com.hazelcast.utils.DebugUtils.debug;
import static com.hazelcast.utils.DebugUtils.debugGreen;
import static com.hazelcast.utils.DebugUtils.debugRed;
import static com.hazelcast.utils.DebugUtils.debugYellow;
import static com.hazelcast.utils.GitHubStatus.ADDED;
import static com.hazelcast.utils.GitHubUtils.getFileContentsFromGitHub;
import static com.hazelcast.utils.Repository.fromRepositoryName;
import static com.hazelcast.utils.Utils.readFromFile;
import static java.lang.String.format;
import static java.util.Collections.unmodifiableMap;
import static org.apache.commons.io.FilenameUtils.getBaseName;

class CodeCoverageAnalyzer {

    private static final double MIN_IDEA_COVERAGE_DIFF = 0.5;
    private static final double COVERAGE_MARGIN = 0.01;

    private final Map<String, FileContainer> files;
    private final PropertyReader props;
    private final GHRepository repo;
    private final WhiteList whiteList;
    private final String localGitRoot;

    CodeCoverageAnalyzer(Map<String, FileContainer> files, PropertyReader props, GHRepository repo, WhiteList whiteList) {
        this.files = files;
        this.props = props;
        this.repo = repo;
        this.whiteList = whiteList;
        this.localGitRoot = getLocalGitRoot(props, repo);
    }

    private String getLocalGitRoot(PropertyReader props, GHRepository repo) {
        if (props.getLocalGitRoot() == null || props.getLocalGitRoot().isEmpty()) {
            return null;
        }
        Repository repository = fromRepositoryName(repo.getName());
        File localGitRoot = new File(props.getLocalGitRoot() + repository.getRepositoryName()).getAbsoluteFile();
        if (!localGitRoot.isDirectory()) {
            debugRed("Could not find local Git repository at: " + localGitRoot.getAbsolutePath());
            return null;
        }
        debug("Using local Git repository at: " + localGitRoot);
        return localGitRoot.getAbsolutePath() + File.separatorChar;
    }

    Map<String, FileContainer> getFiles() {
        return unmodifiableMap(files);
    }

    void run() throws IOException {
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

            checkFileWithoutCoverage(fileContainer, gitFileName);
            if (fileContainer.isQaCheckSet()) {
                continue;
            }

            checkCodeCoverage(fileContainer);
        }
    }

    private void checkEntryStatus(FileContainer fileContainer) {
        if (fileContainer.isModuleDeleted) {
            fileContainer.pass("module deleted");
            return;
        }
        if (fileContainer.status == GitHubStatus.REMOVED) {
            fileContainer.pass("deleted");
            return;
        }
        if (fileContainer.status == GitHubStatus.RENAMED && fileContainer.gitHubChanges <= 2) {
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

    @SuppressWarnings("checkstyle:npathcomplexity")
    private void checkFileWithoutCoverage(FileContainer fileContainer, String gitFileName) {
        if (fileContainer.coverage != null || fileContainer.ideaCoverage > COVERAGE_MARGIN) {
            return;
        }

        String fileContents;
        try {
            if (localGitRoot == null) {
                fileContents = getFileContentsFromGitHub(repo, gitFileName);
            } else {
                fileContents = readFromFile(localGitRoot + gitFileName);
            }
        } catch (FileNotFoundException ignored) {
            fileContainer.pass("deleted in newer PR");
            return;
        } catch (Exception e) {
            fileContainer.fail("Could not get contents for file " + gitFileName + ": " + e.getCause());
            debugRed("Failed with file content not retrievable %s: %s", gitFileName, e.getCause());
            return;
        }

        String baseName = getBaseName(gitFileName);
        if (fileContents.contains("@interface " + baseName)) {
            fileContainer.pass("Annotation");
            return;
        }
        if (fileContents.contains("interface " + baseName)) {
            fileContainer.pass("Interface");
            return;
        }
        if (fileContents.contains("enum " + baseName)) {
            fileContainer.pass("Enum");
            return;
        }

        if (isBelowMinThresholdModified(fileContainer)) {
            fileContainer.pass(format("under threshold with %d changed lines", fileContainer.gitHubChanges));
            debugGreen("Passed with code coverage %5.1f%% %s (%d lines changed below threshold)", 0f, fileContainer.fileName,
                    fileContainer.gitHubChanges);
            return;
        }

        fileContainer.fail("code coverage not found");
        debugRed("Failed with code coverage not found %s", gitFileName);
    }

    @SuppressWarnings("checkstyle:npathcomplexity")
    private void checkCodeCoverage(FileContainer fileContainer) {
        double minCodeCoverage = props.getMinCodeCoverage(fileContainer.status);
        boolean useIdeaCoverage = isIdeaCoverageSignificantlyHigher(fileContainer);
        fileContainer.useForCoverageCalculation(useIdeaCoverage ? IDEA : SONAR);

        double sonarCoverage = fileContainer.numericCoverage;
        if (sonarCoverage >= minCodeCoverage) {
            fileContainer.pass();
            return;
        }

        double ideaCoverage = fileContainer.ideaCoverage;
        if (ideaCoverage >= minCodeCoverage && useIdeaCoverage) {
            fileContainer.pass(format("IDEA coverage: %.1f%%", ideaCoverage));
            debugGreen("Passed with code coverage %5.1f%% (%+6.1f%%) (IDEA ) %s", ideaCoverage, (ideaCoverage - sonarCoverage),
                    fileContainer.fileName);
            return;
        }

        double failedCoverage = useIdeaCoverage ? ideaCoverage : sonarCoverage;
        double diff = failedCoverage - minCodeCoverage;
        String coverageType = useIdeaCoverage ? "IDEA" : "Sonar";

        if (isBelowMinThresholdModified(fileContainer)) {
            fileContainer.pass(format("under threshold with %d changed lines\ncode coverage %.1f%% (%.1f%%) (%s)",
                    fileContainer.gitHubChanges, failedCoverage, diff, coverageType));
            debugGreen("Passed with code coverage %5.1f%% (%6.1f%%) (%-5s) %s (%d lines changed below threshold)", failedCoverage,
                    diff, coverageType, fileContainer.fileName, fileContainer.gitHubChanges);
            return;
        }

        fileContainer.fail(format("code coverage %.1f%% (%.1f%%) (%s)", failedCoverage, diff, coverageType));
        debugYellow("Failed with code coverage %5.1f%% (%6.1f%%) (%-5s) %s", failedCoverage, diff, coverageType,
                fileContainer.fileName);
    }

    private boolean isIdeaCoverageSignificantlyHigher(FileContainer fileContainer) {
        return (fileContainer.ideaCoverage > fileContainer.numericLineCoverage + MIN_IDEA_COVERAGE_DIFF);
    }

    private boolean isBelowMinThresholdModified(FileContainer fileContainer) {
        int minThresholdModified = props.getMinThresholdModified();
        return (fileContainer.status != ADDED && minThresholdModified > 0 && fileContainer.gitHubChanges <= minThresholdModified);
    }
}
