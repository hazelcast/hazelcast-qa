package com.hazelcast.qasonar.csvmerge;

import com.hazelcast.qasonar.utils.FileFinder;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.hazelcast.qasonar.ideaconverter.IdeaConverter.OUTPUT_FILENAME;
import static com.hazelcast.qasonar.utils.Utils.debug;
import static java.lang.String.format;
import static java.nio.file.Files.readAllLines;
import static java.nio.file.Files.walkFileTree;
import static java.nio.file.Files.write;

public class CsvMerge {

    private final Map<String, Double> ideaCoverage = new HashMap<String, Double>();

    public void run() throws IOException {
        FileFinder finder = new FileFinder("*.csv");
        walkFileTree(Paths.get("").toAbsolutePath(), finder);

        Collection<Path> matchedFiles = finder.getMatchedPaths();
        System.out.println(format("Merging %d CSV files...", matchedFiles.size()));

        for (Path file : matchedFiles) {
            List<String> lines = readAllLines(file);
            for (String line : lines) {
                String[] lineArray = line.split(";");
                String fileName = lineArray[0];
                Double coverage = Double.valueOf(lineArray[1]);
                Double oldCoverage = ideaCoverage.get(fileName);
                if (oldCoverage == null) {
                    ideaCoverage.put(fileName, coverage);
                } else if (coverage > oldCoverage) {
                    debug(format("Replaced coverage %.1f with %.1f for class %s", oldCoverage, coverage, fileName));
                    ideaCoverage.put(fileName, coverage);
                }
            }
        }

        int classCount = ideaCoverage.size();
        System.out.println(format("Merged coverage data for %d classes...", classCount));
        if (classCount == 0) {
            System.out.println("Nothing to store, we're done!");
            return;
        }

        System.out.println("Storing results...");
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Double> coverageEntry : ideaCoverage.entrySet()) {
            sb.append(format("%s;%f%n", coverageEntry.getKey(), coverageEntry.getValue()));
        }
        write(Paths.get(OUTPUT_FILENAME), sb.toString().getBytes());

        System.out.println("Done!");
    }
}
