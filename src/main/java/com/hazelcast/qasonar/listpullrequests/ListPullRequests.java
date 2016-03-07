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

import com.hazelcast.qasonar.utils.PropertyReader;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHMilestone;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.stream.Collectors;

import static com.hazelcast.qasonar.utils.DebugUtils.debug;
import static com.hazelcast.qasonar.utils.DebugUtils.isDebug;
import static java.lang.String.format;

public class ListPullRequests {

    private final Calendar calendar = Calendar.getInstance();

    private final PropertyReader propertyReader;
    private final String milestoneTitle;

    public ListPullRequests(PropertyReader propertyReader) {
        this.propertyReader = propertyReader;
        this.milestoneTitle = propertyReader.getMilestone();
    }

    public void run() throws IOException {
        System.out.println("Connecting to GitHub...");
        GitHub github = GitHub.connect();
        GHRepository repo = github.getRepository(propertyReader.getGitHubRepository());

        System.out.println("Searching milestone...");
        GHMilestone milestone = getMilestone(milestoneTitle, repo);

        System.out.println("Searching merged pull requests for milestone...");
        List<Integer> pullRequests = getPullRequests(repo, milestone);

        System.out.println("Sorting result...");
        pullRequests.sort(Integer::compareTo);
        String pullRequestString = pullRequests.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));

        System.out.println("Done!");
        System.out.println();
        System.out.println(format("qa-sonar --verbose --printFailsOnly --pullRequests %s --outputFile %s-failures.txt",
                pullRequestString, milestoneTitle));
    }

    private GHMilestone getMilestone(String milestoneTitle, GHRepository repo) {
        for (GHMilestone milestoneEntry : repo.listMilestones(GHIssueState.OPEN)) {
            if (milestoneTitle.equals(milestoneEntry.getTitle())) {
                return milestoneEntry;
            }
        }
        for (GHMilestone milestoneEntry : repo.listMilestones(GHIssueState.CLOSED)) {
            if (milestoneTitle.equals(milestoneEntry.getTitle())) {
                return milestoneEntry;
            }
        }
        throw new IllegalStateException(format("Could not find milestone %s", milestoneTitle));
    }

    private List<Integer> getPullRequests(GHRepository repo, GHMilestone milestone) throws IOException {
        List<Integer> pullRequests = new ArrayList<>();
        int milestoneId = milestone.getId();
        for (GHIssue issue : repo.listIssues(GHIssueState.CLOSED)) {
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
        if (issueMilestone == null || issueMilestone.getId() != milestoneId) {
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
