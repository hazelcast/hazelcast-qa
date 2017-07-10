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

import com.hazelcast.common.AbstractGitClass;
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

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.hazelcast.utils.CsvUtils.NOT_AVAILABLE;
import static com.hazelcast.utils.CsvUtils.readCSV;
import static com.hazelcast.utils.DebugUtils.debug;
import static com.hazelcast.utils.DebugUtils.isDebug;
import static com.hazelcast.utils.DebugUtils.print;
import static com.hazelcast.utils.DebugUtils.printGreen;
import static com.hazelcast.utils.DebugUtils.printRed;
import static com.hazelcast.utils.DebugUtils.printYellow;
import static com.hazelcast.utils.GitUtils.checkout;
import static com.hazelcast.utils.GitUtils.cleanupBranch;
import static com.hazelcast.utils.GitUtils.compile;
import static com.hazelcast.utils.GitUtils.getCommit;
import static com.hazelcast.utils.GitUtils.getFirstParent;
import static java.lang.String.format;
import static org.eclipse.jgit.lib.Constants.HEAD;

public class Blame extends AbstractGitClass {

    private static final int MAX_EE_COMMIT_SKIPS = 10;

    private final Path commitPath = Paths.get("ee-os.csv");
    private final Map<String, String> commits = new HashMap<>();

    private final String branchName;
    private final BufferingOutputHandler outputHandler;
    private final Invoker invoker;

    private final CommandLineOptions commandLineOptions;

    private final boolean isEE;

    private String currentNameOS;
    private String currentNameEE;

    private int counter;

    public Blame(PropertyReader propertyReader, CommandLineOptions commandLineOptions) {
        super(propertyReader);
        this.commandLineOptions = commandLineOptions;

        this.isEE = commandLineOptions.isEE();

        this.branchName = "blame-" + UUID.randomUUID();
        this.outputHandler = new BufferingOutputHandler();
        this.invoker = new DefaultInvoker()
                .setOutputHandler(outputHandler)
                .setMavenHome(new File("/usr/share/maven"));
    }

    public void run() throws Exception {
        initRepositories(branchName);

        File projectRoot = getProjectRoot();
        List<String> goals = getMavenGoals();
        debug("Maven goals: %s", goals);
        if (isEE) {
            readCSV(commitPath, commits);
        }

        try {
            while (setNextCommit()) {
                checkout(branchName, gitOS, currentCommitOS);
                compile(invoker, outputHandler, gitOS, currentCommitOS, false);
                if (isEE) {
                    checkout(branchName, gitEE, currentCommitEE);
                    compile(invoker, outputHandler, gitEE, currentCommitEE, true);
                }
                if (executeTest(projectRoot, goals)) {
                    printGreen("Test passed without errors!");
                    break;
                }
                System.out.println();
            }
        } finally {
            cleanupBranch(branchName, gitOS);
            cleanupBranch(branchName, gitEE);
        }
    }

    private File getProjectRoot() {
        Git git = isEE ? gitEE : gitOS;
        return git.getRepository().getDirectory().getParentFile();
    }

    private List<String> getMavenGoals() {
        String testModule = isEE ? "hazelcast-enterprise" : "hazelcast";
        if (commandLineOptions.hasTestModule()) {
            testModule = commandLineOptions.getTestModule();
        }
        String testMethod = commandLineOptions.hasTestMethod() ? "#" + commandLineOptions.getTestMethod() : "";

        List<String> goals = new LinkedList<>();
        goals.add("test");
        if (commandLineOptions.hasMavenProfile()) {
            goals.add("-P" + commandLineOptions.getMavenProfile());
        }
        goals.add("-pl " + testModule);
        goals.add("-Dtest=" + commandLineOptions.getTestClass() + testMethod);
        return goals;
    }

    private boolean setNextCommit() throws IOException {
        if (counter++ >= commandLineOptions.getLimit()) {
            printGreen("Done!");
            return false;
        }
        if (counter == 1) {
            return setFirstCommit();
        }
        switch (commandLineOptions.getSearchMode()) {
            case LINEAR:
                return setNextCommitLinear();
            case BINARY:
                throw new UnsupportedOperationException("The BINARY search mode is not implemented yet!");
            default:
                throw new UnsupportedEncodingException("Unknown search mode: " + commandLineOptions.getSearchMode());
        }
    }

    private boolean setFirstCommit() throws IOException {
        String startCommit = commandLineOptions.getStartCommit();
        if (!setCurrentNameOSandEE(startCommit)) {
            printRed("Could not find OS commit for " + startCommit);
            return false;
        }
        currentCommitOS = getCommit(repoOS, walkOS, currentNameOS);
        if (isEE) {
            currentCommitEE = getCommit(repoEE, walkEE, currentNameEE);
        }
        return true;
    }

    private boolean setNextCommitLinear() throws IOException {
        if (isEE) {
            int tryCount = 0;
            do {
                currentCommitEE = getFirstParent(currentCommitEE, walkEE);
                if (currentCommitEE == null) {
                    print("Reached the last EE commit!");
                    return false;
                }
                if (setCurrentNameOSandEE(currentCommitEE.getName())) {
                    break;
                }
            } while (++tryCount < MAX_EE_COMMIT_SKIPS);
            currentCommitOS = getCommit(repoOS, walkOS, currentNameOS);
        } else {
            currentCommitOS = getFirstParent(currentCommitOS, walkOS);
            if (currentCommitOS == null) {
                print("Reached the last OS commit!");
                return false;
            }
        }
        return true;
    }

    private boolean setCurrentNameOSandEE(String currentName) {
        currentNameOS = currentName;
        if (isEE) {
            currentNameOS = HEAD.equals(currentName) ? HEAD : commits.get(currentName);
            if (NOT_AVAILABLE.equals(currentNameOS)) {
                printYellow("There is no OS commit for %s", currentNameEE);
                return false;
            }
            currentNameEE = currentName;
            return true;
        }
        return true;
    }

    private boolean executeTest(File projectRoot, List<String> goals) throws MavenInvocationException {
        System.out.printf("[%s] Executing %s... ", isEE ? "EE" : "OS", commandLineOptions.getTestClass());
        InvocationRequest request = new DefaultInvocationRequest()
                .setBatchMode(true)
                .setBaseDirectory(projectRoot)
                .setPomFile(new File(projectRoot, "pom.xml"))
                .setGoals(goals);

        long started = System.nanoTime();
        InvocationResult result = invoker.execute(request);
        long elapsedSeconds = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - started);

        String errorMsg = null;
        boolean success = result.getExitCode() == 0;

        if (outputHandler.contains("COMPILATION ERROR")) {
            errorMsg = "There were compilation errors!";
            success = false;
        } else if (outputHandler.contains("No tests were executed!")) {
            errorMsg = "Test could not be found, please check if you have specified the correct module and profile!";
            success = false;
        } else if (outputHandler.contains("[ERROR] There are test failures.")) {
            errorMsg = format("There were test failures!%n%s", outputHandler.getLine("»"));
            success = false;
        }

        if (success) {
            printGreen("SUCCESS (%d seconds)", elapsedSeconds);
        } else {
            printRed("FAILURE (%d seconds)", elapsedSeconds);
            printRed(errorMsg);
        }
        if (isDebug()) {
            print("\n================================================================================");
            print("================================= Maven output =================================");
            print("================================================================================");
            outputHandler.printAll();
            print("================================================================================\n");
        }
        outputHandler.clear();

        return success;
    }
}
