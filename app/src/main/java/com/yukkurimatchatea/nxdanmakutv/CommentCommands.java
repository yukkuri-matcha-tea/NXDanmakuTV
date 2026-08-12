package com.yukkurimatchatea.nxdanmakutv;

import java.util.Locale;

final class CommentCommands {
    private CommentCommands() {
    }

    static boolean has(String mail, String expected) {
        if (mail == null || expected == null || expected.isBlank()) return false;
        String normalizedExpected = expected.toLowerCase(Locale.ROOT);
        for (String command : mail.toLowerCase(Locale.ROOT).trim().split("\\s+")) {
            if (normalizedExpected.equals(command)) return true;
        }
        return false;
    }
}
