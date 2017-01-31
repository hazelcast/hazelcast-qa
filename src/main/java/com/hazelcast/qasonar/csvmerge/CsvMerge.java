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

package com.hazelcast.qasonar.csvmerge;

import com.hazelcast.qasonar.utils.FileFinder;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.hazelcast.qasonar.ideaconverter.IdeaConverter.OUTPUT_FILENAME;
import static com.hazelcast.qasonar.utils.DebugUtils.debug;
import static com.hazelcast.qasonar.utils.DebugUtils.print;
import static com.hazelcast.qasonar.utils.DebugUtils.printGreen;
import static com.hazelcast.qasonar.utils.DebugUtils.printYellow;
import static java.lang.String.format;
import static java.nio.file.Files.readAllLines;
import static java.nio.file.Files.walkFileTree;
import static java.nio.file.Files.write;

public class CsvMerge {

    // repository name -> filename -> idea coverage
    private final Map<String, Map<String, Double>> coverageMap = new HashMap<>();

    public void run() throws IOException {
        FileFinder finder = new FileFinder("*.csv");
        walkFileTree(Paths.get("").toAbsolutePath(), finder);

        Collection<Path> matchedFiles = finder.getMatchedPaths();
        print("Merging %d CSV files...", matchedFiles.size());

        for (Path file : matchedFiles) {
            List<String> lines = readAllLines(file);
            for (String line : lines) {
                String[] lineArray = null;
                try {
                    lineArray = line.split(";");
                    String repoName = lineArray[0];
                    String fileName = lineArray[1];
                    Double coverage = Double.valueOf(lineArray[2]);

                    Map<String, Double> repoMap = coverageMap.computeIfAbsent(repoName, k -> new HashMap<>());

                    Double oldCoverage = repoMap.get(fileName);
                    if (oldCoverage == null) {
                        repoMap.put(fileName, coverage);
                    } else if (coverage > oldCoverage) {
                        debug("Replaced coverage %.1f with %.1f for class %s", oldCoverage, coverage, fileName);
                        repoMap.put(fileName, coverage);
                    }
                } catch (Exception e) {
                    System.err.println("File: " + file);
                    System.err.println("Line: " + line);
                    System.err.println("Array: " + Arrays.toString(lineArray));
                    throw e;
                }
            }
        }

        int classCount = coverageMap.size();
        print("Merged coverage data for %d classes...", classCount);
        if (classCount == 0) {
            printYellow("Nothing to store, we're done!");
            return;
        }

        print("Storing results...");
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Map<String, Double>> repoMap : coverageMap.entrySet()) {
            String repoName = repoMap.getKey();
            for (Map.Entry<String, Double> coverageEntry : repoMap.getValue().entrySet()) {
                sb.append(format("%s;%s;%.1f%n", repoName, coverageEntry.getKey(), coverageEntry.getValue()));
            }
        }
        write(Paths.get(OUTPUT_FILENAME), sb.toString().getBytes());

        printGreen("Done!");
    }
}
