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

import com.google.gson.Gson;
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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import static com.hazelcast.utils.Repository.EE;
import static com.hazelcast.utils.Repository.OS;
import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.Collections.reverseOrder;

public class Match {

    private static final int SHA_LENGTH = 7;
    private static final int SHORT_MESSAGE_LENGTH = 80;

    private final Map<RevCommit, RevCommit> compatibilityMap = new TreeMap<>(reverseOrder());
    private final Map<RevCommit, RevCommit> reverseCompatibilityMap = new TreeMap<>(reverseOrder());

    private final PropertyReader propertyReader;
    private final CommandLineOptions commandLineOptions;

    private final boolean isVerbose;

    private final String branchName;
    private final BufferingOutputHandler outputHandler;
    private final Invoker invoker;

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
        Git gitOS = getGit(propertyReader, OS.getRepositoryName());
        Git gitEE = getGit(propertyReader, EE.getRepositoryName());

        cleanupBranches(null, gitOS);
        cleanupBranches(null, gitEE);

        List<RevCommit> commitsOS = getCommits(gitOS);
        List<RevCommit> commitsEE = getCommits(gitEE);

        Iterator<RevCommit> iteratorOS = commitsOS.iterator();
        Iterator<RevCommit> iteratorEE = commitsEE.iterator();

        RevCommit commitOS = iteratorOS.next();
        RevCommit commitEE = iteratorEE.next();

        RevCommit lastCommitOS = commitOS;
        List<RevCommit> failedCommitsEE = new LinkedList<>();

        System.out.println(format("Total commits in Hazelcast %s: %d", OS, commitsOS.size()));
        System.out.println(format("Total commits in Hazelcast %s: %d", EE, commitsEE.size()));
        System.out.println();

        createBranch(branchName, gitOS, commitOS);
        createBranch(branchName, gitEE, commitEE);

        try {
            int counter = 0;
            int limit = commandLineOptions.getCommitLimit();
            while (counter++ < limit) {
                // OS forward search
                while (counter++ < limit) {
                    if (compile(isVerbose, invoker, outputHandler, "OS", gitOS, commitOS)) {
                        if (compile(isVerbose, invoker, outputHandler, "EE", gitEE, commitEE)) {
                            System.out.println("Found matching versions!\n");
                            storeCompatibleCommits(commitOS, commitEE);
                        } else {
                            // jump to EE forward search
                            break;
                        }
                    }
                    lastCommitOS = commitOS;
                    commitOS = createBranch(branchName, gitOS, iteratorOS);
                }
                if (counter >= limit) {
                    break;
                }

                // EE forward search
                while (counter++ < limit) {
                    commitEE = createBranch(branchName, gitEE, iteratorEE);
                    if (compile(isVerbose, invoker, outputHandler, "EE", gitEE, commitEE)) {
                        System.out.println("Found matching versions!\n");
                        storeCompatibleCommits(commitOS, commitEE);
                        // jump to EE backward search
                        break;
                    } else {
                        failedCommitsEE.add(commitEE);
                    }
                }
                if (counter >= limit) {
                    break;
                }
                if (failedCommitsEE.isEmpty()) {
                    // jump to OS forward search
                    lastCommitOS = commitOS;
                    commitOS = createBranch(branchName, gitOS, iteratorOS);
                    continue;
                }

                // EE backward search
                cleanupBranches(branchName, gitOS);
                createBranch(branchName, gitOS, lastCommitOS);
                compile(isVerbose, invoker, outputHandler, "OS", gitOS, lastCommitOS);
                Iterator<RevCommit> failedCommitsIterator = failedCommitsEE.iterator();
                while (failedCommitsIterator.hasNext()) {
                    commitEE = createBranch(branchName, gitEE, failedCommitsIterator);
                    if (compile(isVerbose, invoker, outputHandler, "EE", gitEE, commitEE)) {
                        System.out.println("Found matching versions!\n");
                        storeCompatibleCommits(lastCommitOS, commitEE);
                        failedCommitsIterator.remove();
                    } else {
                        for (RevCommit failedCommit : failedCommitsEE) {
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
                createBranch(branchName, gitOS, commitOS);
            }
        } finally {
            cleanupBranches(branchName, gitOS);
            cleanupBranches(branchName, gitEE);

            System.out.println("\n\n===== Results =====\n");
            printCompatibleVersions(compatibilityMap, false);
            printCompatibleVersions(reverseCompatibilityMap, true);
        }
    }

    private void storeCompatibleCommits(RevCommit commitOS, RevCommit commitEE) {
        compatibilityMap.put(commitOS, commitEE);
        reverseCompatibilityMap.put(commitEE, commitOS);
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

    private static boolean compile(boolean isVerbose, Invoker invoker, BufferingOutputHandler outputHandler, String label,
                                   Git git, RevCommit commit) throws MavenInvocationException {
        System.out.print("Compiling " + label + ": " + toString(commit) + "... ");
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

    private static void printCompatibleVersions(Map<RevCommit, RevCommit> map, boolean isReverseMap) {
        System.out.println(isReverseMap ? "EE -> OS" : "OS -> EE");
        String formatString = isReverseMap ? "EES: %s%nOS: %s%n%n" : "OS: %s%nEE: %s%n%n";
        Map<String, String> shaMap = new TreeMap<>();
        for (Map.Entry<RevCommit, RevCommit> entry : map.entrySet()) {
            System.out.printf(formatString, toString(entry.getKey()), toString(entry.getValue()));
            shaMap.put(entry.getKey().getName(), entry.getValue().getName());
        }
        Gson gson = new Gson();
        gson.toJson(shaMap);
        System.out.println(gson.toString());
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
