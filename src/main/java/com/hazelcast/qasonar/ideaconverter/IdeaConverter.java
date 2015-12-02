package com.hazelcast.qasonar.ideaconverter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;

public class IdeaConverter {

    public void run() {
        try {
            // create a FileFinder instance with a naming pattern
            FileFinder finder = new FileFinder("index.html");

            // pass the initial directory and the finder to the file tree walker
            Files.walkFileTree(Paths.get("").toAbsolutePath(), finder);

            // get the matched paths
            Collection<Path> matchedFiles = finder.getMatchedPaths();

            // print the matched paths
            for (Path path : matchedFiles) {
                System.out.println(path.toAbsolutePath().toString());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
