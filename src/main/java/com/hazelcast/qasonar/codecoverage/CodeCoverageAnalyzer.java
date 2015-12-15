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
import static com.hazelcast.qasonar.utils.Utils.debugGreen;
import static com.hazelcast.qasonar.utils.Utils.debugRed;
import static com.hazelcast.qasonar.utils.Utils.debugYellow;
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

    private void checkFileWithoutCoverage(FileContainer fileContainer, String gitFileName) throws IOException {
        if (fileContainer.coverage != null || fileContainer.ideaCoverage > COVERAGE_MARGIN) {
            return;
        }

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

        fileContainer.fail("code coverage not found");
        debugRed("Failed with code coverage not found %s", gitFileName);
    }

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
        fileContainer.fail(format("code coverage %.1f%% (%.1f%%) (%s)", failedCoverage, diff, coverageType));
        debugYellow("Failed with code coverage %5.1f%% (%6.1f%%) (%-5s) %s", failedCoverage, diff, coverageType,
                fileContainer.fileName);
    }

    private boolean isIdeaCoverageSignificantlyHigher(FileContainer fileContainer) {
        return (fileContainer.ideaCoverage > fileContainer.numericLineCoverage + MIN_IDEA_COVERAGE_DIFF);
    }
}
