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

package com.hazelcast.utils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

import static com.hazelcast.utils.DebugUtils.print;
import static com.hazelcast.utils.DebugUtils.printRed;
import static java.nio.file.Files.lines;

public final class CsvUtils {

    public static final String NOT_AVAILABLE = "n/a";

    private CsvUtils() {
    }

    public static void readCSV(Path commitPath, Map<String, String> commits) {
        try (Stream<String> stream = lines(commitPath)) {
            stream.forEach(line -> {
                String[] split = line.split(";");
                commits.put(split[0], split[1]);
            });
        } catch (IOException e) {
            printRed("Could not read commits [%s] %s", e.getClass().getSimpleName(), e.getMessage());
        }
        print("Found %d commits in %s", commits.size(), commitPath.getFileName());
    }
}
