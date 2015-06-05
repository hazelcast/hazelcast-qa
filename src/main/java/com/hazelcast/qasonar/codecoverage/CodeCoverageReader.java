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

package com.hazelcast.qasonar.codecoverage;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hazelcast.qasonar.utils.GitHubStatus;
import com.hazelcast.qasonar.utils.PropertyReader;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestFileDetail;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.PagedIterable;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.hazelcast.qasonar.utils.Utils.debug;
import static com.hazelcast.qasonar.utils.Utils.findModuleName;
import static com.hazelcast.qasonar.utils.Utils.getJsonElementsFromQuery;
import static java.lang.String.format;

public class CodeCoverageReader {

    private static final String METRICS_LIST = "coverage,line_coverage,branch_coverage";

    private final Map<String, Map<String, String>> resources = new HashMap<String, Map<String, String>>();
    private final Map<String, FileContainer> files = new HashMap<String, FileContainer>();

    private final PropertyReader props;
    private final GHRepository repo;

    public CodeCoverageReader(PropertyReader propertyReader, GHRepository repo) throws IOException {
        this.props = propertyReader;
        this.repo = repo;
    }

    public Map<String, FileContainer> getFiles() {
        return Collections.unmodifiableMap(files);
    }

    public void run(List<Integer> pullRequests) throws IOException {
        populateResourcesMap();

        for (Integer pullRequest : pullRequests) {
            debug(format("Adding pull request %d...", pullRequest));
            addPullRequest(pullRequest);
        }
    }

    private void populateResourcesMap() throws IOException {
        for (String resourceId : props.getProjectResourceIds()) {
            String query = format("https://%s/api/resources?format=json&resource=%s&depth=-1", props.getHost(), resourceId);
            JsonArray array = getJsonElementsFromQuery(props.getUsername(), props.getPassword(), query);
            for (JsonElement jsonElement : array) {
                JsonObject resource = jsonElement.getAsJsonObject();
                if (!"FIL".equals(resource.get("scope").getAsString())) {
                    continue;
                }

                String module = findModuleName(resource.get("key").getAsString(), ":");
                String mapKey = resource.get("lname").getAsString();
                mapKey = mapKey.substring(mapKey.indexOf("src/"));

                if (!resources.containsKey(module)) {
                    resources.put(module, new HashMap<String, String>());
                }
                resources.get(module).put(mapKey, resource.get("id").getAsString());
            }
        }
    }

    private void addPullRequest(int gitPullRequest) throws IOException {
        for (GHPullRequestFileDetail pullRequestFile : getPullRequestFiles(gitPullRequest)) {
            String gitFileName = pullRequestFile.getFilename();
            String resourceId = getResourceIdOrNull(gitFileName);
            GitHubStatus status;

            try {
                status = GitHubStatus.fromString(pullRequestFile.getStatus());
            } catch (IllegalStateException e) {
                throw new IllegalStateException(format("Could not get status for file %s in PR %d", gitFileName, gitPullRequest),
                        e.getCause());
            }

            FileContainer candidate = files.get(gitFileName);
            if (candidate != null) {
            candidate.pullRequests += ", " + gitPullRequest;
                candidate.status = updateStatus(candidate.status, status);
                candidate.gitHubChanges += pullRequestFile.getChanges();
                candidate.gitHubAdditions += pullRequestFile.getAdditions();
                candidate.gitHubDeletions += pullRequestFile.getDeletions();
                continue;
            }

            FileContainer fileContainer = new FileContainer();
            fileContainer.resourceId = resourceId;
            fileContainer.pullRequests = String.valueOf(gitPullRequest);
            fileContainer.fileName = gitFileName;
            fileContainer.status = status;
            fileContainer.gitHubChanges = pullRequestFile.getChanges();
            fileContainer.gitHubAdditions = pullRequestFile.getAdditions();
            fileContainer.gitHubDeletions = pullRequestFile.getDeletions();

            if (resourceId == null) {
                files.put(gitFileName, fileContainer);
                continue;
            }

            String query = format("https://%s/api/resources?resource=%s&metrics=%s", props.getHost(), resourceId, METRICS_LIST);
            JsonArray array = getJsonElementsFromQuery(props.getUsername(), props.getPassword(), query);
            for (JsonElement jsonElement : array) {
                JsonObject resource = jsonElement.getAsJsonObject();

                if (resource.has("msr")) {
                    setMetrics(fileContainer, resource);
                }

                files.put(gitFileName, fileContainer);
            }
        }
    }

    private PagedIterable<GHPullRequestFileDetail> getPullRequestFiles(int gitPullRequest) throws IOException {
        GHPullRequest pullRequest = repo.getPullRequest(gitPullRequest);
        return pullRequest.listFiles();
    }

    private String getResourceIdOrNull(String fileName) {
        if (!fileName.endsWith(".java")) {
            return null;
        }

        String module = findModuleName(fileName, "/");
        String mapKey = fileName.substring(fileName.indexOf("src/"));

        Map<String, String> entryMap = resources.get(module);
        if (entryMap == null) {
            return null;
        }
        return entryMap.get(mapKey);
    }

    private GitHubStatus updateStatus(GitHubStatus oldStatus, GitHubStatus newStatus) {
        if (oldStatus == GitHubStatus.REMOVED || newStatus == GitHubStatus.REMOVED) {
            return GitHubStatus.REMOVED;
        }
        if (oldStatus == GitHubStatus.ADDED || newStatus == GitHubStatus.ADDED) {
            return GitHubStatus.ADDED;
        }
        return newStatus;
    }

    private void setMetrics(FileContainer fileContainer, JsonObject resource) {
        for (JsonElement metricElement : resource.get("msr").getAsJsonArray()) {
            JsonObject metric = metricElement.getAsJsonObject();
            String key = metric.get("key").getAsString();
            String value = metric.get("frmt_val").getAsString();
            if ("coverage".equals(key)) {
                fileContainer.coverage = value;
                fileContainer.numericCoverage = metric.get("val").getAsDouble();
            } else if ("line_coverage".equals(key)) {
                fileContainer.lineCoverage = value;
                fileContainer.numericLineCoverage = metric.get("val").getAsDouble();
            } else if ("branch_coverage".equals(key)) {
                fileContainer.branchCoverage = value;
                fileContainer.numericBranchCoverage = metric.get("val").getAsDouble();
            } else {
                throw new IllegalStateException("unknown metric key: " + key);
            }
        }
    }
}
