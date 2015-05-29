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
import joptsimple.BuiltinHelpFormatter;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CommandLineOptions {

    private static final int HELP_WIDTH = 160;
    private static final int HELP_INDENTATION = 2;

    private final List<Integer> pullRequests = new ArrayList<Integer>();

    private final OptionParser parser = new OptionParser();

    private final OptionSpec<Double> minCodeCoverageSpec = parser.accepts("minCodeCoverageSpec",
            "Specifies the minimum code coverage in percent.")
            .withRequiredArg().ofType(Double.class);

    private final OptionSpec<String> pullRequestsSpec = parser.accepts("pullRequests",
            "Specifies the pull requests whose code coverage should be printed.\n"
                    + "Can either be a single value or a comma separated list.")
            .withRequiredArg().ofType(String.class);

    private final OptionSpec helpSpec = parser.accepts("help", "Show help").forHelp();

    private final PropertyReader propertyReader;
    private final OptionSet options;

    public CommandLineOptions(String[] args, PropertyReader propertyReader) {
        this.propertyReader = propertyReader;
        this.options = parser.parse(args);

        parser.formatHelpWith(new BuiltinHelpFormatter(HELP_WIDTH, HELP_INDENTATION));
    }

    public List<Integer> getPullRequests() {
        return Collections.unmodifiableList(pullRequests);
    }

    public boolean init() throws IOException {
        if (options.has(helpSpec) || !options.has(pullRequestsSpec)) {
            parser.printHelpOn(System.out);
            return false;
        }

        if (options.has(minCodeCoverageSpec)) {
            Double minCodeCoverage = options.valueOf(minCodeCoverageSpec);
            propertyReader.setMinCodeCoverage(minCodeCoverage);
        }

        String pullRequestString = options.valueOf(pullRequestsSpec).trim();
        if (!pullRequestString.contains(",")) {
            addPullRequest(pullRequestString);
            return true;
        }

        for (String pullRequestArrayString : Arrays.asList(pullRequestString.split("\\s*,\\s*"))) {
            addPullRequest(pullRequestArrayString);
        }
        if (pullRequests.size() == 0) {
            throw new IllegalArgumentException("No pull requests specified");
        }
        return true;
    }

    private void addPullRequest(String pullRequestString) {
        Integer pullRequest = Integer.valueOf(pullRequestString);
        if (pullRequest < 1) {
            throw new IllegalArgumentException("Invalid pull request: " + pullRequestString);
        }
        pullRequests.add(pullRequest);
    }
}
