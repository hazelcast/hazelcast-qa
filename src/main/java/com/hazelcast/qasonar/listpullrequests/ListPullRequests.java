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

package com.hazelcast.qasonar.listpullrequests;

import com.hazelcast.qasonar.utils.CommandLineOptions;
import com.hazelcast.qasonar.utils.PropertyReader;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHMilestone;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.stream.Collectors;

import static com.hazelcast.qasonar.utils.DebugUtils.debug;
import static com.hazelcast.qasonar.utils.DebugUtils.isDebug;
import static java.lang.String.format;
import static org.apache.commons.io.FileUtils.readFileToString;
import static org.apache.commons.io.FileUtils.writeStringToFile;
import static org.kohsuke.github.GHIssueState.CLOSED;
import static org.kohsuke.github.GHIssueState.OPEN;

public class ListPullRequests {

    private static final String NO_MILESTONE_TITLE = "none";
    private static final int NO_MILESTONE_ID = -1;

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
        System.out.println("Connecting to GitHub...");
        GitHub github = GitHub.connect();
        GHRepository repo = github.getRepository(gitHubRepository);

        System.out.println("Searching milestone...");
        GHMilestone milestone = getMilestone(milestoneTitle, repo);

        System.out.println("Searching merged pull requests for milestone...");
        List<Integer> pullRequests = getPullRequests(repo, milestone, calendar);

        System.out.println("Sorting result...");
        pullRequests.sort(Integer::compareTo);
        String pullRequestString = pullRequests.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));

        String command = (pullRequests.size() > 0)
                ? format("qa-sonar%s%s%s --pullRequests %s --outputFile %s%n",
                optionalParameters, outputGithubRepository, outputDefaultModule, pullRequestString, outputFile)
                : format("No pull requests have been found for milestone %s in this repository!", milestone);

        System.out.println("Done!");
        System.out.println();
        System.out.println(command);

        if (scriptFile != null && pullRequests.size() > 0) {
            File file = new File(scriptFile);
            String script = readFileToString(file);
            writeStringToFile(file, format("%s%s%n", script, command));
        }
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

    private static GHMilestone getMilestone(String milestoneTitle, GHRepository repo) {
        if (milestoneTitle.equals(NO_MILESTONE_TITLE)) {
            return null;
        }
        for (GHMilestone milestoneEntry : repo.listMilestones(OPEN)) {
            if (milestoneTitle.equals(milestoneEntry.getTitle())) {
                return milestoneEntry;
            }
        }
        for (GHMilestone milestoneEntry : repo.listMilestones(CLOSED)) {
            if (milestoneTitle.equals(milestoneEntry.getTitle())) {
                return milestoneEntry;
            }
        }
        throw new IllegalStateException(format("Could not find milestone %s", milestoneTitle));
    }

    private static List<Integer> getPullRequests(GHRepository repo, GHMilestone milestone, Calendar calendar) throws IOException {
        List<Integer> pullRequests = new ArrayList<>();
        int milestoneId = (milestone == null) ? NO_MILESTONE_ID : milestone.getId();
        for (GHIssue issue : repo.listIssues(CLOSED)) {
            if (!isMergedPullRequestOfMilestone(issue, milestoneId, repo)) {
                continue;
            }
            if (isDebug()) {
                printDebugOutput(calendar, issue);
            } else {
                System.out.print('.');
            }
            pullRequests.add(issue.getNumber());
        }
        if (!isDebug()) {
            System.out.println();
        }
        return pullRequests;
    }

    private static boolean isMergedPullRequestOfMilestone(GHIssue issue, int milestoneId, GHRepository repo) throws IOException {
        if (!issue.isPullRequest()) {
            return false;
        }
        GHMilestone issueMilestone = issue.getMilestone();
        if (milestoneId > NO_MILESTONE_ID) {
            if (issueMilestone == null || issueMilestone.getId() != milestoneId) {
                return false;
            }
        } else if (issueMilestone != null) {
            return false;
        }
        GHPullRequest pullRequest = repo.getPullRequest(issue.getNumber());
        return pullRequest.isMerged();
    }

    private static void printDebugOutput(Calendar calendar, GHIssue issue) {
        calendar.setTime(issue.getClosedAt());
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH) + 1;
        int day = calendar.get(Calendar.DAY_OF_MONTH);
        debug(format("[%d-%02d-%02d] #%04d %s", year, month, day, issue.getNumber(), issue.getTitle()));
    }
}
