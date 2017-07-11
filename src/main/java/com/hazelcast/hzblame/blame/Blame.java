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
import java.util.concurrent.TimeUnit;

import static com.hazelcast.utils.CsvUtils.NOT_AVAILABLE;
import static com.hazelcast.utils.CsvUtils.readCSV;
import static com.hazelcast.utils.DebugUtils.debug;
import static com.hazelcast.utils.DebugUtils.isDebug;
import static com.hazelcast.utils.DebugUtils.print;
import static com.hazelcast.utils.DebugUtils.printGreen;
import static com.hazelcast.utils.DebugUtils.printRed;
import static com.hazelcast.utils.DebugUtils.printYellow;
import static com.hazelcast.utils.GitUtils.asString;
import static com.hazelcast.utils.GitUtils.checkout;
import static com.hazelcast.utils.GitUtils.compile;
import static com.hazelcast.utils.GitUtils.getCommit;
import static com.hazelcast.utils.GitUtils.getFirstParent;
import static org.eclipse.jgit.lib.Constants.HEAD;

public class Blame extends AbstractGitClass {

    private static final int MAX_EE_COMMIT_SKIPS = 10;

    private final Path commitPath = Paths.get("ee-os.csv");
    private final Map<String, String> commits = new HashMap<>();

    private final BufferingOutputHandler outputHandler;
    private final Invoker invoker;

    private final CommandLineOptions commandLineOptions;

    private final boolean isDry;
    private final boolean isEE;
    private final int limit;
    private final int retriesOnTestSuccess;

    private String currentNameOS;
    private String currentNameEE;

    private int counter;

    public Blame(PropertyReader propertyReader, CommandLineOptions commandLineOptions) {
        super("blame", propertyReader);
        this.commandLineOptions = commandLineOptions;

        this.isDry = commandLineOptions.isDry();
        this.isEE = commandLineOptions.isEE();
        this.limit = commandLineOptions.getLimit();
        this.retriesOnTestSuccess = commandLineOptions.getRetriesOnTestSuccess();

        this.outputHandler = new BufferingOutputHandler();
        this.invoker = new DefaultInvoker()
                .setOutputHandler(outputHandler)
                .setMavenHome(new File("/usr/share/maven"));
    }

    @Override
    public void doRun() throws Exception {
        File projectRoot = getProjectRoot();
        List<String> goals = getMavenGoals();
        debug("Maven goals: %s", goals);
        if (isEE) {
            readCSV(commitPath, commits);
        }

        while (setNextCommit()) {
            // compile version
            checkout(branchName, gitOS, currentCommitOS);
            compile(invoker, outputHandler, gitOS, currentCommitOS, isDry, false);
            if (isEE) {
                checkout(branchName, gitEE, currentCommitEE);
                compile(invoker, outputHandler, gitEE, currentCommitEE, isDry, true);
            }
            // execute test
            int successCount = 0;
            for (int retryCount = 1; retryCount <= retriesOnTestSuccess; retryCount++) {
                if (!executeTest(projectRoot, goals, retryCount)) {
                    break;
                }
                successCount++;
            }
            if (successCount == retriesOnTestSuccess) {
                printGreen("Test passed without errors!");
                break;
            }
            System.out.println();
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
        if (counter++ >= limit && limit > 0) {
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
                    if (tryCount > 0) {
                        System.out.println();
                    }
                    break;
                }
            } while (++tryCount < MAX_EE_COMMIT_SKIPS);
            if (currentNameOS == null) {
                printRed("Didn't find any more OS commits, giving up...");
                return false;
            }
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

    private boolean setCurrentNameOSandEE(String currentName) throws IOException {
        currentNameOS = currentName;
        if (isEE) {
            currentNameOS = HEAD.equals(currentName) ? HEAD : commits.get(currentName);
            if (NOT_AVAILABLE.equals(currentNameOS) || currentNameOS == null) {
                printYellow("There is no OS commit for EE %s", asString(getCommit(repoEE, walkEE, currentName)));
                return false;
            }
            currentNameEE = currentName;
            return true;
        }
        return true;
    }

    private boolean executeTest(File projectRoot, List<String> goals, int retryCount) throws MavenInvocationException {
        String message = retriesOnTestSuccess > 1 ? "[%s] Executing %s (%d/%d)... " : "[%s] Executing %s... ";
        System.out.printf(message, isEE ? "EE" : "OS", commandLineOptions.getTestClass(), retryCount, retriesOnTestSuccess);
        if (isDry) {
            printRed("FAILURE (dry run)");
            return false;
        }

        InvocationRequest request = new DefaultInvocationRequest()
                .setBatchMode(true)
                .setBaseDirectory(projectRoot)
                .setPomFile(new File(projectRoot, "pom.xml"))
                .setGoals(goals);

        long started = System.nanoTime();
        InvocationResult result = invoker.execute(request);
        long elapsedSeconds = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - started);

        String errorMsg = outputHandler.findErrors();
        boolean success = errorMsg == null && result.getExitCode() == 0;

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
