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

package com.hazelcast.qasonar.codecoverage;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hazelcast.qasonar.utils.GitHubStatus;
import com.hazelcast.qasonar.utils.PropertyReader;
import com.hazelcast.qasonar.utils.Repository;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestFileDetail;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.PagedIterable;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.hazelcast.qasonar.ideaconverter.IdeaConverter.OUTPUT_FILENAME;
import static com.hazelcast.qasonar.utils.DebugUtils.debug;
import static com.hazelcast.qasonar.utils.DebugUtils.printRed;
import static com.hazelcast.qasonar.utils.Repository.fromRepositoryName;
import static com.hazelcast.qasonar.utils.Utils.findModuleName;
import static java.lang.String.format;
import static java.nio.file.Files.exists;
import static java.nio.file.Files.readAllLines;

public class CodeCoverageReader {

    private static final String METRICS_LIST = "coverage,line_coverage,branch_coverage";

    private final Map<String, Map<String, String>> resources = new HashMap<>();
    private final Map<String, Double> ideaCoverage = new HashMap<>();
    private final Map<String, FileContainer> files = new HashMap<>();

    private final PropertyReader props;
    private final GHRepository repo;
    private final Repository repository;
    private final JsonDownloader jsonDownloader;

    public CodeCoverageReader(PropertyReader propertyReader, GHRepository repo, JsonDownloader jsonDownloader) {
        this.props = propertyReader;
        this.repo = repo;
        this.repository = fromRepositoryName(repo.getName());
        this.jsonDownloader = jsonDownloader;
    }

    public Map<String, FileContainer> getFiles() {
        return Collections.unmodifiableMap(files);
    }

    public void run(List<Integer> pullRequests) throws IOException {
        populateResourcesMap();
        populateIdeaCoverage();

        for (Integer pullRequest : pullRequests) {
            debug(format("Adding pull request %d...", pullRequest));
            addPullRequest(pullRequest);
        }
    }

    private void populateResourcesMap() throws IOException {
        for (String resourceId : props.getProjectResourceIds()) {
            String query = format("https://%s/api/resources?format=json&resource=%s&depth=-1", props.getHost(), resourceId);
            JsonArray array = jsonDownloader.getJsonArrayFromQuery(query);
            for (JsonElement jsonElement : array) {
                JsonObject resource = jsonElement.getAsJsonObject();
                if (!"FIL".equals(resource.get("scope").getAsString())) {
                    continue;
                }

                String module = findModuleName(resource.get("key").getAsString(), ":");
                String mapKey = resource.get("lname").getAsString();
                mapKey = mapKey.substring(mapKey.indexOf("src/"));

                if (!resources.containsKey(module)) {
                    resources.put(module, new HashMap<>());
                }
                resources.get(module).put(mapKey, resource.get("id").getAsString());
            }
        }
    }

    private void populateIdeaCoverage() throws IOException {
        Path path = Paths.get(OUTPUT_FILENAME);
        if (!exists(path)) {
            return;
        }

        List<String> lines = readAllLines(path);
        debug(format("Adding %d classes from IDEA coverage report...", lines.size()));

        for (String line : lines) {
            String[] lineArray = line.split(";");
            String fileName = lineArray[0];
            Double coverage = Double.valueOf(lineArray[1]);
            ideaCoverage.put(fileName, coverage);
        }
    }

    private void addPullRequest(int gitPullRequest) throws IOException {
        for (GHPullRequestFileDetail pullRequestFile : getPullRequestFiles(gitPullRequest)) {
            String gitFileName = getFileNameWithDefaultModule(pullRequestFile.getFilename());
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
            fileContainer.isModuleDeleted = isModuleDeleted(gitFileName);
            fileContainer.gitHubChanges = pullRequestFile.getChanges();
            fileContainer.gitHubAdditions = pullRequestFile.getAdditions();
            fileContainer.gitHubDeletions = pullRequestFile.getDeletions();
            fileContainer.ideaCoverage = getIdeaCoverage(gitFileName);

            if (resourceId == null) {
                files.put(gitFileName, fileContainer);
                continue;
            }

            String query = format("https://%s/api/resources?resource=%s&metrics=%s", props.getHost(), resourceId, METRICS_LIST);
            JsonArray array = jsonDownloader.getJsonArrayFromQuery(query);
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

    private String getFileNameWithDefaultModule(String fileName) {
        if (fileName.startsWith("src/") && fileName.endsWith(".java")) {
            if (props.isDefaultModuleSet()) {
                return props.getDefaultModule() + "/" + fileName;
            }
            if (repository.hasDefaultModule()) {
                return repository.getDefaultModule() + "/" + fileName;
            }
            String message = "Could not find module for " + fileName + " and default module is not set!";
            if (props.throwExceptionOnMissingModule()) {
                throw new IllegalArgumentException(message);
            }
            printRed(message);
            return "/" + fileName;
        }
        return fileName;
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
        if (oldStatus != GitHubStatus.RENAMED && newStatus == GitHubStatus.RENAMED) {
            return oldStatus;
        }
        return newStatus;
    }

    private boolean isModuleDeleted(String gitFileName) {
        int firstSlashIndex = gitFileName.indexOf('/');
        if (firstSlashIndex == -1) {
            return false;
        }

        String module = gitFileName.substring(0, firstSlashIndex);
        return (resources.get(module) == null);
    }

    private double getIdeaCoverage(String fileName) {
        if (!fileName.endsWith(".java")) {
            return 0;
        }

        int beginIndex = fileName.indexOf("com/hazelcast");
        if (beginIndex == -1 && props.isDefaultModuleSet()) {
            beginIndex = fileName.indexOf(props.getDefaultModule());
        }
        if (beginIndex == -1) {
            beginIndex = fileName.indexOf("com.hazelcast");
            if (props.isDefaultModuleSet()) {
                printRed("Filename doesn't contain com/hazelcast or %s: %s", props.getDefaultModule(), fileName);
            } else {
                printRed("Filename doesn't contain com/hazelcast: %s", fileName);
            }
        }
        if (beginIndex == -1) {
            printRed("Could not parse fully qualified class name: %s", fileName);
            return 0;
        }
        String fullyQualifiedClassName = fileName.substring(beginIndex).replace('/', '.');

        Double coverage = ideaCoverage.get(fullyQualifiedClassName);
        return (coverage == null) ? 0 : coverage;
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
