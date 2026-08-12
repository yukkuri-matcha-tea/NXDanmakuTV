package com.yukkurimatchatea.nxdanmakutv;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class WindowVisibilityPolicyTest {
    @Test
    public void inactiveFullscreenApplicationDoesNotBlock() {
        assertFalse(WindowVisibilityPolicy.blocksComments(
                false, false, false, false, 1.0f, 1.0f));
    }

    @Test
    public void activeButUnfocusedSystemUiDoesNotBlock() {
        assertFalse(WindowVisibilityPolicy.blocksComments(
                false, false, true, false, 1.0f, 1.0f));
    }

    @Test
    public void activeTransparentFullscreenSystemUiDoesNotBlock() {
        assertFalse(WindowVisibilityPolicy.blocksComments(
                false, false, true, true, 1.0f, 0.0f));
    }

    @Test
    public void smallVolumePanelDoesNotBlock() {
        assertFalse(WindowVisibilityPolicy.blocksComments(
                false, false, true, true, 0.20f, 0.05f));
    }

    @Test
    public void largeUsbSystemPanelBlocks() {
        assertTrue(WindowVisibilityPolicy.blocksComments(
                false, false, true, true, 1.0f, 0.60f));
    }

    @Test
    public void activeLargeApplicationBlocks() {
        assertTrue(WindowVisibilityPolicy.blocksComments(
                false, false, false, true, 1.0f, 0.0f));
    }

    @Test
    public void tvWindowNeverBlocks() {
        assertFalse(WindowVisibilityPolicy.blocksComments(
                true, false, false, true, 1.0f, 1.0f));
    }
}
