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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hazelcast.qa.PropertyReader;
import com.hazelcast.qa.Utils;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestFileDetail;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.PagedIterable;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.hazelcast.qa.Utils.findModuleName;
import static com.hazelcast.qa.Utils.getJsonElementsFromQuery;
import static java.lang.String.format;

public class CodeCoverageReader {

    private static final String METRICS_LIST = "coverage,line_coverage,branch_coverage";

    private final Map<String, Map<String, String>> resources = new HashMap<String, Map<String, String>>();
    private final Map<String, TableEntry> tableEntries = new HashMap<String, TableEntry>();

    private final PropertyReader props;
    private final GHRepository repo;

    public CodeCoverageReader(PropertyReader propertyReader, GHRepository repo) throws IOException {
        this.props = propertyReader;
        this.repo = repo;

        populateResourcesMap();
    }

    public Map<String, TableEntry> getTableEntries() {
        return Collections.unmodifiableMap(tableEntries);
    }

    public void addPullRequest(int gitPullRequest) throws IOException {
        for (GHPullRequestFileDetail pullRequestFile : getPullRequestFiles(gitPullRequest)) {
            GitHubStatus status = GitHubStatus.fromString(pullRequestFile.getStatus());
            String gitFileName = pullRequestFile.getFilename();
            String resourceId = getResourceIdOrNull(gitFileName);

            TableEntry candidate = tableEntries.get(gitFileName);
            if (candidate != null) {
                candidate.pullRequest += ", " + gitPullRequest;
                candidate.status = status;
                candidate.gitHubChanges += pullRequestFile.getChanges();
                candidate.gitHubAdditions += pullRequestFile.getAdditions();
                candidate.gitHubDeletions += pullRequestFile.getDeletions();
                continue;
            }

            TableEntry tableEntry = new TableEntry();
            tableEntry.resourceId = resourceId;
            tableEntry.pullRequest = String.valueOf(gitPullRequest);
            tableEntry.fileName = gitFileName;
            tableEntry.status = status;
            tableEntry.gitHubChanges = pullRequestFile.getChanges();
            tableEntry.gitHubAdditions = pullRequestFile.getAdditions();
            tableEntry.gitHubDeletions = pullRequestFile.getDeletions();

            if (resourceId == null) {
                tableEntries.put(gitFileName, tableEntry);
                continue;
            }

            String query = format("https://%s/api/resources?resource=%s&metrics=%s", props.getHost(), resourceId, METRICS_LIST);
            JsonArray array = getJsonElementsFromQuery(props.getUsername(), props.getPassword(), query);
            for (JsonElement jsonElement : array) {
                JsonObject resource = jsonElement.getAsJsonObject();

                if (resource.has("msr")) {
                    setMetrics(tableEntry, resource);
                }

                tableEntries.put(gitFileName, tableEntry);
            }
        }
    }

    private void populateResourcesMap() throws IOException {
        String query = format("https://%s/api/resources?format=json&resource=%s&depth=-1",
                props.getHost(), props.getProjectResourceId());
        JsonArray array = getJsonElementsFromQuery(props.getUsername(), props.getPassword(), query);
        for (JsonElement jsonElement : array) {
            JsonObject resource = jsonElement.getAsJsonObject();
            if (!"FIL".equals(resource.get("scope").getAsString())) {
                continue;
            }

            String module = Utils.findModuleName(resource.get("key").getAsString(), ":");
            String mapKey = resource.get("lname").getAsString();
            mapKey = mapKey.substring(mapKey.indexOf("src/"));

            if (!resources.containsKey(module)) {
                resources.put(module, new HashMap<String, String>());
            }

            resources.get(module).put(mapKey, resource.get("id").getAsString());
        }
    }

    private PagedIterable<GHPullRequestFileDetail> getPullRequestFiles(int gitPullRequest) throws IOException {
        GHPullRequest pullRequest = repo.getPullRequest(gitPullRequest);
        return pullRequest.listFiles();
    }

    private String getResourceIdOrNull(String fileName) {
        String module = findModuleName(fileName, "/");
        String mapKey = fileName.substring(fileName.indexOf("src/"));

        Map<String, String> entryMap = resources.get(module);
        if (entryMap == null) {
            return null;
        }
        return entryMap.get(mapKey);
    }

    private void setMetrics(TableEntry tableEntry, JsonObject resource) {
        for (JsonElement metricElement : resource.get("msr").getAsJsonArray()) {
            JsonObject metric = metricElement.getAsJsonObject();
            String key = metric.get("key").getAsString();
            String value = metric.get("frmt_val").getAsString();
            if ("coverage".equals(key)) {
                tableEntry.coverage = value;
                tableEntry.numericCoverage = metric.get("val").getAsDouble();
            } else if ("line_coverage".equals(key)) {
                tableEntry.lineCoverage = value;
                tableEntry.numericLineCoverage = metric.get("val").getAsDouble();
            } else if ("branch_coverage".equals(key)) {
                tableEntry.branchCoverage = value;
                tableEntry.numericBranchCoverage = metric.get("val").getAsDouble();
            } else {
                throw new IllegalStateException("unknown metric key: " + key);
            }
        }
    }
}
