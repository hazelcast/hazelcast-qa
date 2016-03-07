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
import com.hazelcast.qasonar.utils.PropertyReader;

import java.util.Map;

import static com.hazelcast.qasonar.utils.Utils.fillString;
import static com.hazelcast.qasonar.utils.Utils.formatMinWidth;

class PlainPrinter extends AbstractPrinter {

    private final String tableSeparator;

    PlainPrinter(Map<String, FileContainer> files, PropertyReader props, CommandLineOptions commandLineOptions) {
        super(files, props, commandLineOptions, " ", " | ", true);
        tableSeparator = createTableSeparator();
    }

    @Override
    void addHeader(StringBuilder sb) {
        sb.append("| Sonar | PRs  | ")
                .append(formatMinWidth("File", FILE_NAME_WIDTH))
                .append(" | Status   | Add  | Del  | Cover  | Line   | Branch | ")
                .append(formatMinWidth("Comment", COMMENT_WIDTH))
                .append(" | QA   |\n")
                .append(tableSeparator);
    }

    @Override
    void addFooter(StringBuilder sb) {
        sb.append(tableSeparator);
    }

    private String createTableSeparator() {
        return "|-" + fillString(5, '-')
                + "-|-" + fillString(4, '-')
                + "-|-" + fillString(FILE_NAME_WIDTH, '-')
                + "-|-" + fillString(8, '-')
                + "-|-" + fillString(4, '-')
                + "-|-" + fillString(4, '-')
                + "-|-" + fillString(6, '-')
                + "-|-" + fillString(6, '-')
                + "-|-" + fillString(6, '-')
                + "-|-" + fillString(COMMENT_WIDTH, '-')
                + "-|-" + fillString(4, '-')
                + "-|\n";
    }
}
