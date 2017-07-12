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

package com.hazelcast.hzmatch.match;

import com.hazelcast.common.AbstractGitClass;
import com.hazelcast.hzmatch.utils.CommandLineOptions;
import com.hazelcast.utils.BufferingOutputHandler;
import com.hazelcast.utils.PropertyReader;
import org.apache.maven.shared.invoker.DefaultInvoker;
import org.apache.maven.shared.invoker.Invoker;
import org.apache.maven.shared.invoker.MavenInvocationException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.BufferedWriter;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static com.hazelcast.utils.CsvUtils.NOT_AVAILABLE;
import static com.hazelcast.utils.DebugUtils.debug;
import static com.hazelcast.utils.DebugUtils.print;
import static com.hazelcast.utils.DebugUtils.printRed;
import static com.hazelcast.utils.DebugUtils.printYellow;
import static com.hazelcast.utils.GitUtils.asString;
import static com.hazelcast.utils.GitUtils.checkout;
import static com.hazelcast.utils.GitUtils.compile;
import static com.hazelcast.utils.GitUtils.getFirstParent;
import static java.nio.file.Files.newBufferedWriter;
import static java.util.Collections.reverseOrder;

public class Match extends AbstractGitClass {

    private static final int MAX_EE_FAILURES_BEFORE_OS_COMMIT_IS_IGNORED = 10;

    private final Map<RevCommit, RevCommit> compatibilityMap = new TreeMap<>(reverseOrder());
    private final Map<RevCommit, RevCommit> reverseCompatibilityMap = new TreeMap<>(reverseOrder());
    private final List<RevCommit> failedCommitsEE = new LinkedList<>();

    private final Path compatibilityPath = Paths.get("os-ee.csv");
    private final Path reverseCompatibilityPath = Paths.get("ee-os.csv");

    private final CommandLineOptions commandLineOptions;

    private final BufferingOutputHandler outputHandler;
    private final Invoker invoker;

    public Match(PropertyReader propertyReader, CommandLineOptions commandLineOptions) {
        super("matcher", propertyReader);
        this.commandLineOptions = commandLineOptions;

        this.outputHandler = new BufferingOutputHandler();
        this.invoker = new DefaultInvoker()
                .setOutputHandler(outputHandler)
                .setMavenHome(new File("/usr/share/maven"));
    }

    @Override
    public void doRun() throws Exception {
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
    }

    @Override
    protected void doCleanup() throws Exception {
        debug("\n\n===== Results =====\n");
        storeCompatibleCommits(compatibilityMap, compatibilityPath, false);
        storeCompatibleCommits(reverseCompatibilityMap, reverseCompatibilityPath, true);
    }

    private void forwardSearchOS(int limit) throws MavenInvocationException, GitAPIException {
        while (reverseCompatibilityMap.size() < limit) {
            if (compile(invoker, outputHandler, gitOS, currentCommitOS, false, false)) {
                if (compile(invoker, outputHandler, gitEE, currentCommitEE, false, true)) {
                    storeCompatibleCommits(currentCommitOS, currentCommitEE, limit);
                } else {
                    // jump to forward search EE
                    break;
                }
            }
            lastCommitOS = currentCommitOS;
            currentCommitOS = getFirstParent(currentCommitOS, walkOS);
            checkout(branchName, gitOS, currentCommitOS);
        }
    }

    private void forwardSearchEE(int limit) throws GitAPIException, MavenInvocationException {
        RevCommit lastCommitEE = currentCommitEE;
        while (reverseCompatibilityMap.size() < limit) {
            currentCommitEE = getFirstParent(currentCommitEE, walkEE);
            checkout(branchName, gitEE, currentCommitEE);
            if (compile(invoker, outputHandler, gitEE, currentCommitEE, false, true)) {
                storeCompatibleCommits(currentCommitOS, currentCommitEE, limit);
                // we found a passing EE commit, we can stop here
                break;
            } else {
                failedCommitsEE.add(currentCommitEE);
                if (failedCommitsEE.size() == MAX_EE_FAILURES_BEFORE_OS_COMMIT_IS_IGNORED) {
                    // after too many failures, we ignore this OS commit
                    compatibilityMap.put(currentCommitOS, null);
                    // we have to reset the currentCommitEE, since we skipped a lot of commits here
                    currentCommitEE = lastCommitEE;
                    printRed("Got %d failures, ignoring OS %s%nContinue with EE %s%n",
                            failedCommitsEE.size(), asString(currentCommitOS), asString(currentCommitEE));
                    failedCommitsEE.clear();
                    break;
                }
            }
        }
        if (failedCommitsEE.isEmpty()) {
            // jump to forward search OS
            lastCommitOS = currentCommitOS;
            currentCommitOS = getFirstParent(currentCommitOS, walkOS);
            checkout(branchName, gitOS, currentCommitOS);
        } else {
            // jump to backward search EE
            backwardSearchEE(limit);
        }
    }

    private void backwardSearchEE(int limit) throws GitAPIException, MavenInvocationException {
        checkout(branchName, gitOS, lastCommitOS);
        compile(invoker, outputHandler, gitOS, lastCommitOS, false, false);
        Iterator<RevCommit> failedCommitsIterator = failedCommitsEE.iterator();
        while (failedCommitsIterator.hasNext()) {
            RevCommit failedCommit = failedCommitsIterator.next();
            checkout(branchName, gitEE, failedCommit);
            if (compile(invoker, outputHandler, gitEE, failedCommit, false, true)) {
                storeCompatibleCommits(lastCommitOS, failedCommit, limit);
                failedCommitsIterator.remove();
            } else {
                printYellow("Found no matching version for %s", asString(failedCommit));
                reverseCompatibilityMap.put(failedCommit, null);
                while (failedCommitsIterator.hasNext()) {
                    failedCommit = failedCommitsIterator.next();
                    printYellow("Found no matching version for %s", asString(failedCommit));
                    reverseCompatibilityMap.put(failedCommit, null);
                }
                System.out.println();
                failedCommitsEE.clear();
                break;
            }
        }
        // jump to forward search OS
        checkout(branchName, gitOS, currentCommitOS);
        currentCommitEE = getFirstParent(currentCommitEE, walkEE);
        checkout(branchName, gitEE, currentCommitEE);
    }

    private void storeCompatibleCommits(RevCommit commitOS, RevCommit commitEE, int limit) {
        compatibilityMap.put(commitOS, commitEE);
        reverseCompatibilityMap.put(commitEE, commitOS);
        print("Found matching versions (%d/%d)%n", reverseCompatibilityMap.size(), limit);
    }

    private void storeCompatibleCommits(Map<RevCommit, RevCommit> map, Path path, boolean isReverseMap) throws Exception {
        debug(isReverseMap ? "EE -> OS" : "OS -> EE");
        String formatString = isReverseMap ? "EE: %s%nOS: %s%n" : "OS: %s%nEE: %s%n";
        try (BufferedWriter writer = newBufferedWriter(path)) {
            for (Map.Entry<RevCommit, RevCommit> entry : map.entrySet()) {
                RevCommit firstCommit = entry.getKey();
                RevCommit secondsCommit = entry.getValue();
                writer.write(firstCommit == null ? NOT_AVAILABLE : firstCommit.getName());
                writer.write(";");
                writer.write(secondsCommit == null ? NOT_AVAILABLE : secondsCommit.getName());
                writer.write("\n");
                debug(formatString, asString(firstCommit), asString(secondsCommit));
            }
        }
    }
}
