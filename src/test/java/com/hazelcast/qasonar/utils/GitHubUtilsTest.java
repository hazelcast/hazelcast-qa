package com.hazelcast.qasonar.utils;

import org.junit.Before;
import org.junit.Test;
import org.kohsuke.github.GHMilestone;
import org.kohsuke.github.GHPullRequest;

import static com.hazelcast.qasonar.utils.GitHubUtils.ALL_MILESTONE;
import static com.hazelcast.qasonar.utils.GitHubUtils.MERGED_MILESTONE;
import static com.hazelcast.qasonar.utils.GitHubUtils.NO_MILESTONE;
import static com.hazelcast.qasonar.utils.GitHubUtils.matchesMilestone;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GitHubUtilsTest {

    private GHMilestone milestone;

    private GHPullRequest pullRequest;
    private GHPullRequest pullRequestOtherMilestone;
    private GHPullRequest pullRequestNoMilestone;
    private GHPullRequest pullRequestUnmerged;

    @Before
    public void setUp() throws Exception {
        milestone = mock(GHMilestone.class);
        when(milestone.getId()).thenReturn(23);

        GHMilestone otherMilestone = mock(GHMilestone.class);
        when(otherMilestone.getId()).thenReturn(42);

        pullRequest = mock(GHPullRequest.class);
        pullRequestOtherMilestone = mock(GHPullRequest.class);
        pullRequestNoMilestone = mock(GHPullRequest.class);
        pullRequestUnmerged = mock(GHPullRequest.class);

        when(pullRequest.getId()).thenReturn(1);
        when(pullRequest.isMerged()).thenReturn(true);
        when(pullRequest.getMilestone()).thenReturn(milestone);

        when(pullRequestOtherMilestone.getId()).thenReturn(2);
        when(pullRequestOtherMilestone.isMerged()).thenReturn(true);
        when(pullRequestOtherMilestone.getMilestone()).thenReturn(otherMilestone);

        when(pullRequestNoMilestone.getId()).thenReturn(3);
        when(pullRequestNoMilestone.isMerged()).thenReturn(true);

        when(pullRequestUnmerged.getId()).thenReturn(4);
        when(pullRequestUnmerged.isMerged()).thenReturn(false);
        when(pullRequest.getMilestone()).thenReturn(milestone);
    }

    @Test
    public void testMatchesMilestone() {
        assertTrue(matchesMilestone(pullRequest, MERGED_MILESTONE));
        assertTrue(matchesMilestone(pullRequestOtherMilestone, MERGED_MILESTONE));
        assertTrue(matchesMilestone(pullRequestNoMilestone, MERGED_MILESTONE));
        assertFalse(matchesMilestone(pullRequestUnmerged, MERGED_MILESTONE));

        assertTrue(matchesMilestone(pullRequest, ALL_MILESTONE));
        assertTrue(matchesMilestone(pullRequestOtherMilestone, ALL_MILESTONE));
        assertFalse(matchesMilestone(pullRequestNoMilestone, ALL_MILESTONE));
        assertFalse(matchesMilestone(pullRequestUnmerged, ALL_MILESTONE));

        assertFalse(matchesMilestone(pullRequest, NO_MILESTONE));
        assertFalse(matchesMilestone(pullRequestOtherMilestone, NO_MILESTONE));
        assertTrue(matchesMilestone(pullRequestNoMilestone, NO_MILESTONE));
        assertFalse(matchesMilestone(pullRequestUnmerged, NO_MILESTONE));

        assertTrue(matchesMilestone(pullRequest, milestone));
        assertFalse(matchesMilestone(pullRequestOtherMilestone, milestone));
        assertFalse(matchesMilestone(pullRequestNoMilestone, milestone));
        assertFalse(matchesMilestone(pullRequestUnmerged, milestone));
    }
}
