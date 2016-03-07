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

import static com.hazelcast.qasonar.utils.Utils.appendCommandLine;

class MarkupPrinter extends AbstractPrinter {

    MarkupPrinter(Map<String, FileContainer> files, PropertyReader props, CommandLineOptions commandLineOptions) {
        super(files, props, commandLineOptions, "", "|", false);
    }

    @Override
    void addHeader(StringBuilder sb) {
        appendCommandLine(getProps(), sb, getCommandLineOptions().getPullRequests(), false);
        sb.append("||Sonar||PRs||File||Status||Add||Del||Coverage||Line||Branch||Comment||QA||\n");
    }

    @Override
    void addFooter(StringBuilder sb) {
    }
}
