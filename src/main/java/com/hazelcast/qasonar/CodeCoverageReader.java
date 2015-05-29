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
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestFileDetail;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.PagedIterable;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.hazelcast.qa.Utils.getFileContentsFromGitHub;
import static com.hazelcast.qa.Utils.getJsonElementsFromQuery;
import static java.lang.String.format;
import static org.apache.commons.io.FilenameUtils.removeExtension;

public class CodeCoverageReader {

    private static final String METRICS_LIST = "coverage,line_coverage,branch_coverage";

    private final Map<String, Integer> resources = new HashMap<String, Integer>();
    private final Map<String, TableEntry> tableEntries = new HashMap<String, TableEntry>();

    private final PropertyReader props;
    private final GHRepository repo;

    public CodeCoverageReader(PropertyReader propertyReader) throws IOException {
        this.props = propertyReader;

        GitHub github = GitHub.connect();
        this.repo = github.getRepository(propertyReader.getGitHubRepository());

        populateResourcesMap();
    }

    public Map<String, TableEntry> getTableEntries() {
        return Collections.unmodifiableMap(tableEntries);
    }

    public void addPullRequest(int gitPullRequest) throws IOException {
        populateTableEntries(gitPullRequest);
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
            int resourceId = resource.get("id").getAsInt();
            String fileName = resource.get("lname").getAsString();
            resources.put(fileName, resourceId);
        }
    }

    private void populateTableEntries(int gitPullRequest) throws IOException {
        for (GHPullRequestFileDetail pullRequestFile : getPullRequestFiles(gitPullRequest)) {
            String gitFileName = pullRequestFile.getFilename();
            Integer resourceId = getResourceIdOrNull(gitFileName);

            TableEntry candidate = tableEntries.get(gitFileName);
            if (candidate != null) {
                candidate.pullRequest += ", " + gitPullRequest;
                continue;
            }

            TableEntry tableEntry = new TableEntry();
            tableEntry.resourceId = resourceId == null ? null : resourceId.toString();
            tableEntry.fileName = gitFileName;
            tableEntry.pullRequest = String.valueOf(gitPullRequest);

            if (resourceId == null) {
                tableEntry.qaCheck = "OK";
                if (gitFileName.endsWith(".java")) {
                    addWithComment(gitFileName, tableEntry, "deleted");
                } else {
                    addWithComment(gitFileName, tableEntry, "no Java file");
                }
                continue;
            } else if (gitFileName.startsWith("hazelcast-client-new")) {
                addWithComment(gitFileName, tableEntry, "new client is not in SonarQube");
                continue;
            }

            String query = format("https://%s/api/resources?resource=%d&metrics=%s", props.getHost(), resourceId, METRICS_LIST);
            System.out.println(query);
            JsonArray array = getJsonElementsFromQuery(props.getUsername(), props.getPassword(), query);
            for (JsonElement jsonElement : array) {
                JsonObject resource = jsonElement.getAsJsonObject();
                String simpleName = resource.get("name").getAsString();
                if (!simpleName.endsWith(".java") || simpleName.equals("package-info.java")) {
                    tableEntry.qaCheck = "OK";
                    addWithComment(gitFileName, tableEntry, "no Java class");
                    continue;
                }

                if (!resource.has("msr")) {
                    String fileContents = getFileContentsFromGitHub(repo, gitFileName);
                    String baseName = removeExtension(simpleName);
                    if (fileContents.contains(" interface " + baseName)) {
                        tableEntry.qaCheck = "OK";
                        addWithComment(gitFileName, tableEntry, "Interface");
                    } else if (fileContents.contains("@RunWith")) {
                        tableEntry.qaCheck = "OK";
                        addWithComment(gitFileName, tableEntry, "Test");
                    } else {
                        addWithComment(gitFileName, tableEntry, "code coverage not found");
                    }
                    continue;
                }

                for (JsonElement metricElement : resource.get("msr").getAsJsonArray()) {
                    JsonObject metric = metricElement.getAsJsonObject();
                    String key = metric.get("key").getAsString();
                    String value = metric.get("frmt_val").getAsString();
                    if ("coverage".equals(key)) {
                        tableEntry.coverage = value;
                        tableEntry.numericCoverage = metric.get("val").getAsDouble();
                    } else if ("line_coverage".equals(key)) {
                        tableEntry.lineCoverage = value;
                    } else if ("branch_coverage".equals(key)) {
                        tableEntry.branchCoverage = value;
                    } else {
                        tableEntry.comment = "unknown metric key: " + key;
                    }
                }

                if (tableEntry.numericCoverage > props.getMinCodeCoverage()) {
                    tableEntry.qaCheck = "OK";
                } else if (tableEntry.comment == null) {
                    tableEntry.comment = "code coverage below " + props.getMinCodeCoverage() + "%";
                }

                tableEntries.put(gitFileName, tableEntry);
            }
        }
    }

    private PagedIterable<GHPullRequestFileDetail> getPullRequestFiles(int gitPullRequest) throws IOException {
        GHPullRequest pullRequest = repo.getPullRequest(gitPullRequest);
        return pullRequest.listFiles();
    }

    private Integer getResourceIdOrNull(String fileName) {
        while (fileName.contains("/")) {
            fileName = fileName.substring(fileName.indexOf("/") + 1);
            Integer resourceId = resources.get(fileName);
            if (resourceId != null) {
                return resourceId;
            }
        }
        return null;
    }

    private void addWithComment(String gitFileName, TableEntry tableEntry, String comment) {
        tableEntry.comment = comment;
        tableEntries.put(gitFileName, tableEntry);
    }
}
