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

package com.hazelcast.utils;

import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.invoker.Invoker;
import org.apache.maven.shared.invoker.MavenInvocationException;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.hazelcast.utils.DebugUtils.isDebug;
import static com.hazelcast.utils.DebugUtils.printGreen;
import static com.hazelcast.utils.DebugUtils.printRed;
import static java.lang.String.format;
import static java.util.Arrays.asList;

public final class GitUtils {

    private static final int SHA_LENGTH = 7;
    private static final int SHORT_MESSAGE_LENGTH = 80;

    private static final AtomicInteger COMPILE_COUNTER_OS = new AtomicInteger();
    private static final AtomicInteger COMPILE_COUNTER_EE = new AtomicInteger();

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)
            .withZone(ZoneId.systemDefault());

    private GitUtils() {
    }

    public static void resetCompileCounters() {
        COMPILE_COUNTER_OS.set(0);
        COMPILE_COUNTER_EE.set(0);
    }

    public static Git getGit(PropertyReader propertyReader, String repositoryName) throws IOException {
        Repository repoOS = new FileRepositoryBuilder()
                .setGitDir(new File(propertyReader.getLocalGitRoot() + repositoryName + File.separator + ".git"))
                .readEnvironment()
                .setMustExist(true)
                .build();

        return new Git(repoOS);
    }

    public static void cleanupBranch(String branchName, Git git) throws GitAPIException {
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

    public static void createBranch(String branchName, Git git, RevCommit commit) throws GitAPIException {
        git.checkout()
                .setCreateBranch(true)
                .setName(branchName)
                .setStartPoint(commit)
                .call();
    }

    public static void checkout(String branchName, Git git, RevCommit commit) throws GitAPIException {
        cleanupBranch(branchName, git);
        createBranch(branchName, git, commit);
    }

    public static RevCommit getCommit(Repository repo, RevWalk revWalk, String name) throws IOException {
        ObjectId objectId = repo.resolve(name);
        return revWalk.parseCommit(objectId);
    }

    public static RevCommit getFirstParent(RevCommit commit, RevWalk walk) {
        try {
            RevCommit[] parents = commit.getParents();
            if (parents.length > 0) {
                return walk.parseCommit(parents[0]);
            }
        } catch (IOException ignored) {
        }
        return null;
    }

    public static boolean compile(Invoker invoker, BufferingOutputHandler outputHandler, Git git, RevCommit commit,
                                  boolean isEE) throws MavenInvocationException {
        String label = isEE ? "EE" : "OS";
        int counter = isEE ? COMPILE_COUNTER_EE.incrementAndGet() : COMPILE_COUNTER_OS.incrementAndGet();
        System.out.printf("[%s] [%3d] Compiling %s... ", label, counter, asString(commit));
        File projectRoot = git.getRepository().getDirectory().getParentFile();

        InvocationRequest request = new DefaultInvocationRequest()
                .setBatchMode(true)
                .setBaseDirectory(projectRoot)
                .setPomFile(new File(projectRoot, "pom.xml"))
                .setGoals(asList("clean", "install", "-DskipTests"));

        long started = System.nanoTime();
        InvocationResult result = invoker.execute(request);
        long elapsedSeconds = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - started);

        if (isDebug()) {
            outputHandler.printErrors();
        }
        outputHandler.clear();

        int exitCode = result.getExitCode();
        if (exitCode == 0) {
            printGreen("SUCCESS (%d seconds)", elapsedSeconds);
        } else {
            printRed("FAILURE (%d seconds)", elapsedSeconds);
        }
        return exitCode == 0;
    }

    public static String asString(RevCommit commit) {
        if (commit == null) {
            return "null";
        }
        String sha = commit.getName().substring(0, SHA_LENGTH);
        String time = DATE_FORMATTER.format(Instant.ofEpochSecond(commit.getCommitTime()));
        String shortMessage = commit.getShortMessage();
        if (shortMessage.length() > SHORT_MESSAGE_LENGTH) {
            shortMessage = shortMessage.substring(0, SHORT_MESSAGE_LENGTH) + "...";
        }
        String author = commit.getAuthorIdent().getName();
        return format("%s (%s): %s [%s]", sha, time, shortMessage, author);
    }
}
