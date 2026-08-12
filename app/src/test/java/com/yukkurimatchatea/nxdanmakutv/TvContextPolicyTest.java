package com.yukkurimatchatea.nxdanmakutv;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class TvContextPolicyTest {
    @Test
    public void remoteKeysOnlyFollowInsideKnownTvApplication() {
        assertTrue(TvContextPolicy.allowsTvRemoteKey("tv.app", "tv.app", false));
        assertFalse(TvContextPolicy.allowsTvRemoteKey("tv.app", "launcher", false));
        assertFalse(TvContextPolicy.allowsTvRemoteKey("", "tv.app", false));
        assertFalse(TvContextPolicy.allowsTvRemoteKey("tv.app", "tv.app", true));
    }

    @Test
    public void systemUiCanRevealTheUnderlyingTvApplication() {
        assertTrue(TvContextPolicy.isLikelyTvForeground(
                "tv.app", "android", "tv.app", true));
        assertFalse(TvContextPolicy.isLikelyTvForeground(
                "tv.app", "android", "launcher", true));
        assertFalse(TvContextPolicy.isLikelyTvForeground(
                "tv.app", "launcher", "tv.app", false));
    }

    @Test
    public void learnedTvPackageCannotBeOverwrittenByAnotherApplication() {
        assertTrue(TvContextPolicy.canLearnTvPackage("", "tv.app", false, false));
        assertTrue(TvContextPolicy.canLearnTvPackage(
                "tv.app", "tv.app", false, false));
        assertFalse(TvContextPolicy.canLearnTvPackage(
                "tv.app", "youtube.app", false, false));
        assertFalse(TvContextPolicy.canLearnTvPackage("", "android", false, true));
    }
}
