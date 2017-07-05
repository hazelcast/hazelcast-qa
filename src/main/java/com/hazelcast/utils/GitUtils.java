package com.hazelcast.utils;

import org.apache.maven.shared.invoker.DefaultInvocationRequest;
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
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.String.format;
import static java.util.Arrays.asList;

public class GitUtils {

    private static final int SHA_LENGTH = 7;
    private static final int SHORT_MESSAGE_LENGTH = 80;

    private static final AtomicInteger COMPILE_COUNTER_OS = new AtomicInteger();
    private static final AtomicInteger COMPILE_COUNTER_EE = new AtomicInteger();

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

    public static List<RevCommit> getCommits(Git git) throws GitAPIException {
        List<RevCommit> list = new ArrayList<>();
        for (RevCommit commit : git.log().call()) {
            list.add(commit);
        }
        return list;
    }

    public static void cleanupBranches(String branchName, Git git) throws GitAPIException {
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

    public static RevCommit createBranch(String branchName, Git git, Iterator<RevCommit> iterator) throws GitAPIException {
        cleanupBranches(branchName, git);
        RevCommit commit = iterator.next();
        createBranch(branchName, git, commit);
        return commit;
    }

    public static boolean compile(boolean isVerbose, Invoker invoker, BufferingOutputHandler outputHandler, Git git,
                                  RevCommit commit, boolean isEE) throws MavenInvocationException {
        String label = isEE ? "EE" : "OS";
        int counter = isEE ? COMPILE_COUNTER_EE.incrementAndGet() : COMPILE_COUNTER_OS.incrementAndGet();
        System.out.printf("[%s] [%3d] Compiling %s... ", label, counter, asString(commit));
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

    public static String asString(RevCommit commit) {
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
}
