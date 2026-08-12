package com.yukkurimatchatea.nxdanmakutv;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class CommentCommandsTest {
    @Test
    public void blueDoesNotContainTopPositionCommand() {
        assertTrue(CommentCommands.has("blue big", "blue"));
        assertTrue(CommentCommands.has("blue big", "big"));
        assertFalse(CommentCommands.has("blue big", "ue"));
    }

    @Test
    public void positionCommandsRequireWholeTokens() {
        assertTrue(CommentCommands.has("184 UE red", "ue"));
        assertTrue(CommentCommands.has("shita small", "shita"));
        assertFalse(CommentCommands.has("ueue", "ue"));
    }
}
