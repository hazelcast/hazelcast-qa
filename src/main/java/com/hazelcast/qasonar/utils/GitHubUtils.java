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

package com.hazelcast.qasonar.utils;

import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHMilestone;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestFileDetail;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.Callable;

import static com.hazelcast.qasonar.utils.DebugUtils.debug;
import static com.hazelcast.qasonar.utils.DebugUtils.isDebug;
import static com.hazelcast.qasonar.utils.Utils.sleepMillis;
import static org.apache.commons.io.IOUtils.copy;
import static org.kohsuke.github.GHIssueState.CLOSED;
import static org.kohsuke.github.GHIssueState.OPEN;

public final class GitHubUtils {

    static final GHMilestone MERGED_MILESTONE = new GHMilestone();
    static final GHMilestone ALL_MILESTONE = new GHMilestone();
    static final GHMilestone NO_MILESTONE = new GHMilestone();

    private static final int GITHUB_FILE_DOWNLOAD_RETRIES = 10;
    private static final int GITHUB_EXCEPTION_DELAY_MILLIS = 200;

    private static final String MERGED_MILESTONE_TITLE = "MERGED";
    private static final String ALL_MILESTONE_TITLE = "ALL";
    private static final String NO_MILESTONE_TITLE = "NONE";

    private GitHubUtils() {
    }

    public static GHRepository getGitHubRepository(PropertyReader propertyReader) {
        return (GHRepository) execute(() -> {
            GitHub github = GitHub.connect();
            return github.getRepository(propertyReader.getGitHubRepository());
        });
    }

    public static String getFileContentsFromGitHub(GHRepository repo, String fileName) throws IOException {
        IOException exception = null;
        for (int i = 0; i < GITHUB_FILE_DOWNLOAD_RETRIES; i++) {
            try {
                GHContent fileContent = repo.getFileContent(fileName);

                StringWriter writer = new StringWriter();
                copy(fileContent.read(), writer);

                return writer.toString();
            } catch (IOException e) {
                exception = e;
            }
            sleepMillis(GITHUB_EXCEPTION_DELAY_MILLIS * (i + 1));
        }
        throw exception;
    }

    public static String getAuthor(GHRepository repo, int gitPullRequest) {
        return (String) execute(() -> {
            GHUser user = repo.getIssue(gitPullRequest).getUser();
            String author = user.getName();
            if (author != null) {
                return author;
            }
            return user.getLogin();
        });
    }

    public static GHMilestone getMilestone(String milestoneTitle, GHRepository repo) {
        return (GHMilestone) execute(() -> {
            switch (milestoneTitle) {
                case MERGED_MILESTONE_TITLE:
                    return MERGED_MILESTONE;
                case ALL_MILESTONE_TITLE:
                    return ALL_MILESTONE;
                case NO_MILESTONE_TITLE:
                    return NO_MILESTONE;
                default:
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
                    return null;
            }
        });
    }

    public static GHPullRequest getPullRequest(GHRepository repo, int gitPullRequest) {
        return (GHPullRequest) execute(() -> repo.getPullRequest(gitPullRequest));
    }

    @SuppressWarnings("unchecked")
    public static List<Integer> getPullRequests(GHRepository repo, GHMilestone milestone, Calendar calendar) {
        return (List<Integer>) execute(() -> {
            List<Integer> pullRequests = new ArrayList<>();

            for (GHPullRequest pullRequest : repo.getPullRequests(CLOSED)) {
                if (!matchesMilestone(pullRequest, milestone)) {
                    continue;
                }
                if (isDebug()) {
                    printDebugOutput(calendar, pullRequest);
                } else {
                    System.out.print('.');
                }
                pullRequests.add(pullRequest.getNumber());
            }
            if (!isDebug()) {
                System.out.println();
            }
            return pullRequests;
        });
    }

    public static boolean isMerged(GHPullRequest pullRequest) {
        return (boolean) execute(pullRequest::isMerged);
    }

    public static boolean isClosed(GHPullRequest pullRequest) {
        return (boolean) execute(() -> pullRequest.getState().equals(GHIssueState.CLOSED));
    }

    @SuppressWarnings("unchecked")
    public static List<GHPullRequestFileDetail> getPullRequestFiles(GHPullRequest pullRequest) {
        return (List<GHPullRequestFileDetail>) execute(() -> {
            List<GHPullRequestFileDetail> files = new ArrayList<>();
            for (GHPullRequestFileDetail pullRequestFile : pullRequest.listFiles()) {
                files.add(pullRequestFile);
            }
            return files;
        });
    }

    static boolean matchesMilestone(GHPullRequest pullRequest, GHMilestone milestone) {
        if (milestone == MERGED_MILESTONE) {
            // we don't care if the PR has a milestone at all
            return isMerged(pullRequest);
        }
        GHMilestone prMilestone = pullRequest.getMilestone();
        if (milestone == ALL_MILESTONE) {
            // we don't care which milestone the PR has
            return (prMilestone != null && isMerged(pullRequest));
        }
        if (milestone == NO_MILESTONE) {
            // the PR should not have a milestone
            return (prMilestone == null && isMerged(pullRequest));
        }
        // the PR has to have the wanted milestone
        return (prMilestone != null && prMilestone.getId() == milestone.getId() && isMerged(pullRequest));
    }

    private static void printDebugOutput(Calendar calendar, GHPullRequest pullRequest) {
        calendar.setTime(pullRequest.getClosedAt());
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH) + 1;
        int day = calendar.get(Calendar.DAY_OF_MONTH);
        debug("[%d-%02d-%02d] #%04d %s", year, month, day, pullRequest.getNumber(), pullRequest.getTitle());
    }

    private static Object execute(Callable callable) {
        while (true) {
            try {
                return callable.call();
            } catch (Throwable ignored) {
                sleepMillis(GITHUB_EXCEPTION_DELAY_MILLIS);
            }
        }
    }
}
