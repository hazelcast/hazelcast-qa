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
import java.util.List;

import static com.hazelcast.utils.Repository.EE;
import static com.hazelcast.utils.Repository.OS;
import static java.lang.String.format;
import static java.util.Arrays.asList;

public class Match {

    private final PropertyReader propertyReader;
    private final CommandLineOptions commandLineOptions;

    private final Invoker invoker;

    public Match(PropertyReader propertyReader, CommandLineOptions commandLineOptions) {
        this.propertyReader = propertyReader;
        this.commandLineOptions = commandLineOptions;

        this.invoker = new DefaultInvoker()
                .setMavenHome(new File("/usr/share/maven"));
    }

    public void run() throws Exception {
        Git gitOS = getGit(propertyReader, OS.getRepositoryName());
        Git gitEE = getGit(propertyReader, EE.getRepositoryName());

        List<RevCommit> commitsOS = getCommits(gitOS);
        List<RevCommit> commitsEE = getCommits(gitEE);

        System.out.println(format("Total commits in Hazelcast %s: %d", OS, commitsOS.size()));
        System.out.println("Last commit: " + toString(commitsOS.get(0)));
        System.out.println();

        System.out.println(format("Total commits in Hazelcast %s: %d", EE, commitsEE.size()));
        System.out.println("Last commit: " + toString(commitsEE.get(0)));
        System.out.println();

        InvocationRequest requestOS = getInvocationRequest(gitOS);
        InvocationRequest requestEE = getInvocationRequest(gitEE);

        int exitCodeOS = compile(invoker, requestOS);
        int exitCodeEE = compile(invoker, requestEE);

        if (exitCodeOS != 0) {
            System.err.println("Compilation error in Hazelcast OS!");
        }
        if (exitCodeEE != 0) {
            System.err.println("Compilation error in Hazelcast EE!");
        }
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

    private static String toString(RevCommit commit) {
        String sha = commit.getName().substring(0, 7);
        String author = commit.getAuthorIdent().getName();
        return format("%s (%s): %s [%s]", sha, commit.getCommitTime(), commit.getShortMessage(), author);
    }

    private static InvocationRequest getInvocationRequest(Git git) {
        File projectRoot = git.getRepository().getDirectory().getParentFile();
        return new DefaultInvocationRequest()
                .setPomFile(new File(projectRoot, "pom.xml"))
                .setGoals(asList("clean", "install", "-DskipTests"));
    }

    private static int compile(Invoker invoker, InvocationRequest request) throws MavenInvocationException {
        InvocationResult result = invoker.execute(request);
        int exitCode = result.getExitCode();

        System.out.println();
        System.out.println("Return code: " + exitCode);

        return exitCode;
    }
}
