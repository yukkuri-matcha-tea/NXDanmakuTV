package com.yukkurimatchatea.nxdanmakutv;

final class TvContextPolicy {
    private TvContextPolicy() {
    }

    static boolean allowsTvRemoteKey(
            String tvPackage, String foregroundPackage, boolean blockingUiVisible) {
        return !isBlank(tvPackage)
                && tvPackage.equals(foregroundPackage)
                && !blockingUiVisible;
    }

    static boolean isLikelyTvForeground(
            String tvPackage,
            String foregroundPackage,
            String lastApplicationPackage,
            boolean foregroundIsSystemUi
    ) {
        if (isBlank(tvPackage)) return false;
        if (tvPackage.equals(foregroundPackage)) return true;
        return foregroundIsSystemUi && tvPackage.equals(lastApplicationPackage);
    }

    static boolean canLearnTvPackage(
            String currentTvPackage,
            String sourcePackage,
            boolean ownPackage,
            boolean systemUiPackage
    ) {
        return !isBlank(sourcePackage)
                && !ownPackage
                && !systemUiPackage
                && (isBlank(currentTvPackage) || currentTvPackage.equals(sourcePackage));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
