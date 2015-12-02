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

package com.hazelcast.qasonar.ideaconverter;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;

import static java.lang.String.format;
import static java.nio.file.Files.walkFileTree;

public class IdeaConverter {

    public void run() {
        try {
            FileFinder finder = new FileFinder("index.html");
            walkFileTree(Paths.get("").toAbsolutePath(), finder);

            Collection<Path> matchedFiles = finder.getMatchedPaths();
            for (Path file : matchedFiles) {
                Elements tableRows = getLastTableRows(file);
                if (!isClassTable(tableRows)) {
                    //System.out.println("File does not contain class table: " + file);
                    continue;
                }

                String packageName = file.getName(file.getNameCount() - 2).toString();
                for (Element tableRow : tableRows) {
                    Elements tableColumns = tableRow.getElementsByTag("td");
                    String className = tableColumns.first().getElementsByTag("a").text();
                    String lineCoverageString = tableColumns.last().getElementsByClass("percent").text().trim();
                    double lineCoverage = Double.valueOf(lineCoverageString.substring(0, lineCoverageString.length() - 1));
                    System.out.println(format("%s.%s.java -> %.1f%%", packageName, className, lineCoverage));
                }
            }
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
