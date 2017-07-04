package com.hazelcast.qasonar.codecoverage;

import com.hazelcast.qasonar.codecoverage.FileContainer.CoverageType;
import com.hazelcast.qasonar.utils.WhiteList;
import com.hazelcast.utils.DebugUtils;
import com.hazelcast.utils.GitHubStatus;
import com.hazelcast.utils.PropertyReader;
import org.junit.Before;
import org.junit.Test;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHRepository;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static com.hazelcast.qasonar.codecoverage.CodeCoverageAnalyzerTest.Result.FAIL;
import static com.hazelcast.qasonar.codecoverage.CodeCoverageAnalyzerTest.Result.PASS;
import static com.hazelcast.qasonar.codecoverage.FileContainer.CoverageType.IDEA;
import static com.hazelcast.qasonar.codecoverage.FileContainer.CoverageType.NONE;
import static com.hazelcast.qasonar.codecoverage.FileContainer.CoverageType.SONAR;
import static com.hazelcast.utils.GitHubStatus.ADDED;
import static com.hazelcast.utils.GitHubStatus.CHANGED;
import static com.hazelcast.utils.GitHubStatus.MODIFIED;
import static com.hazelcast.utils.GitHubStatus.REMOVED;
import static com.hazelcast.utils.GitHubStatus.RENAMED;
import static java.lang.String.format;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CodeCoverageAnalyzerTest {

    enum Result {
        PASS,
        FAIL
    }

    private static final String HZ_PREFIX = "hazelcast/src/main/java/com/hazelcast/";

    private Map<String, FileContainer> files;
    private Map<String, Result> expectedResults;
    private Map<String, CoverageType> expectedCoverageTypes;

    private PropertyReader props;
    private GHRepository repo;
    private WhiteList whiteList;

    private CodeCoverageAnalyzer analyzer;

    @Before
    public void setUp() throws Exception {
        DebugUtils.setDebug(true);
        DebugUtils.debug("");

        files = new HashMap<>();
        expectedResults = new HashMap<>();
        expectedCoverageTypes = new HashMap<>();

        props = new PropertyReader("host", "username", "password");
        props.setMinCodeCoverage(87.5, false);
        props.setMinCodeCoverage(60.0, true);

        GHContent ghContent = createGHContentMock("");

        repo = mock(GHRepository.class);
        when(repo.getFileContent(anyString())).thenReturn(ghContent);

        whiteList = new WhiteList();

        analyzer = new CodeCoverageAnalyzer(files, props, repo, whiteList);
    }

    @Test
    public void testRun() throws Exception {
        addFile(PASS, NONE, "pom.xml", ADDED);
        addFile(PASS, NONE, "package-info.java", ADDED);
        addFile(PASS, NONE, "src/test/java/HazelcastTestSupport.java", ADDED);

        FileContainer fileContainer = addFile(PASS, NONE, "ModuleDeleted.java", ADDED);
        fileContainer.isModuleDeleted = true;
        fileContainer = addFile(PASS, NONE, "ModuleDeletedButHasIdeaCoverageDueToNameCollision.java", ADDED, 56.7);
        fileContainer.isModuleDeleted = true;

        addFile(PASS, NONE, "RemovedFile.java", REMOVED);
        addFile(PASS, NONE, "RenamedFile.java", RENAMED);
        addFile(PASS, NONE, "ChangedFile.java", CHANGED);
        addFile(PASS, NONE, "RenamedFileWithSingleLineGitHubChanges.java", RENAMED, 2, 1, 1);
        addFile(FAIL, NONE, "ChangedFileWithSingleLineGitHubChanges.java", CHANGED, 2, 1, 1);
        addFile(FAIL, NONE, "RenamedFileWithSignificantGitHubChanges.java", RENAMED, 15, 20, 5);
        addFile(FAIL, NONE, "ChangedFileWithSignificantGitHubChanges.java", CHANGED, 19, 42, 23);

        addFile(PASS, NONE, "WhitelistedFile.java", ADDED);
        whiteList.addEntry("ENDS_WITH", "WhitelistedFile.java", "cross project", null);
        addFile(FAIL, NONE, "WhitelistedFileJustComment.java", ADDED);
        whiteList.addEntry("ENDS_WITH", "WhitelistedFileJustComment.java", null, "just a comment");

        addFile(PASS, NONE, "DeletedInNewerPR.java", ADDED);
        throwExceptionForGitHubFile("DeletedInNewerPR.java", new FileNotFoundException("DeletedInNewerPR.java not found"));

        addFile(PASS, NONE, "PublicInterface.java", ADDED);
        addFileFromGitHub("PublicInterface.java");
        addFile(PASS, NONE, "PackagePrivateInterface.java", ADDED);
        addFileFromGitHub("PackagePrivateInterface.java");
        addFile(PASS, NONE, "PublicEnum.java", MODIFIED);
        addFileFromGitHub("PublicEnum.java");
        addFile(PASS, NONE, "CustomAnnotation.java", MODIFIED);
        addFileFromGitHub("CustomAnnotation.java");

        addFile(PASS, SONAR, "AddedFileWithSufficientSonarCoverage.java", ADDED, 89.4, 93.8, 78.1, 0.0);
        addFile(FAIL, SONAR, "AddedFileWithInsufficientSonarCoverage.java", ADDED, 86.7, 91.4, 75.0, 0.0);
        addFile(PASS, IDEA, "AddedFileNoSonarCoverageAndSufficientIdeaCoverage.java", ADDED, 88.2);
        addFile(FAIL, IDEA, "AddedFileNoSonarCoverageAndInsufficientIdeaCoverage.java", ADDED, 87.2);
        addFile(FAIL, SONAR, "AddedFileWithInsufficientSonarAndIdeaCoverage.java", ADDED, 0.0, 0.0, 0.0, 0.0);
        addFile(FAIL, NONE, "AddedFileNoSonarCoverageAndNoIdeaCoverage.java", ADDED, 0.0);

        addFile(PASS, SONAR, "ModifiedFileWithSufficientSonarCoverage.java", MODIFIED, 86.5, 87.5, 80.6, 0.0);
        addFile(FAIL, SONAR, "ModifiedFileWithInsufficientSonarCoverage.java", MODIFIED, 45.6, 48.6, 40.2, 0.0);
        addFile(PASS, IDEA, "ModifiedFileNoSonarCoverageAndSufficientIdeaCoverage.java", MODIFIED, 61.2);
        addFile(FAIL, IDEA, "ModifiedFileNoSonarCoverageAndInsufficientIdeaCoverage.java", MODIFIED, 48.9);
        addFile(FAIL, SONAR, "ModifiedFileWithInsufficientSonarAndIdeaCoverage.java", MODIFIED, 0.0, 0.0, 0.0, 0.0);
        addFile(FAIL, NONE, "ModifiedFileNoSonarCoverageAndNoIdeaCoverage.java", MODIFIED, 0.0);

        addFile(FAIL, SONAR, "AddedFileWithInsufficientSonarCoverageAndIdeaCoverageNotUsed.java", ADDED, 82.3, 87.3, 79.2, 87.6);

        analyzer.run();

        assertQACheckOfAllFiles();
    }

    @Test
    public void testRun_withMinThresholdModified() throws Exception {
        props.setMinThresholdModified(5);

        addFile(FAIL, SONAR, "AddedFileShouldAlwaysFail.java", ADDED, 23.0, 26.5, 18.3, 26.5).gitHubChanges = 2;
        addFile(FAIL, SONAR, "ModifiedWithChangesAboveThreshold.java", MODIFIED, 27.0, 29.4, 25.3, 29.5).gitHubChanges = 6;
        addFile(PASS, IDEA, "ModifiedWithChangesOnThreshold.java", MODIFIED, 12.6).gitHubChanges = 5;
        addFile(PASS, SONAR, "ModifiedWithChangesBelowThreshold.java", MODIFIED, 13.0, 18.5, 12.3, 18.6).gitHubChanges = 4;

        analyzer.run();

        assertQACheckOfAllFiles();
    }

    @Test
    public void testRun_shouldThrowIfFileCouldNotBeRetrieved() throws Exception {
        addFile(FAIL, NONE, "CouldNotRetrieveFileContent.java", ADDED);
        throwExceptionForGitHubFile("CouldNotRetrieveFileContent.java", new IOException("Expected connection failure!"));

        analyzer.run();

        assertQACheckOfAllFiles();
    }

    @Test
    public void testGetFiles() {
        addFile(PASS, NONE, "RemovedFile.java", REMOVED);
        addFile(PASS, NONE, "RenamedFile.java", RENAMED);
        addFile(PASS, NONE, "ChangedFile.java", CHANGED);

        Map<String, FileContainer> analyzerFiles = analyzer.getFiles();

        assertEquals(files.size(), analyzerFiles.size());
        assertTrue(analyzerFiles.containsKey(HZ_PREFIX + "RemovedFile.java"));
        assertTrue(analyzerFiles.containsKey(HZ_PREFIX + "RenamedFile.java"));
        assertTrue(analyzerFiles.containsKey(HZ_PREFIX + "ChangedFile.java"));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testGetFiles_shouldBeUnmodifiable() {
        Map<String, FileContainer> analyzerFiles = analyzer.getFiles();

        analyzerFiles.put("key", new FileContainer());
    }

    private FileContainer addFile(Result expectedResult, CoverageType expectedCoverageType, String fileName,
                                  GitHubStatus status) {
        FileContainer fileContainer = new FileContainer();
        fileContainer.resourceId = "0";
        fileContainer.pullRequests = "23";
        fileContainer.fileName = HZ_PREFIX + fileName;
        fileContainer.status = status;
        fileContainer.gitHubChanges = 0;
        fileContainer.gitHubAdditions = 0;
        fileContainer.gitHubDeletions = 0;

        files.put(HZ_PREFIX + fileName, fileContainer);
        expectedResults.put(fileName, expectedResult);
        expectedCoverageTypes.put(fileName, expectedCoverageType);

        return fileContainer;
    }

    private FileContainer addFile(Result expectedResult, CoverageType expectedCoverageType, String fileName, GitHubStatus status,
                                  double ideaCoverage) {
        FileContainer fileContainer = addFile(expectedResult, expectedCoverageType, fileName, status);
        fileContainer.ideaCoverage = ideaCoverage;

        return fileContainer;
    }

    private FileContainer addFile(Result expectedResult, CoverageType expectedCoverageType, String fileName, GitHubStatus status,
                                  double sonarCoverage, double lineCoverage, double branchCoverage, double ideaCoverage) {
        FileContainer fileContainer = addFile(expectedResult, expectedCoverageType, fileName, status, ideaCoverage);
        fileContainer.coverage = format("%.1f%%", sonarCoverage);
        fileContainer.numericCoverage = sonarCoverage;
        fileContainer.lineCoverage = format("%.1f%%", lineCoverage);
        fileContainer.numericLineCoverage = lineCoverage;
        fileContainer.branchCoverage = format("%.1f%%", branchCoverage);
        fileContainer.numericBranchCoverage = branchCoverage;

        return fileContainer;
    }

    private FileContainer addFile(Result expectedResult, CoverageType expectedCoverageType, String fileName,
                                  GitHubStatus status, int gitHubChanges, int gitHubAdditions, int gitHubDeletions) {
        FileContainer fileContainer = addFile(expectedResult, expectedCoverageType, fileName, status);
        fileContainer.gitHubChanges = gitHubChanges;
        fileContainer.gitHubAdditions = gitHubAdditions;
        fileContainer.gitHubDeletions = gitHubDeletions;

        return fileContainer;
    }

    private void addFileFromGitHub(String fileName) throws Exception {
        URL resource = getClass().getResource(fileName);
        Path path = Paths.get(resource.toURI());
        String fileContent = new String(Files.readAllBytes(path));
        GHContent ghContent = createGHContentMock(fileContent);
        when(repo.getFileContent(HZ_PREFIX + fileName)).thenReturn(ghContent);
    }

    private void throwExceptionForGitHubFile(String fileName, Exception exception) throws Exception {
        when(repo.getFileContent(HZ_PREFIX + fileName)).thenThrow(exception);
    }

    private void assertQACheckOfAllFiles() {
        for (Map.Entry<String, Result> resultEntry : expectedResults.entrySet()) {
            String fileName = resultEntry.getKey();

            FileContainer fileContainer = files.get(HZ_PREFIX + fileName);
            assertNotNull(format("Could not find fileContainer for %s ", fileName), fileContainer);
            assertTrue(format("%s should have been QA checked!", fileName), fileContainer.isQaCheckSet());

            Result expectedResult = resultEntry.getValue();
            switch (expectedResult) {
                case PASS:
                    assertTrue(format("%s should have passed QA check!", fileName), fileContainer.qaCheck);
                    break;
                case FAIL:
                    assertFalse(format("%s should have failed QA check!", fileName), fileContainer.qaCheck);
                    break;
                default:
                    throw new UnsupportedOperationException("Unknown Result: " + expectedResult);
            }

            CoverageType expectedCoverageType = expectedCoverageTypes.get(fileName);
            CoverageType actualCoverageType = fileContainer.coverageType;
            assertEquals(
                    format("%s should have coverage from %s, but was %s!", fileName, expectedCoverageType, actualCoverageType),
                    expectedCoverageType, actualCoverageType);
        }
    }

    private static GHContent createGHContentMock(String content) throws IOException {
        InputStream stream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

        GHContent ghContent = mock(GHContent.class);
        when(ghContent.read()).thenReturn(stream);
        return ghContent;
    }
}
