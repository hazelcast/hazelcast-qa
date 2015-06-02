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

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import static com.hazelcast.qa.Utils.fillString;
import static com.hazelcast.qa.Utils.formatCoverage;
import static com.hazelcast.qa.Utils.formatFileName;
import static com.hazelcast.qa.Utils.formatGitHubChanges;
import static com.hazelcast.qa.Utils.formatGitHubLink;
import static com.hazelcast.qa.Utils.formatGitHubStatus;
import static com.hazelcast.qa.Utils.formatMinWidth;
import static com.hazelcast.qa.Utils.formatNullable;
import static com.hazelcast.qa.Utils.formatSonarQubeLink;
import static com.hazelcast.qa.Utils.writeToFile;

public class CodeCoveragePrinter {

    private static final int FILE_NAME_WIDTH = 100;
    private static final int COMMENT_WIDTH = 25;

    private final Map<String, TableEntry> tableEntries;
    private final PropertyReader props;

    private String spacer;
    private String separator;

    public CodeCoveragePrinter(Map<String, TableEntry> tableEntries, PropertyReader props) {
        this.tableEntries = tableEntries;
        this.props = props;
    }

    public void plain() throws IOException {
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

        int qaCheckPassCount = appendTableEntries(sb, true);
        sb.append(tableSeparator);
        appendSummary(sb, qaCheckPassCount);

        print(sb);
    }

    public void markUp(List<Integer> pullRequests) throws IOException {
        spacer = "";
        separator = "|";

        StringBuilder sb = new StringBuilder();
        appendCommandLine(sb, pullRequests);
        sb.append("||Sonar||PRs||File||Status||Additions||Deletions||Coverage||Line||Branch||Comment||QA||\n");

        int qaCheckPassCount = appendTableEntries(sb, false);
        appendSummary(sb, qaCheckPassCount);

        print(sb);
    }

    private void appendCommandLine(StringBuilder sb, List<Integer> pullRequests) {
        sb.append("Command line: {{qa-sonar --pullRequests ");
        String separator = "";
        for (Integer pullRequest : pullRequests) {
            sb.append(separator).append(pullRequest);
            separator = ",";
        }
        if (props.isGitHubRepositoryOverwritten()) {
            sb.append(" --gitHubRepository ").append(props.getGitHubRepository());
        }
        sb.append("}}\n");
    }

    private int appendTableEntries(StringBuilder sb, boolean plainOutput) {
        int qaCheckPassCount = 0;
        SortedSet<String> keys = new TreeSet<String>(tableEntries.keySet());
        for (String key : keys) {
            TableEntry tableEntry = tableEntries.get(key);
            String resourceId = tableEntry.resourceId;
            sb.append("|").append(spacer);
            sb.append(resourceId == null ? "?????" : formatSonarQubeLink(props, resourceId, plainOutput));
            sb.append(separator).append(formatGitHubLink(props, tableEntry.pullRequest, plainOutput));
            sb.append(separator).append(formatFileName(tableEntry.fileName, plainOutput, FILE_NAME_WIDTH));
            sb.append(separator).append(formatGitHubStatus(tableEntry.status, plainOutput));
            sb.append(separator).append(formatGitHubChanges(tableEntry.gitHubAdditions, "+", plainOutput));
            sb.append(separator).append(formatGitHubChanges(tableEntry.gitHubDeletions, "-", plainOutput));
            sb.append(separator).append(formatCoverage(tableEntry.coverage, plainOutput));
            sb.append(separator).append(formatCoverage(tableEntry.lineCoverage, plainOutput));
            sb.append(separator).append(formatCoverage(tableEntry.branchCoverage, plainOutput));
            sb.append(separator).append(formatNullable(tableEntry.comment, " ", plainOutput, COMMENT_WIDTH));
            sb.append(separator).append(tableEntry.qaCheck ? " OK " : "FAIL");
            sb.append(spacer).append("|\n");
            if (tableEntry.qaCheck) {
                qaCheckPassCount++;
            }
        }
        return qaCheckPassCount;
    }

    private void appendSummary(StringBuilder sb, int qaCheckPassCount) {
        int totalCount = tableEntries.size();
        double minCodeCoverage = props.getMinCodeCoverage(GitHubStatus.ADDED);
        sb.append("Summary: ")
                .append(qaCheckPassCount).append("/").append(totalCount)
                .append(" files passed QA Check with minimum code coverage of ").append(minCodeCoverage).append("%");

        double minCodeCoverageModified = props.getMinCodeCoverage(GitHubStatus.MODIFIED);
        if (Math.abs(minCodeCoverage - minCodeCoverageModified) > 0.01) {
            sb.append(" (").append(minCodeCoverageModified).append("% for modified files)");
        }
    }

    private void print(StringBuilder sb) throws IOException {
        if (props.getOutputFile() != null) {
            writeToFile(props.getOutputFile(), sb);
        } else {
            System.out.println(sb.toString());
        }
    }
}
