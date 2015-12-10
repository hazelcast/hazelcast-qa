package com.hazelcast.qasonar.codecoverage;

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

import static com.hazelcast.qasonar.utils.GitHubStatus.ADDED;
import static com.hazelcast.qasonar.utils.GitHubStatus.MODIFIED;
import static java.lang.String.format;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CodeCoverageAnalyzerTest {

    private static final String HZ_PREFIX = "hazelcast/src/main/java/com/hazelcast/";

    private Map<String, FileContainer> files = new HashMap<String, FileContainer>();
    private WhiteList whiteList = new WhiteList();

    private CodeCoverageAnalyzer analyzer;

    @Before
    public void setUp() throws Exception {
        PropertyReader props = new PropertyReader("host", "username", "password");
        props.setMinCodeCoverage(87.5, false);
        props.setMinCodeCoverage(60.0, true);

        GHContent ghContent = createGHContent("");

        GHRepository repo = mock(GHRepository.class);
        when(repo.getFileContent(anyString())).thenReturn(ghContent);

        analyzer = new CodeCoverageAnalyzer(files, props, repo, whiteList);
    }

    @Test
    public void testRun() throws Exception {
        addFile("pom.xml", ADDED);
        addFile("package-info.java", ADDED);
        addFile("src/test/java/HazelcastTestSupport.java", ADDED);

        addFile("AddedFileWithLowBranchCoverage.java", ADDED, 86.7, 91.4, 75.0, 91.4);
        addFile("AddedFileWithSufficientCoverage.java", ADDED, 89.4, 93.8, 78.1, 91.4);
        addFile("AddedFileWithoutSonarCoverageAndSufficientIdeaCoverage.java", ADDED, 88.2);
        addFile("AddedFileWithoutSonarCoverageAndInsufficientIdeaCoverage.java", ADDED, 87.2);

        addFile("ModifiedFileWithSufficientCoverage.java", MODIFIED, 86.5, 87.5, 80.6, 0);

        analyzer.run();

        assertThatFileHasPassed("pom.xml");
        assertThatFileHasPassed("package-info.java");
        assertThatFileHasPassed("src/test/java/HazelcastTestSupport.java");

        assertThatFileHasFailed("AddedFileWithLowBranchCoverage.java");
        assertThatFileHasPassed("AddedFileWithSufficientCoverage.java");
        assertThatFileHasPassed("AddedFileWithoutSonarCoverageAndSufficientIdeaCoverage.java");
        assertThatFileHasFailed("AddedFileWithoutSonarCoverageAndInsufficientIdeaCoverage.java");

        assertThatFileHasPassed("ModifiedFileWithSufficientCoverage.java");
    }

    private FileContainer addFile(String fileName, GitHubStatus status) {
        FileContainer fileContainer = new FileContainer();
        fileContainer.resourceId = "0";
        fileContainer.pullRequests = "23";
        fileContainer.fileName = HZ_PREFIX + fileName;
        fileContainer.status = status;
        fileContainer.gitHubChanges = 0;
        fileContainer.gitHubAdditions = 0;
        fileContainer.gitHubDeletions = 0;

        files.put(HZ_PREFIX + fileName, fileContainer);

        return fileContainer;
    }

    private FileContainer addFile(String fileName, GitHubStatus status, double ideaCoverage) {
        FileContainer fileContainer = addFile(fileName, status);
        fileContainer.ideaCoverage = ideaCoverage;

        return fileContainer;
    }

    private FileContainer addFile(String fileName, GitHubStatus status, double sonarCoverage, double lineCoverage,
                                  double branchCoverage, double ideaCoverage) {
        FileContainer fileContainer = addFile(fileName, status, ideaCoverage);
        fileContainer.coverage = format("%.1f%%", sonarCoverage);
        fileContainer.numericCoverage = sonarCoverage;
        fileContainer.lineCoverage = format("%.1f%%", lineCoverage);
        fileContainer.numericLineCoverage = lineCoverage;
        fileContainer.branchCoverage = format("%.1f%%", branchCoverage);
        fileContainer.numericBranchCoverage = branchCoverage;

        return fileContainer;
    }

    private void assertThatFileHasFailed(String fileName) {
        FileContainer fileContainer = files.get(HZ_PREFIX + fileName);
        assertNotNull(format("Could not find fileContainer for %s ", fileName), fileContainer);

        assertTrue(format("%s should have been QA checked!", fileName), fileContainer.isQaCheckSet());
        assertFalse(format("%s should have failed QA check!", fileName), fileContainer.qaCheck);
    }

    private void assertThatFileHasPassed(String fileName) {
        FileContainer fileContainer = files.get(HZ_PREFIX + fileName);
        assertNotNull(format("Could not find fileContainer for %s ", fileName), fileContainer);

        assertTrue(format("%s should have been QA checked!", fileName), fileContainer.isQaCheckSet());
        assertTrue(format("%s should have failed QA check!", fileName), fileContainer.qaCheck);
    }

    private static GHContent createGHContent(String content) throws IOException {
        InputStream stream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

        GHContent ghContent = mock(GHContent.class);
        when(ghContent.read()).thenReturn(stream);
        return ghContent;
    }
}
