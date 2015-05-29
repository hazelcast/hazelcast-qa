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

import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import static com.hazelcast.qa.Utils.formatCoverage;
import static com.hazelcast.qa.Utils.formatNullable;

public class CodeCoveragePrinter {

    private final Map<String, TableEntry> tableEntries;
    private final PropertyReader props;

    public CodeCoveragePrinter(Map<String, TableEntry> tableEntries, PropertyReader props) {
        this.tableEntries = tableEntries;
        this.props = props;
    }

    public void run() {
        StringBuilder sb = new StringBuilder();
        sb.append("||Resource||PRs||File||Coverage||Line||Branch||Comment||QA Check||\n");

        int qaCheckPassCount = 0;
        SortedSet<String> keys = new TreeSet<String>(tableEntries.keySet());
        for (String key : keys) {
            TableEntry tableEntry = tableEntries.get(key);
            sb.append("|").append(formatNullable(tableEntry.resourceId, "?????"));
            sb.append("|").append(tableEntry.pullRequest);
            sb.append("|").append(tableEntry.fileName);
            sb.append("|").append(formatCoverage(tableEntry.coverage));
            sb.append("|").append(formatCoverage(tableEntry.lineCoverage));
            sb.append("|").append(formatCoverage(tableEntry.branchCoverage));
            sb.append("|").append(formatNullable(tableEntry.comment, " "));
            sb.append("|").append(formatNullable(tableEntry.qaCheck, "FAIL"));
            sb.append("|\n");
            if ("OK".equals(tableEntry.qaCheck)) {
                qaCheckPassCount++;
            }
        }

        int totalCount = tableEntries.size();
        sb.append("Summary: ")
                .append(qaCheckPassCount).append("/").append(totalCount)
                .append(" files passed QA Check with minimum code coverage of ")
                .append(props.getMinCodeCoverage()).append("%");

        System.out.println(sb.toString());
    }
}
