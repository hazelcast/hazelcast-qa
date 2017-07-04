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

package com.hazelcast.qasonar.outputMerge;

import com.hazelcast.utils.FileFinder;
import com.hazelcast.utils.PropertyReader;
import com.hazelcast.utils.Repository;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.hazelcast.utils.DebugUtils.print;
import static com.hazelcast.utils.DebugUtils.printGreen;
import static com.hazelcast.utils.DebugUtils.printRed;
import static com.hazelcast.utils.DebugUtils.printYellow;
import static com.hazelcast.utils.Utils.writeToFile;
import static java.nio.file.Files.readAllLines;
import static java.nio.file.Files.walkFileTree;

public class OutputMerge {

    private final Map<Repository, String> content = new HashMap<>();

    private final PropertyReader propertyReader;

    public OutputMerge(PropertyReader propertyReader) {
        this.propertyReader = propertyReader;
    }

    public void run() throws IOException {
        String outputFile = propertyReader.getOutputFile();

        FileFinder finder = new FileFinder(outputFile + "-(" + Repository.getSuffixes("|") + ").txt", true);
        walkFileTree(Paths.get("").toAbsolutePath(), finder);

        Collection<Path> matchedFiles = finder.getMatchedPaths();
        int matchedFilesNumber = matchedFiles.size();
        if (matchedFilesNumber == 0) {
            printRed("No files found!");
            return;
        }
        if (matchedFilesNumber == 1) {
            printYellow("Found just a single file, are you sure you need to merge it?");
        }

        print("Merging %d txt files...", matchedFilesNumber);
        StringBuilder sb = mergeFiles(outputFile, matchedFiles);
        if (sb == null) {
            return;
        }

        print("Creating new output file...");
        writeMergedFile(outputFile, sb);

        printGreen("Done!\n");
    }

    private StringBuilder mergeFiles(String outputFile, Collection<Path> matchedFiles) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (Path file : matchedFiles) {
            String fileName = file.getFileName().toString().toLowerCase();
            String baseName = fileName.substring(0, fileName.lastIndexOf('.'));

            if (baseName.equals(outputFile)) {
                printYellow("Filename equals the output filename (skipping)...");
                continue;
            }

            print("Parsing %s...", fileName);

            Repository repository = null;
            int suffixPos = baseName.lastIndexOf('-');
            if (suffixPos != -1) {
                String suffix = baseName.substring(suffixPos + 1);
                repository = Repository.fromSuffix(suffix);
            }
            if (repository == null) {
                printRed("Filename must contain a project postfix: " + fileName);
                return null;
            }

            List<String> lines = readAllLines(file);
            for (String line : lines) {
                sb.append(line).append("\n");
            }
            content.put(repository, sb.toString());
            sb.setLength(0);
        }
        return sb;
    }

    private void writeMergedFile(String outputFile, StringBuilder sb) throws IOException {
        sb.append("{toc:type=list|style=disc|minLevel=1|maxLevel=7|indent=|class=|")
                .append("outline=false|include=|exclude=|printable=true}\n\n");
        for (Repository repository : Repository.values()) {
            if (!content.containsKey(repository)) {
                continue;
            }

            sb.append("h1. Code Coverage (").append(repository).append(")\n");
            sb.append(content.get(repository));
            sb.append("\n");
        }
        writeToFile(outputFile + ".txt", sb);
    }
}
