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

package com.hazelcast.qa;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.hazelcast.qasonar.GitHubStatus;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHRepository;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;

import static java.lang.String.format;

public final class Utils {

    private Utils() {
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
        if (plainOutput) {
            return resourceId;
        }
        return format("[%s|https://%s/resource/index/%s?display_title=true&metric=coverage]",
                resourceId, props.getHost(), resourceId);
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
        int pos = fileName.lastIndexOf('/');
        if (fileName.length() < 80 || pos == -1) {
            return fileName;
        }
        return fileName.substring(0, pos) + "/\n" + fileName.substring(pos + 1, fileName.length());
    }

    public static String formatGitHubStatus(GitHubStatus status, boolean plainOutput) {
        return plainOutput ? format("%-8s", status.toString()) : status.toString();
    }

    public static String formatGitHubChanges(int gitHubChanges, String sign, boolean plainOutput) {
        if (gitHubChanges <= 0) {
            sign = "";
        }
        return plainOutput ? format("%4s", sign + gitHubChanges) : sign + gitHubChanges;
    }

    public static String formatCoverage(String coverage, boolean plainOutput) {
        if (plainOutput) {
            return format("%6s", formatNullable(coverage, "-"));
        }
        return "{align:right}" + formatNullable(coverage, "-") + "{align}";
    }

    public static String formatNullable(String value, String defaultValue, boolean plainOutput, int minWidth) {
        if (plainOutput) {
            return format("%-" + minWidth + "s", formatNullable(value, defaultValue));
        }
        return formatNullable(value, defaultValue);
    }

    private static String formatNullable(String value, String defaultValue) {
        return (value == null ? defaultValue : value);
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
        GHContent fileContent = repo.getFileContent(fileName);

        StringWriter writer = new StringWriter();
        IOUtils.copy(fileContent.read(), writer);

        return writer.toString();
    }

    public static JsonArray getJsonElementsFromQuery(String username, String password, String query) throws IOException {
        String result = getStringFromQuery(query, username, password);

        Gson gson = new Gson();
        return gson.fromJson(result, JsonArray.class);
    }

    private static String getStringFromQuery(String query, String username, String password) throws IOException {
        StringWriter writer = new StringWriter();
        IOUtils.copy(getBaseAuthInputStreamFromURL(query, username, password), writer);

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
}
