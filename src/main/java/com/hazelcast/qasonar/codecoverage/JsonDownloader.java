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
import com.hazelcast.qasonar.utils.PropertyReader;
import com.hazelcast.qasonar.utils.TimeTracker;
import com.hazelcast.qasonar.utils.TimeTrackerLabel;

import java.io.IOException;

import static com.hazelcast.qasonar.utils.Utils.getJsonElementsFromQuery;

public class JsonDownloader {

    private final PropertyReader props;

    public JsonDownloader(PropertyReader props) {
        this.props = props;
    }

    JsonArray getJsonArrayFromQuery(String query) throws IOException {
        long started = System.nanoTime();
        JsonArray jsonArray = getJsonElementsFromQuery(props.getUsername(), props.getPassword(), query);
        TimeTracker.record(TimeTrackerLabel.GET_JSON_ARRAY_FROM_QUERY, System.nanoTime() - started);
        return jsonArray;
    }
}
