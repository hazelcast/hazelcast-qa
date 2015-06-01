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
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHRepository;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLConnection;

public final class Utils {

    private Utils() {
    }

    public static String formatNullable(String value, String defaultValue) {
        return (value == null ? defaultValue : value);
    }

    public static String formatCoverage(String coverage) {
        if (coverage == null) {
            //return "{align:right}-{align}";
            return "-";
        }
        //return "{align:right}" + coverage + "{align}";
        return coverage;
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
