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

package com.hazelcast.qasonar.listpullrequests;

import com.hazelcast.qasonar.utils.CommandLineOptions;
import com.hazelcast.qasonar.utils.PropertyReader;
import org.kohsuke.github.GHMilestone;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

import java.io.File;
import java.io.IOException;
import java.util.Calendar;
import java.util.List;
import java.util.stream.Collectors;

import static com.hazelcast.qasonar.utils.DebugUtils.print;
import static com.hazelcast.qasonar.utils.DebugUtils.printGreen;
import static com.hazelcast.qasonar.utils.DebugUtils.printRed;
import static com.hazelcast.qasonar.utils.GitHubUtils.getMilestone;
import static com.hazelcast.qasonar.utils.GitHubUtils.getPullRequests;
import static com.hazelcast.qasonar.utils.TimeTracker.printTimeTracks;
import static java.lang.String.format;
import static org.apache.commons.io.FileUtils.readFileToString;
import static org.apache.commons.io.FileUtils.writeStringToFile;

public class ListPullRequests {

    private final Calendar calendar = Calendar.getInstance();

    private final String gitHubRepository;
    private final String milestoneTitle;
    private final String optionalParameters;
    private final String outputGithubRepository;
    private final String outputDefaultModule;
    private final String outputFile;
    private final String scriptFile;

    public ListPullRequests(PropertyReader propertyReader, CommandLineOptions commandLineOptions) {
        this.gitHubRepository = propertyReader.getGitHubRepository();
        this.milestoneTitle = propertyReader.getMilestone();
        this.optionalParameters = getOptionalParameters(commandLineOptions.getOptionalParameters());
        this.outputGithubRepository = getGithubRepository(propertyReader.isGitHubRepositoryOverwritten(), gitHubRepository);
        this.outputDefaultModule = getDefaultModule(propertyReader.getDefaultModule());
        this.outputFile = getOutputFile(propertyReader.getOutputFile(), milestoneTitle);
        this.scriptFile = getScriptFile(commandLineOptions.getScriptFile());
    }

    public void run() throws IOException {
        print("Connecting to GitHub...");
        GitHub github = GitHub.connect();
        GHRepository repo = github.getRepository(gitHubRepository);

        print("Searching milestone \"%s\"...", milestoneTitle);
        GHMilestone milestone = getMilestone(milestoneTitle, repo);
        if (milestone == null) {
            printRed("Could not find milestone \"%s\"!", milestoneTitle);
            return;
        }

        print("Searching merged PRs for milestone \"%s\"...", milestoneTitle);
        List<Integer> pullRequests = getPullRequests(repo, milestone, calendar);

        print("Sorting %d PRs...", pullRequests.size());
        pullRequests.sort(Integer::compareTo);
        String pullRequestString = pullRequests.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));

        String command = (pullRequests.size() > 0)
                ? format("qa-sonar%s%s%s --pullRequests %s --outputFile %s%n",
                optionalParameters, outputGithubRepository, outputDefaultModule, pullRequestString, outputFile)
                : format("No PRs have been found for milestone %s in this repository!", milestone);

        printGreen("Done!\n");
        print(command);

        if (scriptFile != null && pullRequests.size() > 0) {
            File file = new File(scriptFile);
            String script = readFileToString(file);
            writeStringToFile(file, format("%s%s%n", script, command));
        }

        printTimeTracks();
    }

    private static String getOptionalParameters(String optionalParameters) {
        if (optionalParameters == null || optionalParameters.isEmpty()) {
            return "";
        }
        return " " + optionalParameters;
    }

    private static String getGithubRepository(boolean isGitHubRepositoryOverwritten, String gitHubRepository) {
        if (!isGitHubRepositoryOverwritten) {
            return "";
        }
        return " --gitHubRepository " + gitHubRepository;
    }

    private static String getDefaultModule(String defaultModule) {
        if (defaultModule == null || defaultModule.isEmpty()) {
            return "";
        }
        return " --defaultModule " + defaultModule;
    }

    private static String getOutputFile(String outputFile, String milestoneTitle) {
        if (outputFile == null || outputFile.isEmpty()) {
            return milestoneTitle + "-failures.txt";
        }
        return outputFile;
    }

    private static String getScriptFile(String scriptFile) {
        if (scriptFile == null || scriptFile.isEmpty()) {
            return null;
        }
        return scriptFile;
    }
}
