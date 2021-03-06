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

package com.hazelcast.qasonar.ideaconverter;

import com.hazelcast.utils.FileFinder;
import com.hazelcast.utils.PropertyReader;
import com.hazelcast.utils.Repository;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;

import static com.hazelcast.utils.DebugUtils.debugYellow;
import static com.hazelcast.utils.DebugUtils.print;
import static com.hazelcast.utils.DebugUtils.printGreen;
import static com.hazelcast.utils.Repository.fromRepositoryName;
import static java.lang.String.format;
import static java.nio.file.Files.walkFileTree;
import static java.nio.file.Files.write;

public class IdeaConverter {

    public static final String OUTPUT_FILENAME = "idea-coverage.csv";

    private final String repositoryName;

    public IdeaConverter(PropertyReader propertyReader) {
        Repository repository = fromRepositoryName(propertyReader.getGitHubRepository());
        this.repositoryName = repository.getRepositoryName();
    }

    public void run() {
        try {
            int parsedClasses = 0;

            FileFinder finder = new FileFinder("index.html");
            walkFileTree(Paths.get("").toAbsolutePath(), finder);

            Collection<Path> matchedFiles = finder.getMatchedPaths();
            print("Parsing classes from %d report files...", matchedFiles.size());

            StringBuilder sb = new StringBuilder();
            for (Path file : matchedFiles) {
                Elements tableRows = getLastTableRows(file);
                if (!isClassTable(tableRows)) {
                    debugYellow("File does not contain class table: " + file);
                    continue;
                }

                String packageName = file.getName(file.getNameCount() - 2).toString();
                for (Element tableRow : tableRows) {
                    Elements tableColumns = tableRow.getElementsByTag("td");
                    String className = tableColumns.first().getElementsByTag("a").text();
                    String lineCoverageString = tableColumns.last().getElementsByClass("percent").text().trim();
                    double lineCoverage = Double.valueOf(lineCoverageString.substring(0, lineCoverageString.length() - 1));
                    sb.append(format("%s;%s.%s.java;%.1f%n", repositoryName, packageName, className, lineCoverage));
                    parsedClasses++;
                }
            }
            print("Successfully parsed %d classes!", parsedClasses);

            print("Writing data to %s...", OUTPUT_FILENAME);
            write(Paths.get(OUTPUT_FILENAME), sb.toString().getBytes());

            printGreen("Done!");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Elements getLastTableRows(Path file) throws IOException {
        Document doc = Jsoup.parse(file.toFile(), "UTF-8", "");
        Elements tables = doc.getElementsByTag("table");
        return tables.last().getElementsByTag("tr");
    }

    private boolean isClassTable(Elements tableRows) {
        Element firstRow = tableRows.first();
        if (firstRow == null) {
            return false;
        }
        tableRows.remove(firstRow);

        Element firstCell = firstRow.getElementsByTag("th").first();
        String header = firstCell.getElementsByTag("a").text().trim();
        return header.equals("Class");
    }
}
