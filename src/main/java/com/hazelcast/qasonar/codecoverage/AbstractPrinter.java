/*
 * Copyright (c) 2008-2016, Hazelcast, Inc. All Rights Reserved.
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

import com.hazelcast.qasonar.utils.CommandLineOptions;
import com.hazelcast.qasonar.utils.GitHubStatus;
import com.hazelcast.qasonar.utils.PropertyReader;

import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import static com.hazelcast.qasonar.utils.DebugUtils.debug;
import static com.hazelcast.qasonar.utils.Utils.formatCoverage;
import static com.hazelcast.qasonar.utils.Utils.formatFileName;
import static com.hazelcast.qasonar.utils.Utils.formatGitHubChanges;
import static com.hazelcast.qasonar.utils.Utils.formatGitHubStatus;
import static com.hazelcast.qasonar.utils.Utils.formatNullable;
import static com.hazelcast.qasonar.utils.Utils.formatPullRequestLinks;
import static com.hazelcast.qasonar.utils.Utils.formatSonarQubeLink;
import static java.lang.Double.compare;
import static java.lang.String.format;

abstract class AbstractPrinter {

    static final int FILE_NAME_WIDTH = 100;
    static final int COMMENT_WIDTH = 25;

    private final Map<String, FileContainer> files;
    private final PropertyReader props;
    private final CommandLineOptions commandLineOptions;

    private final String spacer;
    private final String separator;
    private final boolean isPlainOutput;

    private double addedCoverageMin;
    private double addedCoverageMax;
    private double addedCoverageSum;
    private int addedCoverageFileCount;
    private int addedCoveragePassedFileCount;

    private double modifiedCoverageMin;
    private double modifiedCoverageMax;
    private double modifiedCoverageSum;
    private int modifiedCoverageFileCount;
    private int modifiedCoveragePassedFileCount;

    AbstractPrinter(Map<String, FileContainer> files, PropertyReader props, CommandLineOptions commandLineOptions,
                    String spacer, String separator, boolean isPlainOutput) {
        this.files = files;
        this.props = props;
        this.commandLineOptions = commandLineOptions;
        this.spacer = spacer;
        this.separator = separator;
        this.isPlainOutput = isPlainOutput;

        this.addedCoverageMin = Double.MAX_VALUE;
        this.addedCoverageMax = Double.MIN_VALUE;

        this.modifiedCoverageMin = Double.MAX_VALUE;
        this.modifiedCoverageMax = Double.MIN_VALUE;
    }

    StringBuilder run() {
        StringBuilder sb = new StringBuilder();
        addHeader(sb);

        int qaCheckPassCount = appendFileContainer(sb);
        addFooter(sb);
        appendSummary(sb, qaCheckPassCount);

        return sb;
    }

    abstract void addHeader(StringBuilder sb);

    abstract void addFooter(StringBuilder sb);

    PropertyReader getProps() {
        return props;
    }

    CommandLineOptions getCommandLineOptions() {
        return commandLineOptions;
    }

    private int appendFileContainer(StringBuilder sb) {
        boolean printFailsOnly = commandLineOptions.printFailsOnly();
        int qaCheckPassCount = 0;
        SortedSet<String> keys = new TreeSet<>(files.keySet());
        for (String key : keys) {
            FileContainer fileContainer = files.get(key);
            if (fileContainer.qaCheck) {
                qaCheckPassCount++;
            }
            if (fileContainer.isForCoverageCalculation) {
                calculateCoverage(fileContainer);
            }
            if (printFailsOnly && fileContainer.qaCheck) {
                continue;
            }

            sb.append("|").append(spacer);
            sb.append(formatSonarQubeLink(props, fileContainer.resourceId, isPlainOutput));
            sb.append(separator).append(formatPullRequestLinks(props, fileContainer.pullRequests, isPlainOutput));
            sb.append(separator).append(fileContainer.author);
            sb.append(separator).append(formatFileName(fileContainer.fileName, isPlainOutput, FILE_NAME_WIDTH));
            sb.append(separator).append(formatGitHubStatus(fileContainer.status, isPlainOutput));
            sb.append(separator).append(formatGitHubChanges(fileContainer.gitHubAdditions, "+", isPlainOutput));
            sb.append(separator).append(formatGitHubChanges(fileContainer.gitHubDeletions, "-", isPlainOutput));
            sb.append(separator).append(formatCoverage(fileContainer.coverage, isPlainOutput));
            sb.append(separator).append(formatCoverage(fileContainer.lineCoverage, isPlainOutput));
            sb.append(separator).append(formatCoverage(fileContainer.branchCoverage, isPlainOutput));
            sb.append(separator).append(formatNullable(fileContainer.comment, " ", isPlainOutput, COMMENT_WIDTH));
            sb.append(separator).append(fileContainer.qaCheck ? " OK " : "FAIL");
            sb.append(spacer).append("|\n");
        }
        return qaCheckPassCount;
    }

    private void calculateCoverage(FileContainer fileContainer) {
        if (!fileContainer.isForCoverageCalculation()) {
            return;
        }

        double fileCoverage = fileContainer.getCoverageForCalculation();
        if (fileContainer.status == GitHubStatus.ADDED) {
            if (fileCoverage < addedCoverageMin) {
                addedCoverageMin = fileCoverage;
            }
            if (fileCoverage > addedCoverageMax) {
                addedCoverageMax = fileCoverage;
            }
            addedCoverageSum += fileCoverage;
            addedCoverageFileCount++;
            if (fileContainer.qaCheck) {
                addedCoveragePassedFileCount++;
            }
        } else {
            if (fileCoverage < modifiedCoverageMin) {
                modifiedCoverageMin = fileCoverage;
            }
            if (fileCoverage > modifiedCoverageMax) {
                modifiedCoverageMax = fileCoverage;
            }
            modifiedCoverageSum += fileCoverage;
            modifiedCoverageFileCount++;
            if (fileContainer.qaCheck) {
                modifiedCoveragePassedFileCount++;
            }
        }
    }

    private void appendSummary(StringBuilder sb, int qaCheckPassCount) {
        int totalCount = files.size();
        double minCodeCoverage = props.getMinCodeCoverage(GitHubStatus.ADDED);
        StringBuilder summary = new StringBuilder();
        summary.append("Summary: ")
                .append(qaCheckPassCount).append("/").append(totalCount)
                .append(" files passed QA Check with minimum code coverage of ").append(minCodeCoverage).append("%");

        double minCodeCoverageModified = props.getMinCodeCoverage(GitHubStatus.MODIFIED);
        if (compare(minCodeCoverage, minCodeCoverageModified) != 0) {
            summary.append(" (").append(minCodeCoverageModified).append("% for modified files)");
        }

        if (addedCoverageFileCount > 0) {
            double addedCoverage = addedCoverageSum / addedCoverageFileCount;
            summary.append(format("%nCoverage on added files: %.1f%% avg, %.1f%% min, %.1f%% max (%d/%d files)",
                    addedCoverage, addedCoverageMin, addedCoverageMax, addedCoveragePassedFileCount, addedCoverageFileCount));
        }
        if (modifiedCoverageFileCount > 0) {
            double modifiedCoverage = modifiedCoverageSum / modifiedCoverageFileCount;
            summary.append(format("%nCoverage on modified files: %.1f%% avg, %.1f%% min, %.1f%% max (%d/%d files)",
                    modifiedCoverage, modifiedCoverageMin, modifiedCoverageMax, modifiedCoveragePassedFileCount,
                    modifiedCoverageFileCount));
        }

        debug(summary.toString());
        sb.append(summary);
    }
}
