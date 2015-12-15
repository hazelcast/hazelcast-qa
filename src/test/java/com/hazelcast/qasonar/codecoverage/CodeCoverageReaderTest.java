package com.hazelcast.qasonar.codecoverage;

import com.hazelcast.qasonar.utils.GitHubStatus;
import com.hazelcast.qasonar.utils.PropertyReader;
import org.junit.Before;
import org.junit.Test;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestFileDetail;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.PagedIterable;
import org.kohsuke.github.PagedIterator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.hazelcast.qasonar.utils.GitHubStatus.ADDED;
import static com.hazelcast.qasonar.utils.GitHubStatus.REMOVED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CodeCoverageReaderTest {

    private static final String HZ_PREFIX = "hazelcast/src/main/java/com/hazelcast/";

    private List<Integer> pullRequests = new ArrayList<Integer>();

    private int pullRequestIdGenerator;

    private GHRepository repo;

    private CodeCoverageReader reader;

    @Before
    public void setUp() throws Exception {
        repo = mock(GHRepository.class);

        PropertyReader props = new PropertyReader("host", "username", "password");
        props.setMinCodeCoverage(87.5, false);
        props.setMinCodeCoverage(60.0, true);

        reader = new CodeCoverageReader(props, repo);
    }

    @Test
    public void testRun() throws Exception {
        addPullRequest(
                getGhPullRequestFileDetail("AddedAndRemovedFile.java", ADDED),
                getGhPullRequestFileDetail("AddedFile.java", ADDED));
        addPullRequest(getGhPullRequestFileDetail("AddedAndRemovedFile.java", REMOVED));

        reader.run(pullRequests);

        Map<String, FileContainer> readerFiles = reader.getFiles();
        assertEquals(2, readerFiles.size());
        assertTrue(readerFiles.containsKey(HZ_PREFIX + "AddedFile.java"));
        assertTrue(readerFiles.containsKey(HZ_PREFIX + "AddedAndRemovedFile.java"));
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
        when(pullRequest.listFiles()).thenReturn(iterable);

        when(repo.getPullRequest(pullRequestId)).thenReturn(pullRequest);
    }

    private static GHPullRequestFileDetail getGhPullRequestFileDetail(String fileName, GitHubStatus status) {
        GHPullRequestFileDetail pullRequestFile = mock(GHPullRequestFileDetail.class);
        when(pullRequestFile.getFilename()).thenReturn(HZ_PREFIX + fileName);
        when(pullRequestFile.getStatus()).thenReturn(status.toString());
        when(pullRequestFile.getChanges()).thenReturn(0);
        when(pullRequestFile.getAdditions()).thenReturn(0);
        when(pullRequestFile.getDeletions()).thenReturn(0);
        return pullRequestFile;
    }

    @SuppressWarnings("unchecked")
    public static <T> PagedIterable<T> mockPagedIterable(T... values) {
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
}
