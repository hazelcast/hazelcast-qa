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

package com.hazelcast.qamatch.match;

import com.hazelcast.qamatch.utils.CommandLineOptions;
import com.hazelcast.utils.PropertyReader;
import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.DefaultInvoker;
import org.apache.maven.shared.invoker.InvocationOutputHandler;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.invoker.Invoker;
import org.apache.maven.shared.invoker.MavenInvocationException;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static com.hazelcast.utils.Repository.EE;
import static com.hazelcast.utils.Repository.OS;
import static java.lang.String.format;
import static java.nio.file.Files.newBufferedWriter;
import static java.util.Arrays.asList;
import static java.util.Collections.reverseOrder;

public class Match {

    private static final int SHA_LENGTH = 7;
    private static final int SHORT_MESSAGE_LENGTH = 80;

    private static final AtomicInteger COMPILE_COUNTER_OS = new AtomicInteger();
    private static final AtomicInteger COMPILE_COUNTER_EE = new AtomicInteger();

    private final Map<RevCommit, RevCommit> compatibilityMap = new TreeMap<>(reverseOrder());
    private final Map<RevCommit, RevCommit> reverseCompatibilityMap = new TreeMap<>(reverseOrder());
    private final List<RevCommit> failedCommitsEE = new LinkedList<>();

    private final Path compatibilityPath = Paths.get("os-ee.csv");
    private final Path reverseCompatibilityPath = Paths.get("ee-os.csv");

    private final PropertyReader propertyReader;
    private final CommandLineOptions commandLineOptions;

    private final boolean isVerbose;

    private final String branchName;
    private final BufferingOutputHandler outputHandler;
    private final Invoker invoker;

    private Git gitOS;
    private Git gitEE;

    private List<RevCommit> commitsOS;
    private List<RevCommit> commitsEE;

    private Iterator<RevCommit> iteratorOS;
    private Iterator<RevCommit> iteratorEE;

    private RevCommit currentCommitOS;
    private RevCommit currentCommitEE;
    private RevCommit lastCommitOS;

    public Match(PropertyReader propertyReader, CommandLineOptions commandLineOptions) {
        this.propertyReader = propertyReader;
        this.commandLineOptions = commandLineOptions;

        this.isVerbose = commandLineOptions.isVerbose();

        this.branchName = "matcher-" + UUID.randomUUID();
        this.outputHandler = new BufferingOutputHandler();
        this.invoker = new DefaultInvoker()
                .setOutputHandler(outputHandler)
                .setMavenHome(new File("/usr/share/maven"));
    }

    public void run() throws Exception {
        initRepositories();

        System.out.println(format("Total commits in Hazelcast %s: %d", OS, commitsOS.size()));
        System.out.println(format("Total commits in Hazelcast %s: %d", EE, commitsEE.size()));
        System.out.println();

        try {
            int limit = commandLineOptions.getLimit();
            while (reverseCompatibilityMap.size() < limit) {
                // OS forward search
                forwardSearchOS(limit);
                if (reverseCompatibilityMap.size() >= limit) {
                    break;
                }

                // EE forward search
                forwardSearchEE(limit);
                if (reverseCompatibilityMap.size() >= limit) {
                    break;
                }

                // EE backward search
                backwardSearchEE(limit);
            }
        } finally {
            cleanupBranches(branchName, gitOS);
            cleanupBranches(branchName, gitEE);

            System.out.println("\n\n===== Results =====\n");
            printAndStoreCompatibleVersions(compatibilityMap, compatibilityPath, false);
            printAndStoreCompatibleVersions(reverseCompatibilityMap, reverseCompatibilityPath, true);
        }
    }

    private void initRepositories() throws IOException, GitAPIException {
        COMPILE_COUNTER_OS.set(0);
        COMPILE_COUNTER_EE.set(0);

        gitOS = getGit(propertyReader, OS.getRepositoryName());
        gitEE = getGit(propertyReader, EE.getRepositoryName());

        cleanupBranches(null, gitOS);
        cleanupBranches(null, gitEE);

        commitsOS = getCommits(gitOS);
        commitsEE = getCommits(gitEE);

        iteratorOS = commitsOS.iterator();
        iteratorEE = commitsEE.iterator();

        currentCommitOS = iteratorOS.next();
        currentCommitEE = iteratorEE.next();

        lastCommitOS = currentCommitOS;
        failedCommitsEE.clear();

        createBranch(branchName, gitOS, currentCommitOS);
        createBranch(branchName, gitEE, currentCommitEE);
    }

    private void forwardSearchOS(int limit) throws MavenInvocationException, GitAPIException {
        while (reverseCompatibilityMap.size() < limit) {
            if (compile(isVerbose, invoker, outputHandler, gitOS, currentCommitOS, false)) {
                if (compile(isVerbose, invoker, outputHandler, gitEE, currentCommitEE, true)) {
                    storeCompatibleCommits(currentCommitOS, currentCommitEE, limit);
                } else {
                    // jump to EE forward search
                    break;
                }
            }
            lastCommitOS = currentCommitOS;
            currentCommitOS = createBranch(branchName, gitOS, iteratorOS);
        }
    }

    private void forwardSearchEE(int limit) throws GitAPIException, MavenInvocationException {
        while (reverseCompatibilityMap.size() < limit) {
            currentCommitEE = createBranch(branchName, gitEE, iteratorEE);
            if (compile(isVerbose, invoker, outputHandler, gitEE, currentCommitEE, true)) {
                storeCompatibleCommits(currentCommitOS, currentCommitEE, limit);
                // jump to EE backward search
                break;
            } else {
                failedCommitsEE.add(currentCommitEE);
            }
        }
        if (failedCommitsEE.isEmpty()) {
            // jump to OS forward search
            lastCommitOS = currentCommitOS;
            currentCommitOS = createBranch(branchName, gitOS, iteratorOS);
        }
    }

    private void backwardSearchEE(int limit) throws GitAPIException, MavenInvocationException {
        if (failedCommitsEE.isEmpty()) {
            return;
        }
        cleanupBranches(branchName, gitOS);
        createBranch(branchName, gitOS, lastCommitOS);
        compile(isVerbose, invoker, outputHandler, gitOS, lastCommitOS, false);
        Iterator<RevCommit> failedCommitsIterator = failedCommitsEE.iterator();
        while (failedCommitsIterator.hasNext()) {
            RevCommit failedCommit = createBranch(branchName, gitEE, failedCommitsIterator);
            if (compile(isVerbose, invoker, outputHandler, gitEE, failedCommit, true)) {
                storeCompatibleCommits(lastCommitOS, failedCommit, limit);
                failedCommitsIterator.remove();
            } else {
                System.out.println("Found no matching version for " + toString(failedCommit));
                reverseCompatibilityMap.put(failedCommit, null);
                while (failedCommitsIterator.hasNext()) {
                    failedCommit = failedCommitsIterator.next();
                    System.out.println("Found no matching version for " + toString(failedCommit));
                    reverseCompatibilityMap.put(failedCommit, null);
                }
                System.out.println();
                failedCommitsEE.clear();
                break;
            }
        }
        // jump to OS forward search
        cleanupBranches(branchName, gitOS);
        createBranch(branchName, gitOS, currentCommitOS);
        currentCommitEE = createBranch(branchName, gitEE, iteratorEE);
    }

    private void storeCompatibleCommits(RevCommit commitOS, RevCommit commitEE, int limit) {
        compatibilityMap.put(commitOS, commitEE);
        reverseCompatibilityMap.put(commitEE, commitOS);
        System.out.printf("Found matching versions (%d/%d)%n%n", reverseCompatibilityMap.size(), limit);
    }

    private static Git getGit(PropertyReader propertyReader, String repositoryName) throws IOException {
        Repository repoOS = new FileRepositoryBuilder()
                .setGitDir(new File(propertyReader.getLocalGitRoot() + repositoryName + File.separator + ".git"))
                .readEnvironment()
                .setMustExist(true)
                .build();

        return new Git(repoOS);
    }

    private static List<RevCommit> getCommits(Git git) throws GitAPIException {
        List<RevCommit> list = new ArrayList<>();
        for (RevCommit commit : git.log().call()) {
            list.add(commit);
        }
        return list;
    }

    private static void cleanupBranches(String branchName, Git git) throws GitAPIException {
        git.checkout()
                .setName("master")
                .call();

        if (branchName != null) {
            git.branchDelete()
                    .setBranchNames(branchName)
                    .setForce(true)
                    .call();
        }
    }

    private static void createBranch(String branchName, Git git, RevCommit commit) throws GitAPIException {
        git.checkout()
                .setCreateBranch(true)
                .setName(branchName)
                .setStartPoint(commit)
                .call();
    }

    private static RevCommit createBranch(String branchName, Git git, Iterator<RevCommit> iterator) throws GitAPIException {
        cleanupBranches(branchName, git);
        RevCommit commit = iterator.next();
        createBranch(branchName, git, commit);
        return commit;
    }

    private static boolean compile(boolean isVerbose, Invoker invoker, BufferingOutputHandler outputHandler, Git git,
                                   RevCommit commit, boolean isEE) throws MavenInvocationException {
        String label = isEE ? "EE" : "OS";
        int counter = isEE ? COMPILE_COUNTER_EE.incrementAndGet() : COMPILE_COUNTER_OS.incrementAndGet();
        System.out.printf("[%s] [%3d] Compiling %s... ", label, counter, toString(commit));
        File projectRoot = git.getRepository().getDirectory().getParentFile();

        InvocationRequest request = new DefaultInvocationRequest()
                .setBatchMode(true)
                .setBaseDirectory(projectRoot)
                .setPomFile(new File(projectRoot, "pom.xml"))
                .setGoals(asList("clean", "install", "-DskipTests"));
        InvocationResult result = invoker.execute(request);

        if (isVerbose) {
            outputHandler.printErrors();
        }
        outputHandler.clear();

        int exitCode = result.getExitCode();
        System.out.println(exitCode == 0 ? "SUCCESS" : "FAILURE");
        return exitCode == 0;
    }

    private static String toString(RevCommit commit) {
        if (commit == null) {
            return "null";
        }
        String sha = commit.getName().substring(0, SHA_LENGTH);
        String shortMessage = commit.getShortMessage();
        if (shortMessage.length() > SHORT_MESSAGE_LENGTH) {
            shortMessage = shortMessage.substring(0, SHORT_MESSAGE_LENGTH) + "...";
        }
        String author = commit.getAuthorIdent().getName();
        return format("%s (%s): %s [%s]", sha, commit.getCommitTime(), shortMessage, author);
    }

    private static void printAndStoreCompatibleVersions(Map<RevCommit, RevCommit> map, Path path, boolean isReverseMap)
            throws IOException {
        System.out.println(isReverseMap ? "EE -> OS" : "OS -> EE");
        String formatString = isReverseMap ? "EE: %s%nOS: %s%n%n" : "OS: %s%nEE: %s%n%n";
        for (Map.Entry<RevCommit, RevCommit> entry : map.entrySet()) {
            RevCommit firstCommit = entry.getKey();
            RevCommit secondsCommit = entry.getValue();
            System.out.printf(formatString, toString(firstCommit), toString(secondsCommit));
            try (BufferedWriter writer = newBufferedWriter(path)) {
                writer.write(firstCommit == null ? "n/a" : firstCommit.getName());
                writer.write(";");
                writer.write(secondsCommit == null ? "n/a" : secondsCommit.getName());
                writer.write("\n");
            }
        }
    }

    private static class BufferingOutputHandler implements InvocationOutputHandler {

        private final List<String> lines = new LinkedList<>();

        @Override
        public void consumeLine(String line) {
            lines.add(line);
        }

        void printErrors() {
            for (String line : lines) {
                if (line.contains("ERROR")) {
                    System.err.println(line);
                }
            }
        }

        void clear() {
            lines.clear();
        }
    }
}
