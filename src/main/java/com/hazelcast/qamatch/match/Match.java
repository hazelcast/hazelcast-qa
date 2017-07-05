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
import com.hazelcast.utils.BufferingOutputHandler;
import com.hazelcast.utils.PropertyReader;
import org.apache.maven.shared.invoker.DefaultInvoker;
import org.apache.maven.shared.invoker.Invoker;
import org.apache.maven.shared.invoker.MavenInvocationException;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import static com.hazelcast.utils.GitUtils.asString;
import static com.hazelcast.utils.GitUtils.cleanupBranches;
import static com.hazelcast.utils.GitUtils.compile;
import static com.hazelcast.utils.GitUtils.createBranch;
import static com.hazelcast.utils.GitUtils.getCommits;
import static com.hazelcast.utils.GitUtils.getGit;
import static com.hazelcast.utils.GitUtils.resetCompileCounters;
import static com.hazelcast.utils.Repository.EE;
import static com.hazelcast.utils.Repository.OS;
import static java.lang.String.format;
import static java.nio.file.Files.newBufferedWriter;
import static java.util.Collections.reverseOrder;

public class Match {

    private static final int MAX_EE_FAILURES_BEFORE_OS_COMMIT_IS_IGNORED = 10;

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
                // forward search OS
                forwardSearchOS(limit);
                if (reverseCompatibilityMap.size() >= limit) {
                    break;
                }

                // forward search EE
                forwardSearchEE(limit);
            }
        } finally {
            cleanupBranches(branchName, gitOS);
            cleanupBranches(branchName, gitEE);

            System.out.println("\n\n===== Results =====\n");
            printAndStoreCompatibleCommits(compatibilityMap, compatibilityPath, false);
            printAndStoreCompatibleCommits(reverseCompatibilityMap, reverseCompatibilityPath, true);
        }
    }

    private void initRepositories() throws IOException, GitAPIException {
        resetCompileCounters();

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
                    // jump to forward search EE
                    break;
                }
            }
            lastCommitOS = currentCommitOS;
            currentCommitOS = createBranch(branchName, gitOS, iteratorOS);
        }
    }

    private void forwardSearchEE(int limit) throws GitAPIException, MavenInvocationException {
        RevCommit lastCommitEE = currentCommitEE;
        while (reverseCompatibilityMap.size() < limit) {
            currentCommitEE = createBranch(branchName, gitEE, iteratorEE);
            if (compile(isVerbose, invoker, outputHandler, gitEE, currentCommitEE, true)) {
                storeCompatibleCommits(currentCommitOS, currentCommitEE, limit);
                // we found a passing EE commit, we can stop here
                break;
            } else {
                failedCommitsEE.add(currentCommitEE);
                if (failedCommitsEE.size() == MAX_EE_FAILURES_BEFORE_OS_COMMIT_IS_IGNORED) {
                    // after too many failures, we ignore this OS commit
                    compatibilityMap.put(currentCommitOS, null);
                    // we have to reset the EE iterator, since we skipped a lot of commits here
                    iteratorEE = commitsEE.iterator();
                    while (currentCommitEE != lastCommitEE) {
                        currentCommitEE = iteratorEE.next();
                    }
                    System.err.printf("Got %d failures, ignore OS %s%nContinue with EE %s%n%n",
                            failedCommitsEE.size(), asString(currentCommitOS), asString(currentCommitEE));
                    failedCommitsEE.clear();
                }
            }
        }
        if (failedCommitsEE.isEmpty()) {
            // jump to forward search OS
            lastCommitOS = currentCommitOS;
            currentCommitOS = createBranch(branchName, gitOS, iteratorOS);
        } else {
            // jump to backward search EE
            backwardSearchEE(limit);
        }
    }

    private void backwardSearchEE(int limit) throws GitAPIException, MavenInvocationException {
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
                System.out.println("Found no matching version for " + asString(failedCommit));
                reverseCompatibilityMap.put(failedCommit, null);
                while (failedCommitsIterator.hasNext()) {
                    failedCommit = failedCommitsIterator.next();
                    System.out.println("Found no matching version for " + asString(failedCommit));
                    reverseCompatibilityMap.put(failedCommit, null);
                }
                System.out.println();
                failedCommitsEE.clear();
                break;
            }
        }
        // jump to forward search OS
        cleanupBranches(branchName, gitOS);
        createBranch(branchName, gitOS, currentCommitOS);
        currentCommitEE = createBranch(branchName, gitEE, iteratorEE);
    }

    private void storeCompatibleCommits(RevCommit commitOS, RevCommit commitEE, int limit) {
        compatibilityMap.put(commitOS, commitEE);
        reverseCompatibilityMap.put(commitEE, commitOS);
        System.out.printf("Found matching versions (%d/%d)%n%n", reverseCompatibilityMap.size(), limit);
    }

    private void printAndStoreCompatibleCommits(Map<RevCommit, RevCommit> map, Path path, boolean isReverseMap) {
        System.out.println(isReverseMap ? "EE -> OS" : "OS -> EE");
        String formatString = isReverseMap ? "EE: %s%nOS: %s%n%n" : "OS: %s%nEE: %s%n%n";
        try {
            try (BufferedWriter writer = newBufferedWriter(path)) {
                for (Map.Entry<RevCommit, RevCommit> entry : map.entrySet()) {
                    RevCommit firstCommit = entry.getKey();
                    RevCommit secondsCommit = entry.getValue();
                    System.out.printf(formatString, asString(firstCommit), asString(secondsCommit));
                    writer.write(firstCommit == null ? "n/a" : firstCommit.getName());
                    writer.write(";");
                    writer.write(secondsCommit == null ? "n/a" : secondsCommit.getName());
                    writer.write("\n");
                }
            }
        } catch (IOException e) {
            System.err.println("Could not store results! " + e.getMessage());
        }
    }
}
