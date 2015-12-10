package com.hazelcast.qasonar.codecoverage;

import com.hazelcast.qasonar.codecoverage.FileContainer.CoverageType;
import com.hazelcast.qasonar.utils.GitHubStatus;
import com.hazelcast.qasonar.utils.PropertyReader;
import com.hazelcast.qasonar.utils.WhiteList;
import org.junit.Before;
import org.junit.Test;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHRepository;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static com.hazelcast.qasonar.codecoverage.CodeCoverageAnalyzerTest.Result.FAIL;
import static com.hazelcast.qasonar.codecoverage.CodeCoverageAnalyzerTest.Result.PASS;
import static com.hazelcast.qasonar.codecoverage.FileContainer.CoverageType.IDEA;
import static com.hazelcast.qasonar.codecoverage.FileContainer.CoverageType.NONE;
import static com.hazelcast.qasonar.codecoverage.FileContainer.CoverageType.SONAR;
import static com.hazelcast.qasonar.utils.GitHubStatus.ADDED;
import static com.hazelcast.qasonar.utils.GitHubStatus.CHANGED;
import static com.hazelcast.qasonar.utils.GitHubStatus.MODIFIED;
import static com.hazelcast.qasonar.utils.GitHubStatus.REMOVED;
import static com.hazelcast.qasonar.utils.GitHubStatus.RENAMED;
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

    private CodeCoverageAnalyzer analyzer;

    @Before
    public void setUp() throws Exception {
        files = new HashMap<String, FileContainer>();
        expectedResults = new HashMap<String, Result>();
        expectedCoverageTypes = new HashMap<String, CoverageType>();

        PropertyReader props = new PropertyReader("host", "username", "password");
        props.setMinCodeCoverage(87.5, false);
        props.setMinCodeCoverage(60.0, true);

        GHContent ghContent = createGHContentMock("");

        GHRepository repo = mock(GHRepository.class);
        when(repo.getFileContent(anyString())).thenReturn(ghContent);

        WhiteList whiteList = new WhiteList();

        analyzer = new CodeCoverageAnalyzer(files, props, repo, whiteList);
    }

    @Test
    public void testRun() throws Exception {
        addFile(PASS, NONE, "pom.xml", ADDED);
        addFile(PASS, NONE, "package-info.java", ADDED);
        addFile(PASS, NONE, "src/test/java/HazelcastTestSupport.java", ADDED);

        addFile(PASS, NONE, "RemovedFile.java", REMOVED);
        addFile(PASS, NONE, "RenamedFile.java", RENAMED);
        addFile(PASS, NONE, "ChangedFile.java", CHANGED);

        addFile(PASS, SONAR, "AddedFileWithSufficientSonarCoverage.java", ADDED, 89.4, 93.8, 78.1, 91.4);
        addFile(FAIL, SONAR, "AddedFileWithInsufficientSonarCoverage.java", ADDED, 86.7, 91.4, 75.0, 91.4);
        addFile(PASS, IDEA, "AddedFileWithoutSonarCoverageAndSufficientIdeaCoverage.java", ADDED, 88.2);
        addFile(FAIL, IDEA, "AddedFileWithoutSonarCoverageAndInsufficientIdeaCoverage.java", ADDED, 87.2);

        addFile(PASS, SONAR, "ModifiedFileWithSufficientSonarCoverage.java", MODIFIED, 86.5, 87.5, 80.6, 0.0);
        addFile(FAIL, SONAR, "ModifiedFileWithInsufficientSonarCoverage.java", MODIFIED, 45.6, 48.6, 40.2, 0.0);
        addFile(PASS, IDEA, "ModifiedFileWithoutSonarCoverageAndSufficientIdeaCoverage.java", MODIFIED, 61.2);
        addFile(FAIL, IDEA, "ModifiedFileWithoutSonarCoverageAndInsufficientIdeaCoverage.java", MODIFIED, 48.9);

        analyzer.run();

        assertQACheckOfAllFiles();
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

    private void assertQACheckOfAllFiles() {
        Map<String, FileContainer> analyzerFiles = analyzer.getFiles();

        for (Map.Entry<String, Result> resultEntry : expectedResults.entrySet()) {
            String fileName = resultEntry.getKey();

            FileContainer fileContainer = analyzerFiles.get(HZ_PREFIX + fileName);
            assertNotNull(format("Could not find fileContainer for %s ", fileName), fileContainer);

            assertTrue(format("%s should have been QA checked!", fileName), fileContainer.isQaCheckSet());

            switch (resultEntry.getValue()) {
                case PASS:
                    assertTrue(format("%s should have passed QA check!", fileName), fileContainer.qaCheck);

                    CoverageType expectedCoverageType = expectedCoverageTypes.get(fileName);
                    CoverageType actualCoverageType = fileContainer.coverageType;
                    assertEquals(format("%s should have coverage from %s, but was %s!",
                            fileName, expectedCoverageType, actualCoverageType),
                            expectedCoverageType, actualCoverageType);
                    break;
                case FAIL:
                    assertFalse(format("%s should have failed QA check!", fileName), fileContainer.qaCheck);
                    break;
                default:
                    throw new UnsupportedOperationException("Unknown Result: " + resultEntry.getValue());
            }
        }
    }

    private static GHContent createGHContentMock(String content) throws IOException {
        InputStream stream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

        GHContent ghContent = mock(GHContent.class);
        when(ghContent.read()).thenReturn(stream);
        return ghContent;
    }
}
