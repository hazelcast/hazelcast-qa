package com.hazelcast.qasonar.codecoverage;

import com.hazelcast.qasonar.utils.GitHubStatus;
import com.hazelcast.qasonar.utils.PropertyReader;
import com.hazelcast.qasonar.utils.WhiteList;
import org.junit.Before;
import org.junit.Test;
import org.kohsuke.github.GHRepository;

import java.util.HashMap;
import java.util.Map;

import static com.hazelcast.qasonar.utils.GitHubStatus.ADDED;
import static com.hazelcast.qasonar.utils.GitHubStatus.MODIFIED;
import static java.lang.String.format;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

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

        GHRepository repo = mock(GHRepository.class);

        analyzer = new CodeCoverageAnalyzer(files, props, repo, whiteList);
    }

    @Test
    public void testRun() throws Exception {
        addFile("AddedFileWithLowBranchCoverage.java", ADDED, 86.7, 91.4, 75.0, 91.4);
        addFile("ModifiedFileWithSufficientCoverage.java", MODIFIED, 86.5, 87.5, 80.6, 0);

        analyzer.run();

        assertThatFileHasFailed("AddedFileWithLowBranchCoverage.java");
        assertThatFileHasPassed("ModifiedFileWithSufficientCoverage.java");
    }

    private void addFile(String fileName, GitHubStatus status, double sonarCoverage, double lineCoverage, double branchCoverage,
                         double ideaCoverage) {
        FileContainer fileContainer = new FileContainer();
        fileContainer.resourceId = "0";
        fileContainer.pullRequests = "23";
        fileContainer.fileName = HZ_PREFIX + fileName;
        fileContainer.status = status;
        fileContainer.gitHubChanges = 0;
        fileContainer.gitHubAdditions = 0;
        fileContainer.gitHubDeletions = 0;
        fileContainer.coverage = format("%.1f%%", sonarCoverage);
        fileContainer.numericCoverage = sonarCoverage;
        fileContainer.lineCoverage = format("%.1f%%", lineCoverage);
        fileContainer.numericLineCoverage = lineCoverage;
        fileContainer.branchCoverage = format("%.1f%%", branchCoverage);
        fileContainer.numericBranchCoverage = branchCoverage;
        fileContainer.ideaCoverage = ideaCoverage;

        files.put(HZ_PREFIX + fileName, fileContainer);
    }

    private void assertThatFileHasPassed(String fileName) {
        FileContainer fileContainer = files.get(HZ_PREFIX + fileName);
        assertNotNull(fileContainer);

        assertTrue(fileContainer.isQaCheckSet());
        assertTrue(fileContainer.qaCheck);
    }

    private void assertThatFileHasFailed(String fileName) {
        FileContainer fileContainer = files.get(HZ_PREFIX + fileName);
        assertNotNull(fileContainer);

        assertTrue(fileContainer.isQaCheckSet());
        assertFalse(fileContainer.qaCheck);
    }
}
