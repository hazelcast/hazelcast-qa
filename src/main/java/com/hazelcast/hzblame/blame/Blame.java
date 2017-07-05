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

package com.hazelcast.hzblame.blame;

import com.hazelcast.hzblame.utils.CommandLineOptions;
import com.hazelcast.utils.PropertyReader;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static java.nio.file.Files.lines;

public class Blame {

    private final Path commitPath = Paths.get("ee-os.csv");
    private final Map<String, String> commits = new HashMap<>();

    private final PropertyReader propertyReader;
    private final CommandLineOptions commandLineOptions;

    public Blame(PropertyReader propertyReader, CommandLineOptions commandLineOptions) {
        this.propertyReader = propertyReader;
        this.commandLineOptions = commandLineOptions;
    }

    public void run() {
        readCSV();
    }

    private void readCSV() {
        try (Stream<String> stream = lines(commitPath)) {
            stream.forEach(s -> {
                String[] split = s.split(";");
                commits.put(split[0], split[1]);
            });
        } catch (IOException e) {
            System.err.println("Could not read commits! " + e.getMessage());
        }
        System.out.printf("Found %d commits!\n", commits.size());
    }
}
