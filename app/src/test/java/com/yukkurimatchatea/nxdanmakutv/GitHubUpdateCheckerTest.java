package com.yukkurimatchatea.nxdanmakutv;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class GitHubUpdateCheckerTest {
    @Test
    public void detectsNewerSemanticVersion() {
        assertTrue(GitHubUpdateChecker.isNewer("0.3.1", "0.3.0"));
        assertTrue(GitHubUpdateChecker.isNewer("v1.0.0", "0.9.9"));
    }

    @Test
    public void rejectsSameOrOlderVersion() {
        assertFalse(GitHubUpdateChecker.isNewer("v0.3.0", "0.3.0"));
        assertFalse(GitHubUpdateChecker.isNewer("0.2.9", "0.3.0"));
    }
}
