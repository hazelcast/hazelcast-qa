package com.hazelcast.qasonar.codecoverage;

import com.hazelcast.qasonar.utils.GitHubStatus;
import com.hazelcast.qasonar.utils.PropertyReader;
import org.junit.Before;
import org.junit.Test;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestFileDetail;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.PagedIterable;
import org.kohsuke.github.PagedIterator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.hazelcast.qasonar.utils.GitHubStatus.ADDED;
import static com.hazelcast.qasonar.utils.GitHubStatus.MODIFIED;
import static com.hazelcast.qasonar.utils.GitHubStatus.REMOVED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CodeCoverageReaderTest {

    private static final String HZ_PACKAGE = "com.hazelcast.";
    private static final String HZ_PREFIX = "hazelcast/src/main/java/com/hazelcast/";

    private static final String DCL_PACKAGE = "distributedclassloading";
    private static final String DCL_TEST_PREFIX = "hazelcast/src/test/java/distributedclassloading/";

    private static final String JET_PACKAGE = "com.hazelcast.jet.cascading.";
    private static final String JET_PREFIX = "hazelcast-jet-cascading/src/main/java/com/hazelcast/jet/cascading/";
    private static final String JET_TEST_PREFIX = "hazelcast-jet-cascading/src/test/java/data/";

    private List<Integer> pullRequests = new ArrayList<>();

    private int pullRequestIdGenerator;
    private GHRepository repo;
    private CodeCoverageReader reader;

    @Before
    public void setUp() throws Exception {
        GHUser author = mock(GHUser.class);
        when(author.getName()).thenReturn("Hazelcast");

        repo = mock(GHRepository.class, RETURNS_DEEP_STUBS);
        when(repo.getIssue(anyInt()).getUser()).thenReturn(author);
        when(repo.getName()).thenReturn("hazelcast");

        PropertyReader props = new PropertyReader("host", "username", "password");
        props.setMinCodeCoverage(87.5, false);
        props.setMinCodeCoverage(60.0, true);

        JsonDownloader jsonDownloader = mock(JsonDownloader.class);

        reader = new CodeCoverageReader(props, repo, jsonDownloader);
    }

    @Test
    public void testRun() throws Exception {
        reader.addIdeaCoverage(HZ_PACKAGE + "AddedFile.java", 23);
        reader.addIdeaCoverage(HZ_PACKAGE + "AddedAndRemovedFile.java", 42);

        addPullRequest(
                getGhPullRequestFileDetail("AddedAndRemovedFile.java", ADDED),
                getGhPullRequestFileDetail("AddedFile.java", ADDED),
                getGhPullRequestFileDetail("pom.xml", MODIFIED)
        );
        addPullRequest(getGhPullRequestFileDetail("AddedAndRemovedFile.java", REMOVED));

        reader.run(pullRequests);

        Map<String, FileContainer> readerFiles = reader.getFiles();
        assertEquals(3, readerFiles.size());

        assertIdeaCoverage(readerFiles, HZ_PREFIX + "AddedFile.java", 23);
        assertIdeaCoverage(readerFiles, HZ_PREFIX + "AddedAndRemovedFile.java", 42);
        assertIdeaCoverage(readerFiles, HZ_PREFIX + "pom.xml", 0);
    }

    @Test
    public void testRun_withDistributedClassloadingFiles() throws Exception {
        reader.addIdeaCoverage(DCL_PACKAGE + "IncrementingEntryProcessor.java", 23);

        addPullRequest(
                getGhPullRequestFileDetail(DCL_TEST_PREFIX, "IncrementingEntryProcessor.java", ADDED)
        );

        reader.run(pullRequests);

        Map<String, FileContainer> readerFiles = reader.getFiles();
        assertEquals(1, readerFiles.size());

        assertIdeaCoverage(readerFiles, DCL_TEST_PREFIX + "IncrementingEntryProcessor.java", 0);
    }

    @Test
    public void testRun_withJetFiles() throws Exception {
        reader.addIdeaCoverage(JET_PACKAGE + "JetFlow.java", 23);

        addPullRequest(
                getGhPullRequestFileDetail(JET_PREFIX, "JetFlow.java", ADDED),
                getGhPullRequestFileDetail(JET_TEST_PREFIX, "InputData.java", ADDED)
        );

        reader.run(pullRequests);

        Map<String, FileContainer> readerFiles = reader.getFiles();
        assertEquals(2, readerFiles.size());

        assertIdeaCoverage(readerFiles, JET_PREFIX + "JetFlow.java", 23);
        assertIdeaCoverage(readerFiles, JET_TEST_PREFIX + "InputData.java", 0);
    }

    @Test(expected = IllegalStateException.class)
    public void testRun_shouldThrowIfGithubStatusCannotBeRetrieved() throws Exception {
        GHPullRequestFileDetail pullRequestFile = mock(GHPullRequestFileDetail.class);
        when(pullRequestFile.getFilename()).thenReturn(HZ_PREFIX + "CannotRetrieveStatus.java");

        addPullRequest(pullRequestFile);

        reader.run(pullRequests);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testGetFiles_shouldBeUnmodifiable() throws Exception {
        addPullRequest(getGhPullRequestFileDetail("AddedFile.java", ADDED));

        reader.run(pullRequests);
        Map<String, FileContainer> readerFiles = reader.getFiles();

        readerFiles.put("key", new FileContainer());
    }

    private void addPullRequest(GHPullRequestFileDetail... pullRequestFiles) throws IOException {
        int pullRequestId = ++pullRequestIdGenerator;
        pullRequests.add(pullRequestId);

        PagedIterable<GHPullRequestFileDetail> iterable = mockPagedIterable(pullRequestFiles);

        GHPullRequest pullRequest = mock(GHPullRequest.class);
        when(pullRequest.isMerged()).thenReturn(true);
        when(pullRequest.listFiles()).thenReturn(iterable);

        when(repo.getPullRequest(pullRequestId)).thenReturn(pullRequest);
    }

    private static GHPullRequestFileDetail getGhPullRequestFileDetail(String fileName, GitHubStatus status) {
        return getGhPullRequestFileDetail(HZ_PREFIX, fileName, status);
    }

    private static GHPullRequestFileDetail getGhPullRequestFileDetail(String prefix, String fileName, GitHubStatus status) {
        GHPullRequestFileDetail pullRequestFile = mock(GHPullRequestFileDetail.class);
        when(pullRequestFile.getFilename()).thenReturn(prefix + fileName);
        when(pullRequestFile.getStatus()).thenReturn(status.toString());
        when(pullRequestFile.getChanges()).thenReturn(0);
        when(pullRequestFile.getAdditions()).thenReturn(0);
        when(pullRequestFile.getDeletions()).thenReturn(0);
        return pullRequestFile;
    }

    @SuppressWarnings("unchecked")
    private static <T> PagedIterable<T> mockPagedIterable(T... values) {
        PagedIterator<T> mockIterator = mock(PagedIterator.class);

        PagedIterable<T> iterable = mock(PagedIterable.class);
        when(iterable.iterator()).thenReturn(mockIterator);

        if (values.length == 0) {
            when(mockIterator.hasNext()).thenReturn(false);
        } else if (values.length == 1) {
            when(mockIterator.hasNext()).thenReturn(true, false);
            when(mockIterator.next()).thenReturn(values[0]);
        } else {
            // build boolean array for hasNext()
            Boolean[] hasNextResponses = new Boolean[values.length];
            for (int i = 0; i < hasNextResponses.length - 1; i++) {
                hasNextResponses[i] = true;
            }
            hasNextResponses[hasNextResponses.length - 1] = false;
            when(mockIterator.hasNext()).thenReturn(true, hasNextResponses);
            T[] valuesMinusTheFirst = Arrays.copyOfRange(values, 1, values.length);
            when(mockIterator.next()).thenReturn(values[0], valuesMinusTheFirst);
        }

        return iterable;
    }

    private static void assertIdeaCoverage(Map<String, FileContainer> readerFiles, String fileName, double coverage) {
        assertTrue(readerFiles.containsKey(fileName));

        FileContainer container = readerFiles.get(fileName);
        assertEquals(coverage, container.ideaCoverage, 0.0001);
    }
}
