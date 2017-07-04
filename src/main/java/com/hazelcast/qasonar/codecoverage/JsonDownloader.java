/*
 * Copyright (c) 2008-2017, Hazelcast, Inc. All Rights Reserved.
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
import com.hazelcast.utils.PropertyReader;
import com.hazelcast.utils.TimeTrackerLabel;

import java.io.IOException;

import static com.hazelcast.utils.TimeTracker.record;
import static com.hazelcast.utils.Utils.getBasicAuthString;
import static com.hazelcast.utils.Utils.getJsonElementsFromQuery;

class JsonDownloader {

    private final String basicAuthString;

    JsonDownloader(PropertyReader props) {
        this.basicAuthString = getBasicAuthString(props.getUsername(), props.getPassword());
    }

    JsonArray getJsonArrayFromQuery(TimeTrackerLabel label, String query) throws IOException {
        long started = System.nanoTime();
        JsonArray jsonArray = getJsonElementsFromQuery(basicAuthString, query);
        record(label, System.nanoTime() - started);

        return jsonArray;
    }
}
