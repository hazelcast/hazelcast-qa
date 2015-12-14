package com.hazelcast.qasonar.outputMerge;

import com.hazelcast.qasonar.utils.FileFinder;
import com.hazelcast.qasonar.utils.PropertyReader;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.hazelcast.qasonar.utils.Utils.writeToFile;
import static java.lang.String.format;
import static java.nio.file.Files.readAllLines;
import static java.nio.file.Files.walkFileTree;

public class OutputMerge {

    enum Repository {
        OS("Open Source"),
        EE("Enterprise"),
        MC("MC");

        String description;

        Repository(String description) {
            this.description = description;
        }

        @Override
        public String toString() {
            return description;
        }
    }

    private final Map<Repository, String> content = new HashMap<Repository, String>();

    private final PropertyReader propertyReader;

    public OutputMerge(PropertyReader propertyReader) {
        this.propertyReader = propertyReader;
    }

    public void run() throws IOException {
        String outputFile = propertyReader.getOutputFile();

        FileFinder finder = new FileFinder(outputFile + "*.txt");
        walkFileTree(Paths.get("").toAbsolutePath(), finder);

        Collection<Path> matchedFiles = finder.getMatchedPaths();
        if (matchedFiles.size() == 0) {
            System.err.println("No files found!");
            return;
        }

        System.out.println(format("Merging %d txt files...", matchedFiles.size()));
        StringBuilder sb = new StringBuilder();
        for (Path file : matchedFiles) {
            String fileName = file.getFileName().toString().toLowerCase();
            String baseName = fileName.substring(0, fileName.lastIndexOf('.'));

            if (baseName.equals(outputFile)) {
                System.err.println("Filename equals the output filename: " + outputFile);
                return;
            }

            System.out.println("Parsing " + fileName + "...");

            Repository repository;
            if (baseName.endsWith("-opensource") || baseName.endsWith("-os")) {
                repository = Repository.OS;
            } else if (baseName.endsWith("-enterprise") || baseName.endsWith("-ee")) {
                repository = Repository.EE;
            } else if (baseName.endsWith("-mc")) {
                repository = Repository.MC;
            } else {
                System.err.println("Filename must contain a project postfix: " + fileName);
                return;
            }

            List<String> lines = readAllLines(file);
            for (String line : lines) {
                sb.append(line).append("\n");
            }
            content.put(repository, sb.toString());
            sb.setLength(0);
        }

        System.out.println("Creating new output file...");
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

        System.out.println("Done!");
    }
}
