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

package com.hazelcast.hzblame.blame;

import com.hazelcast.hzblame.utils.CommandLineOptions;
import com.hazelcast.utils.BufferingOutputHandler;
import com.hazelcast.utils.PropertyReader;
import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.DefaultInvoker;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.invoker.Invoker;
import org.apache.maven.shared.invoker.MavenInvocationException;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.hazelcast.utils.CsvUtils.readCSV;
import static com.hazelcast.utils.GitUtils.cleanupBranch;
import static com.hazelcast.utils.GitUtils.compile;
import static com.hazelcast.utils.GitUtils.createBranch;
import static com.hazelcast.utils.GitUtils.getGit;
import static com.hazelcast.utils.GitUtils.resetCompileCounters;
import static com.hazelcast.utils.Repository.EE;
import static com.hazelcast.utils.Repository.OS;

public class Blame {

    private final Path commitPath = Paths.get("ee-os.csv");
    private final Map<String, String> commits = new HashMap<>();

    private final String branchName;
    private final BufferingOutputHandler outputHandler;
    private final Invoker invoker;

    private final PropertyReader propertyReader;
    private final CommandLineOptions commandLineOptions;

    private final boolean isVerbose;
    private final boolean isEE;

    private Git gitOS;
    private Git gitEE;

    private RevWalk walkOS;
    private RevWalk walkEE;

    private RevCommit currentCommitOS;
    private RevCommit currentCommitEE;

    public Blame(PropertyReader propertyReader, CommandLineOptions commandLineOptions) {
        this.propertyReader = propertyReader;
        this.commandLineOptions = commandLineOptions;

        this.isVerbose = commandLineOptions.isVerbose();
        this.isEE = commandLineOptions.isEE();

        this.branchName = "blame-" + UUID.randomUUID();
        this.outputHandler = new BufferingOutputHandler();
        this.invoker = new DefaultInvoker()
                .setOutputHandler(outputHandler)
                .setMavenHome(new File("/usr/share/maven"));
    }

    public void run() throws Exception {
        initRepositories();
        File projectRoot = getProjectRoot();
        List<String> goals = getTestRunGoals();
        if (isVerbose) {
            System.out.println("Goals: " + goals);
        }

        if (isEE) {
            readCSV(commitPath, commits);
            compile(isVerbose, invoker, outputHandler, gitOS, currentCommitOS, false);
        }

        runTest(projectRoot, goals);

        cleanupBranch(branchName, gitOS);
        cleanupBranch(branchName, gitEE);
    }

    private void initRepositories() throws IOException, GitAPIException {
        gitOS = getGit(propertyReader, OS.getRepositoryName());
        gitEE = getGit(propertyReader, EE.getRepositoryName());

        Repository repoOS = gitOS.getRepository();
        Repository repoEE = gitEE.getRepository();

        walkOS = new RevWalk(repoOS);
        walkEE = new RevWalk(repoEE);

        Ref headOS = repoOS.exactRef(Constants.HEAD);
        Ref headEE = repoEE.exactRef(Constants.HEAD);

        currentCommitOS = walkOS.parseCommit(headOS.getObjectId());
        currentCommitEE = walkEE.parseCommit(headEE.getObjectId());

        cleanupBranch(null, gitOS);
        cleanupBranch(null, gitEE);

        createBranch(branchName, gitOS, currentCommitOS);
        createBranch(branchName, gitEE, currentCommitEE);

        resetCompileCounters();
    }

    private File getProjectRoot() {
        Git git = isEE ? gitEE : gitOS;
        return git.getRepository().getDirectory().getParentFile();
    }

    private List<String> getTestRunGoals() {
        String testModule = isEE ? "hazelcast-enterprise" : "hazelcast";
        if (commandLineOptions.hasTestModule()) {
            testModule = commandLineOptions.getTestModule();
        }
        String testMethod = commandLineOptions.hasTestMethod() ? "#" + commandLineOptions.getTestMethod() : "";

        List<String> goals = new LinkedList<>();
        goals.add("clean");
        goals.add("test");
        if (commandLineOptions.hasMavenProfile()) {
            goals.add("-P" + commandLineOptions.getMavenProfile());
        }
        goals.add("-pl " + testModule);
        goals.add("-Dtest=" + commandLineOptions.getTestClass() + testMethod);
        return goals;
    }

    private boolean runTest(File projectRoot, List<String> goals) throws MavenInvocationException {
        InvocationRequest request = new DefaultInvocationRequest()
                .setBatchMode(true)
                .setBaseDirectory(projectRoot)
                .setPomFile(new File(projectRoot, "pom.xml"))
                .setGoals(goals);
        InvocationResult result = invoker.execute(request);

        if (isVerbose) {
            outputHandler.printAll();
        }
        if (outputHandler.contains("No tests were executed!")) {
            System.err.println("Test could not be found, please check if you have specified the correct module and profile!");
            return false;
        }
        outputHandler.clear();

        int exitCode = result.getExitCode();
        System.out.println(exitCode == 0 ? "SUCCESS" : "FAILURE");
        return exitCode == 0;
    }
}
