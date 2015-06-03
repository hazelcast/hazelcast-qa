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

package com.hazelcast.qasonar.utils;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.commons.io.IOUtils;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import static org.apache.commons.io.IOUtils.closeQuietly;

public final class WhiteListBuilder {

    private WhiteListBuilder() {
    }

    public static WhiteList fromJsonFile(PropertyReader propertyReader) {
        return fromJsonFile(propertyReader.getWhiteListFileName());
    }

    public static WhiteList fromJsonFile(String propertyFileName) {
        WhiteList whiteList = new WhiteList();
        if (propertyFileName == null) {
            return whiteList;
        }

        JsonArray array;
        if (propertyFileName.startsWith("http://") || propertyFileName.startsWith("https://")) {
            array = getJsonArrayFromUrl(propertyFileName);
        } else {
            array = getJsonArrayFromFile(propertyFileName);
        }

        populateWhiteList(whiteList, array);
        return whiteList;
    }

    private static JsonArray getJsonArrayFromUrl(String propertyFileName) {
        InputStream inputStream = null;
        try {
            inputStream = new URL(propertyFileName).openStream();
            String json = IOUtils.toString(inputStream);

            Gson gson = new Gson();
            return gson.fromJson(json, JsonArray.class);
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read whitelist from url " + propertyFileName, e.getCause());
        } finally {
            closeQuietly(inputStream);
        }
    }

    private static JsonArray getJsonArrayFromFile(String propertyFileName) {
        BufferedReader bufferedReader = null;
        FileReader fileReader = null;
        try {
            fileReader = new FileReader(propertyFileName);
            bufferedReader = new BufferedReader(fileReader);

            Gson gson = new Gson();
            return gson.fromJson(bufferedReader, JsonArray.class);
        } catch (FileNotFoundException e) {
            throw new IllegalArgumentException("Could not read whitelist from file " + propertyFileName, e.getCause());
        } finally {
            closeQuietly(fileReader);
            closeQuietly(bufferedReader);
        }
    }

    private static void populateWhiteList(WhiteList whiteList, JsonArray array) {
        for (JsonElement element : array) {
            JsonObject entry = element.getAsJsonObject();
            String type = entry.get("type").getAsString();
            String value = entry.get("value").getAsString();
            String justification = entry.get("justification").getAsString();
            whiteList.addEntry(type, value, justification);
        }
    }
}
