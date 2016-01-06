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

package com.hazelcast.qasonar.listprojects;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hazelcast.qasonar.utils.PropertyReader;

import java.io.IOException;

import static com.hazelcast.qasonar.utils.Utils.getJsonElementsFromQuery;
import static java.lang.String.format;

public class ListProjects {

    private final PropertyReader props;

    public ListProjects(PropertyReader propertyReader) {
        this.props = propertyReader;
    }

    public void run() throws IOException {
        StringBuilder sb = new StringBuilder();
        String separator = "";

        String query = format("https://%s/api/resources?format=json", props.getHost());
        JsonArray array = getJsonElementsFromQuery(props.getUsername(), props.getPassword(), query);
        for (JsonElement jsonElement : array) {
            JsonObject resource = jsonElement.getAsJsonObject();

            sb.append(separator)
                    .append(resource.get("name").getAsString()).append('\n')
                    .append("Description: ").append(resource.get("description").getAsString()).append('\n')
                    .append("Version: ").append(resource.get("version").getAsString()).append('\n')
                    .append("ResourceId: ").append(resource.get("id").getAsInt()).append('\n');

            separator = "\n";
        }

        System.out.println(sb.toString());
    }
}
