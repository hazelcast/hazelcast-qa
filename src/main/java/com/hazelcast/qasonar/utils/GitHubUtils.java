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
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestFileDetail;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHUser;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import static com.hazelcast.qasonar.utils.Utils.sleepMillis;
import static org.apache.commons.io.IOUtils.copy;

public final class GitHubUtils {

    private static final int EXCEPTION_DELAY_MILLIS = 100;

    private static final int GITHUB_FILE_DOWNLOAD_RETRIES = 10;
    private static final int GITHUB_FILE_DOWNLOAD_RETRY_DELAY_MILLIS = 200;

    private GitHubUtils() {
    }

    public static String getAuthor(GHRepository repo, int gitPullRequest) {
        while (true) {
            try {
                GHUser user = repo.getIssue(gitPullRequest).getUser();
                String author = user.getName();
                if (author != null) {
                    return author;
                }
                return user.getLogin();
            } catch (Throwable ignored) {
                sleepMillis(EXCEPTION_DELAY_MILLIS);
            }
        }
    }

    public static GHPullRequest getPullRequest(GHRepository repo, int gitPullRequest) {
        while (true) {
            try {
                return repo.getPullRequest(gitPullRequest);
            } catch (Throwable ignored) {
                sleepMillis(EXCEPTION_DELAY_MILLIS);
            }
        }
    }

    public static boolean isMerged(GHPullRequest pullRequest) {
        while (true) {
            try {
                return pullRequest.isMerged();
            } catch (Throwable ignored) {
                sleepMillis(EXCEPTION_DELAY_MILLIS);
            }
        }
    }

    public static boolean isClosed(GHPullRequest pullRequest) {
        while (true) {
            try {
                return pullRequest.getState().equals(GHIssueState.CLOSED);
            } catch (Throwable ignored) {
                sleepMillis(EXCEPTION_DELAY_MILLIS);
            }
        }
    }

    public static List<GHPullRequestFileDetail> getPullRequestFiles(GHPullRequest pullRequest) {
        while (true) {
            try {
                List<GHPullRequestFileDetail> files = new ArrayList<>();
                for (GHPullRequestFileDetail pullRequestFile : pullRequest.listFiles()) {
                    files.add(pullRequestFile);
                }
                return files;
            } catch (Throwable ignored) {
                sleepMillis(EXCEPTION_DELAY_MILLIS);
            }
        }
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
            sleepMillis(GITHUB_FILE_DOWNLOAD_RETRY_DELAY_MILLIS * (i + 1));
        }
        throw exception;
    }
}
