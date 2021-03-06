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

package com.hazelcast.qasonar.utils;

import com.hazelcast.utils.PropertyReader;
import joptsimple.BuiltinHelpFormatter;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static java.util.Collections.unmodifiableList;

public class CommandLineOptions {

    private static final int HELP_WIDTH = 160;
    private static final int HELP_INDENTATION = 2;

    private final List<Integer> pullRequests = new ArrayList<>();

    private final OptionParser parser = new OptionParser();

    private final OptionSpec ideaConverterSpec = parser.accepts("ideaConverter",
            "Converts an IDEA coverage report for QA Sonar.");

    private final OptionSpec csvMergeSpec = parser.accepts("csvMerge",
            "Merges multiple IDEA coverage reports to a single one.");

    private final OptionSpec outputMergeSpec = parser.accepts("outputMerge",
            "Merges multiple Confluence coverage reports to a single one.");

    private final OptionSpec listProjectsSpec = parser.accepts("listProjects",
            "Lists projects of specified SonarQube instance.");

    private final OptionSpec<String> listPullRequestsSpec = parser.accepts("listPullRequests",
            "Lists pull requests of a specified GitHub milestone."
                    + " Either specify a milestone title from GitHub or use one of the following types:\n"
                    + " * MERGED: all merged PRs, regardless if they have a milestone set or not\n"
                    + " * ALL: all merged PRs which have a milestone set\n"
                    + " * NONE: all merged PRs which have no milestone set")
            .withRequiredArg().ofType(String.class);

    private final OptionSpec<String> pullRequestsSpec = parser.accepts("pullRequests",
            "Specifies the pull requests whose code coverage should be printed.\n"
                    + "Can either be a single value or a comma separated list.")
            .withRequiredArg().ofType(String.class);

    private final OptionSpec<String> gitHubRepositorySpec = parser.accepts("gitHubRepository",
            "Specifies the GitHub repository to be used.")
            .withRequiredArg().ofType(String.class);

    private final OptionSpec<String> defaultModuleSpec = parser.accepts("defaultModule",
            "Specifies the default module if no module is found in file name.")
            .withRequiredArg().ofType(String.class);

    private final OptionSpec<Boolean> throwExceptionOnMissingModuleSpec = parser.accepts("throwExceptionOnMissingModule",
            "Specifies if an exception should be thrown if no module is found in a file name.")
            .withRequiredArg().ofType(Boolean.class).defaultsTo(false);

    private final OptionSpec<Double> minCodeCoverageSpec = parser.accepts("minCodeCoverage",
            "Specifies the minimum code coverage in percent.")
            .withRequiredArg().ofType(Double.class);

    private final OptionSpec<Double> minCodeCoverageModifiedSpec = parser.accepts("minCodeCoverageModified",
            "Specifies the minimum code coverage for modified files in percent.")
            .withRequiredArg().ofType(Double.class);

    private final OptionSpec<Integer> minThresholdModifiedSpec = parser.accepts("minThresholdModified",
            "Specifies the minimum threshold for modified files in changed lines.")
            .withRequiredArg().ofType(Integer.class);

    private final OptionSpec<String> outputFileSpec = parser.accepts("outputFile",
            "Specifies a file for the output.")
            .withRequiredArg().ofType(String.class);

    private final OptionSpec<String> scriptFileSpec = parser.accepts("scriptFile",
            "Specifies a file for the script.")
            .withRequiredArg().ofType(String.class);

    private final OptionSpec<String> optionalParametersSpec = parser.accepts("optionalParameters",
            "Specifies optional command line parameters which are added to the result output.")
            .withRequiredArg().ofType(String.class);

    private final OptionSpec printFailsOnlySpec = parser.accepts("printFailsOnly",
            "Prints failed files only.");

    private final OptionSpec plainOutputSpec = parser.accepts("plainOutput",
            "Prints plain output without Confluence markup.");

    private final OptionSpec verboseSpec = parser.accepts("verbose",
            "Prints debug output.");

    private final PropertyReader propertyReader;
    private final OptionSet options;
    private final CommandLineAction action;

    public CommandLineOptions(String[] args, PropertyReader propertyReader) {
        this.propertyReader = propertyReader;
        this.options = initOptions(args);
        this.action = initCommandLineAction();
    }

    public CommandLineAction getAction() {
        return action;
    }

    public void printHelp() throws IOException {
        parser.formatHelpWith(new BuiltinHelpFormatter(HELP_WIDTH, HELP_INDENTATION));
        parser.printHelpOn(System.out);
    }

    public String getOptionalParameters() {
        return options.valueOf(optionalParametersSpec);
    }

    public String getScriptFile() {
        return options.valueOf(scriptFileSpec);
    }

    public boolean printFailsOnly() {
        return options.has(printFailsOnlySpec);
    }

    public boolean isPlainOutput() {
        return options.has(plainOutputSpec);
    }

    public boolean isVerbose() {
        return options.has(verboseSpec);
    }

    public List<Integer> getPullRequests() {
        return unmodifiableList(pullRequests);
    }

    private OptionSet initOptions(String[] args) {
        parser.accepts("help", "Show help").forHelp();
        return parser.parse(args);
    }

    private CommandLineAction initCommandLineAction() {
        setGitHubRepository();
        setDefaultModule();
        setThrowExceptionOnMissingModule();
        setMinCodeCoverage();
        setMinCodeCoverageModified();
        setMinThresholdModified();
        setOutputFile();

        return getCommandLineAction();
    }

    private void setGitHubRepository() {
        if (options.has(gitHubRepositorySpec)) {
            propertyReader.setGitHubRepository(options.valueOf(gitHubRepositorySpec));
        }
    }

    private void setDefaultModule() {
        if (options.has(defaultModuleSpec)) {
            propertyReader.setDefaultModule(options.valueOf(defaultModuleSpec));
        }
    }

    private void setThrowExceptionOnMissingModule() {
        propertyReader.setThrowExceptionOnMissingModule(options.valueOf(throwExceptionOnMissingModuleSpec));
    }

    private void setMinCodeCoverage() {
        if (options.has(minCodeCoverageSpec)) {
            Double minCodeCoverage = options.valueOf(minCodeCoverageSpec);
            propertyReader.setMinCodeCoverage(minCodeCoverage, false);
        }
    }

    private void setMinCodeCoverageModified() {
        if (options.has(minCodeCoverageModifiedSpec)) {
            Double minCodeCoverage = options.valueOf(minCodeCoverageModifiedSpec);
            propertyReader.setMinCodeCoverage(minCodeCoverage, true);
        }
    }

    private void setMinThresholdModified() {
        if (options.has(minThresholdModifiedSpec)) {
            Integer minThresholdModified = options.valueOf(minThresholdModifiedSpec);
            propertyReader.setMinThresholdModified(minThresholdModified);
        }
    }

    private void setOutputFile() {
        if (options.has(outputFileSpec)) {
            propertyReader.setOutputFile(options.valueOf(outputFileSpec));
        }
    }

    @SuppressWarnings("checkstyle:npathcomplexity")
    private CommandLineAction getCommandLineAction() {
        if (options.has(ideaConverterSpec)) {
            return CommandLineAction.IDEA_CONVERTER;
        }

        if (options.has(csvMergeSpec)) {
            return CommandLineAction.CSV_MERGE;
        }

        if (options.has(outputMergeSpec)) {
            return CommandLineAction.OUTPUT_MERGE;
        }

        if (options.has(listProjectsSpec)) {
            return CommandLineAction.LIST_PROJECTS;
        }

        if (options.has(listPullRequestsSpec)) {
            addMileStone();
            return CommandLineAction.LIST_PULL_REQUESTS;
        }

        if (options.has(pullRequestsSpec)) {
            addPullRequests();
            return CommandLineAction.PULL_REQUESTS;
        }

        return CommandLineAction.PRINT_HELP;
    }

    private void addMileStone() {
        propertyReader.setMilestone(options.valueOf(listPullRequestsSpec));
    }

    private void addPullRequests() {
        String pullRequestString = options.valueOf(pullRequestsSpec).trim();
        if (!pullRequestString.contains(",")) {
            addPullRequest(pullRequestString);
            return;
        }

        Arrays.asList(pullRequestString.split("\\s*,\\s*")).forEach(this::addPullRequest);
        if (pullRequests.size() == 0) {
            throw new IllegalArgumentException("No pull requests specified");
        }
    }

    private void addPullRequest(String pullRequestString) {
        Integer pullRequest = Integer.valueOf(pullRequestString);
        if (pullRequest < 1) {
            throw new IllegalArgumentException("Invalid pull request: " + pullRequestString);
        }
        pullRequests.add(pullRequest);
    }
}
