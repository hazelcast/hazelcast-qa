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

import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestFileDetail;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHUser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.hazelcast.qasonar.utils.Utils.sleepMillis;

public final class GitHubUtils {

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
            } catch (IOException ignored) {
                sleepMillis(100);
            }
        }
    }

    public static GHPullRequest getPullRequest(GHRepository repo, int gitPullRequest) {
        while (true) {
            try {
                return repo.getPullRequest(gitPullRequest);
            } catch (IOException ignored) {
                sleepMillis(100);
            }
        }
    }

    public static boolean isMerged(GHPullRequest pullRequest) {
        while (true) {
            try {
                return pullRequest.isMerged();
            } catch (IOException ignored) {
                sleepMillis(100);
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
            } catch (Exception ignored) {
                sleepMillis(100);
            }
        }
    }
}
