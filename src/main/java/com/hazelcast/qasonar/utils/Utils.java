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

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import org.apache.commons.codec.binary.Base64;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHRepository;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.hazelcast.qasonar.utils.DebugUtils.isDebug;
import static java.lang.String.format;
import static org.apache.commons.io.IOUtils.copy;

public final class Utils {

    private static final int GITHUB_FILE_DOWNLOAD_RETRIES = 3;
    private static final int GITHUB_FILE_DOWNLOAD_RETRY_DELAY_MILLIS = 500;

    private Utils() {
    }

    public static void appendCommandLine(PropertyReader props, StringBuilder sb, List<Integer> pullRequests, boolean plain) {
        if (plain) {
            sb.append("Executing: ");
        } else {
            sb.append("Command line: {{");
        }
        sb.append("qa-sonar");
        if (isDebug()) {
            sb.append(" --verbose");
        }
        if (props.getMinThresholdModified() > 0) {
            sb.append(" --minThresholdModified ").append(props.getMinThresholdModified());
        }
        addPullRequests(sb, pullRequests, plain);
        if (props.isGitHubRepositoryOverwritten()) {
            sb.append(" --gitHubRepository ").append(props.getGitHubRepository());
        }
        if (plain) {
            if (props.getOutputFile() != null) {
                sb.append(" --outputFile ").append(props.getOutputFile());
            }
        } else {
            sb.append("}}\n");
        }
    }

    @SuppressWarnings("checkstyle:magicnumber")
    private static void addPullRequests(StringBuilder sb, List<Integer> pullRequests, boolean plain) {
        sb.append(" --pullRequests ");
        String separator = "";
        int counter = 1;
        int limit = 15;
        for (Integer pullRequest : pullRequests) {
            sb.append(separator).append(pullRequest);
            if (counter++ % limit == 0) {
                separator = (plain ? ",\n" : ",}}\n{{");
                counter = 1;
                limit = 30;
            } else {
                separator = ",";
            }
        }
    }

    public static String fillString(int length, char charToFill) {
        if (length == 0) {
            return "";
        }
        char[] array = new char[length];
        Arrays.fill(array, charToFill);
        return new String(array);
    }

    public static String formatSonarQubeLink(PropertyReader props, String resourceId, boolean plainOutput) {
        if (resourceId == null) {
            return "?????";
        }
        if (plainOutput) {
            return resourceId;
        }
        return format("[%s|https://%s/resource/index/%s?display_title=true&metric=coverage]",
                resourceId, props.getHost(), resourceId);
    }

    public static String formatPullRequestLinks(PropertyReader props, String pullRequest, boolean plainOutput) {
        if (plainOutput) {
            return pullRequest;
        }
        StringBuilder sb = new StringBuilder();
        String separator = "";
        int counter = 1;
        for (String pullRequestPart : pullRequest.split(",")) {
            sb.append(separator).append(formatPullRequestLinks(props, pullRequestPart.trim()));
            separator = (counter++ % 3 == 0) ? ",\n" : ", ";
        }
        return sb.toString();
    }

    private static String formatPullRequestLinks(PropertyReader props, String pullRequest) {
        return format("[%s|https://github.com/%s/pull/%s]",
                pullRequest, props.getGitHubRepository(), pullRequest);
    }

    public static String formatMinWidth(String value, int minWidth) {
        return format("%-" + minWidth + "s", value);
    }

    public static String formatFileName(String fileName, boolean plainOutput, int width) {
        if (plainOutput) {
            return formatMinWidth(fileName
                    .replace("src/main/java/com/hazelcast", "main")
                    .replace("src/test/java/com/hazelcast", "test"), width);
        }
        return format("{span:style=font-size:12px}%s{span}", fileName);
    }

    public static String formatGitHubStatus(GitHubStatus status, boolean plainOutput) {
        return plainOutput ? format("%-8s", status.toString()) : status.toString();
    }

    public static String formatGitHubChanges(int gitHubChanges, String sign, boolean plainOutput) {
        if (gitHubChanges <= 0) {
            sign = "";
        }
        return plainOutput ? format("%4s", sign + gitHubChanges) : formatAlignRight(sign + gitHubChanges);
    }

    public static String formatCoverage(String coverage, boolean plainOutput) {
        if (plainOutput) {
            return format("%6s", formatNullable(coverage, "-"));
        }
        return formatAlignRight(formatNullable(coverage, "-"));
    }

    public static String formatNullable(String value, String defaultValue, boolean plainOutput, int minWidth) {
        if (plainOutput) {
            return format("%-" + minWidth + "s", formatNullable(value, defaultValue));
        }
        return formatNullable(value, defaultValue);
    }

    private static String formatNullable(String value, String defaultValue) {
        return (value == null ? defaultValue : value.replace("\n", " \\\\ "));
    }

    private static String formatAlignRight(String value) {
        return "{align:right}" + value + "{align}";
    }

    public static String findModuleName(String fileName, String delimiter) {
        String[] keyParts = fileName.split(delimiter);
        if (keyParts.length < 2) {
            throw new IllegalArgumentException("File name has not enough elements: " + fileName);
        }
        for (int index = 0; index < keyParts.length; index++) {
            if (keyParts[index].equals("src") || keyParts[index].startsWith("src/")) {
                return keyParts[index - 1];
            }
        }
        throw new IllegalArgumentException("Could not find module in file name: " + fileName);
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
            sleepMillis(GITHUB_FILE_DOWNLOAD_RETRY_DELAY_MILLIS);
        }
        throw exception;
    }

    public static JsonArray getJsonElementsFromQuery(String username, String password, String query) throws IOException {
        String result = getStringFromQuery(query, username, password);

        Gson gson = new Gson();
        return gson.fromJson(result, JsonArray.class);
    }

    private static String getStringFromQuery(String query, String username, String password) throws IOException {
        StringWriter writer = new StringWriter();
        copy(getBaseAuthInputStreamFromURL(query, username, password), writer);

        return writer.toString();
    }

    private static InputStream getBaseAuthInputStreamFromURL(String query, String username, String password) throws IOException {
        URL url = new URL(query);

        URLConnection uc = url.openConnection();
        String authString = username + ":" + password;
        String basicAuth = "Basic " + new String(new Base64().encode(authString.getBytes()));
        uc.setRequestProperty("Authorization", basicAuth);

        return uc.getInputStream();
    }

    public static void writeToFile(String fileName, StringBuilder content) throws IOException {
        File file = new File(fileName);
        FileWriter fileWriter = new FileWriter(file);
        BufferedWriter writer = new BufferedWriter(fileWriter);
        writer.write(content.toString());
        writer.flush();
        writer.close();
    }

    public static void sleepMillis(int millis) {
        try {
            TimeUnit.MILLISECONDS.sleep(millis);
        } catch (InterruptedException ignore) {
            Thread.currentThread().interrupt();
        }
    }
}
