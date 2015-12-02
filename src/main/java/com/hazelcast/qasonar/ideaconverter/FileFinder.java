package com.hazelcast.qasonar.ideaconverter;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static java.nio.file.FileVisitResult.CONTINUE;

class FileFinder extends SimpleFileVisitor<Path> {

    private final PathMatcher matcher;
    private List<Path> matchedPaths = new ArrayList<Path>();

    FileFinder(String pattern) {
        matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
    }

    // compares the glob pattern against the file or directory name
    void match(Path file) {
        Path name = file.getFileName();
        if (name != null && matcher.matches(name)) {
            matchedPaths.add(file);
        }
    }

    // invoke the pattern matching method on each file
    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
        match(file);
        return CONTINUE;
    }

    // invoke the pattern matching method on each directory
    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
        match(dir);
        return CONTINUE;
    }

    @Override
    public FileVisitResult visitFileFailed(Path file, IOException exc) {
        exc.printStackTrace();
        return CONTINUE;
    }

    public Collection<Path> getMatchedPaths() {
        return matchedPaths;
    }
}
