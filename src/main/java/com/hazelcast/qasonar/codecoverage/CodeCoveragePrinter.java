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
import com.hazelcast.qasonar.utils.CommandLineOptions;

import java.io.IOException;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import static com.hazelcast.qasonar.utils.Utils.appendCommandLine;
import static com.hazelcast.qasonar.utils.Utils.debug;
import static com.hazelcast.qasonar.utils.Utils.fillString;
import static com.hazelcast.qasonar.utils.Utils.formatCoverage;
import static com.hazelcast.qasonar.utils.Utils.formatFileName;
import static com.hazelcast.qasonar.utils.Utils.formatGitHubChanges;
import static com.hazelcast.qasonar.utils.Utils.formatPullRequestLinks;
import static com.hazelcast.qasonar.utils.Utils.formatGitHubStatus;
import static com.hazelcast.qasonar.utils.Utils.formatMinWidth;
import static com.hazelcast.qasonar.utils.Utils.formatNullable;
import static com.hazelcast.qasonar.utils.Utils.formatSonarQubeLink;
import static com.hazelcast.qasonar.utils.Utils.writeToFile;

public class CodeCoveragePrinter {

    private static final int FILE_NAME_WIDTH = 100;
    private static final int COMMENT_WIDTH = 25;

    private final Map<String, FileContainer> files;
    private final PropertyReader props;
    private final CommandLineOptions commandLineOptions;

    private String spacer;
    private String separator;

    public CodeCoveragePrinter(Map<String, FileContainer> files, PropertyReader props, CommandLineOptions cliOptions) {
        this.files = files;
        this.props = props;
        this.commandLineOptions = cliOptions;
    }

    public void run() throws IOException {
        if (commandLineOptions.isPlainOutput()) {
            plain();
        } else {
            markUp();
        }
    }

    private void plain() throws IOException {
        spacer = " ";
        separator = " | ";

        StringBuilder sb = new StringBuilder();
        sb.append("|-").append(fillString(5, '-'))
                .append("-|-").append(fillString(4, '-'))
                .append("-|-").append(fillString(FILE_NAME_WIDTH, '-'))
                .append("-|-").append(fillString(8, '-'))
                .append("-|-").append(fillString(4, '-'))
                .append("-|-").append(fillString(4, '-'))
                .append("-|-").append(fillString(6, '-'))
                .append("-|-").append(fillString(6, '-'))
                .append("-|-").append(fillString(6, '-'))
                .append("-|-").append(fillString(COMMENT_WIDTH, '-'))
                .append("-|-").append(fillString(4, '-'))
                .append("-|\n");

        String tableSeparator = sb.toString();

        sb.append("| Sonar | PRs  | ")
                .append(formatMinWidth("File", FILE_NAME_WIDTH))
                .append(" | Status   | Add  | Del  | Cover  | Line   | Branch | ")
                .append(formatMinWidth("Comment", COMMENT_WIDTH))
                .append(" | QA   |\n")
                .append(tableSeparator);

        int qaCheckPassCount = appendFileContainer(sb, true);
        sb.append(tableSeparator);
        appendSummary(sb, qaCheckPassCount);

        print(sb);
    }

    private void markUp() throws IOException {
        spacer = "";
        separator = "|";

        StringBuilder sb = new StringBuilder();
        appendCommandLine(props, sb, commandLineOptions.getPullRequests(), false);
        sb.append("||Sonar||PRs||File||Status||Add||Del||Coverage||Line||Branch||Comment||QA||\n");

        int qaCheckPassCount = appendFileContainer(sb, false);
        appendSummary(sb, qaCheckPassCount);

        print(sb);
    }

    private int appendFileContainer(StringBuilder sb, boolean plainOutput) {
        boolean printFailsOnly = commandLineOptions.printFailsOnly();
        int qaCheckPassCount = 0;
        SortedSet<String> keys = new TreeSet<String>(files.keySet());
        for (String key : keys) {
            FileContainer fileContainer = files.get(key);
            if (fileContainer.qaCheck) {
                qaCheckPassCount++;
            }
            if (printFailsOnly && fileContainer.qaCheck) {
                continue;
            }

            sb.append("|").append(spacer);
            sb.append(formatSonarQubeLink(props, fileContainer.resourceId, plainOutput));
            sb.append(separator).append(formatPullRequestLinks(props, fileContainer.pullRequests, plainOutput));
            sb.append(separator).append(formatFileName(fileContainer.fileName, plainOutput, FILE_NAME_WIDTH));
            sb.append(separator).append(formatGitHubStatus(fileContainer.status, plainOutput));
            sb.append(separator).append(formatGitHubChanges(fileContainer.gitHubAdditions, "+", plainOutput));
            sb.append(separator).append(formatGitHubChanges(fileContainer.gitHubDeletions, "-", plainOutput));
            sb.append(separator).append(formatCoverage(fileContainer.coverage, plainOutput));
            sb.append(separator).append(formatCoverage(fileContainer.lineCoverage, plainOutput));
            sb.append(separator).append(formatCoverage(fileContainer.branchCoverage, plainOutput));
            sb.append(separator).append(formatNullable(fileContainer.comment, " ", plainOutput, COMMENT_WIDTH));
            sb.append(separator).append(fileContainer.qaCheck ? " OK " : "FAIL");
            sb.append(spacer).append("|\n");
        }
        return qaCheckPassCount;
    }

    private void appendSummary(StringBuilder sb, int qaCheckPassCount) {
        int totalCount = files.size();
        double minCodeCoverage = props.getMinCodeCoverage(GitHubStatus.ADDED);
        StringBuilder summary = new StringBuilder();
        summary.append("Summary: ")
                .append(qaCheckPassCount).append("/").append(totalCount)
                .append(" files passed QA Check with minimum code coverage of ").append(minCodeCoverage).append("%");

        double minCodeCoverageModified = props.getMinCodeCoverage(GitHubStatus.MODIFIED);
        if (Math.abs(minCodeCoverage - minCodeCoverageModified) > 0.01) {
            summary.append(" (").append(minCodeCoverageModified).append("% for modified files)");
        }

        debug(summary.toString());
        sb.append(summary);
    }

    private void print(StringBuilder sb) throws IOException {
        if (props.getOutputFile() != null) {
            writeToFile(props.getOutputFile(), sb);
        } else {
            System.out.println(sb.toString());
        }
    }
}
