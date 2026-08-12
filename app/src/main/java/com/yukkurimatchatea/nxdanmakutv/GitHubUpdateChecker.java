package com.yukkurimatchatea.nxdanmakutv;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

final class GitHubUpdateChecker {
    static final String RELEASES_URL =
            "https://github.com/yukkuri-matcha-tea/NXDanmakuTV/releases";
    private static final String LATEST_RELEASE_API =
            "https://api.github.com/repos/yukkuri-matcha-tea/NXDanmakuTV/releases/latest";

    interface Listener {
        void onUpdateAvailable(UpdateInfo update);
        void onUpToDate();
        void onError(String message);
    }

    record UpdateInfo(String version, String apkUrl, String releaseUrl, String notes) {}

    private final OkHttpClient client = new OkHttpClient();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    void check(String currentVersion, Listener listener) {
        Request request = new Request.Builder()
                .url(LATEST_RELEASE_API)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "NXDanmakuTV/" + currentVersion)
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException error) {
                post(() -> listener.onError(safeMessage(error)));
            }

            @Override
            public void onResponse(Call call, Response response) {
                try (response) {
                    if (!response.isSuccessful() || response.body() == null) {
                        post(() -> listener.onError("GitHub応答 " + response.code()));
                        return;
                    }
                    JSONObject release = new JSONObject(response.body().string());
                    String version = normalizeVersion(release.optString("tag_name"));
                    String apkUrl = findApkUrl(release.optJSONArray("assets"));
                    String releaseUrl = release.optString("html_url", RELEASES_URL);
                    String notes = release.optString("body", "");
                    if (version.isEmpty() || apkUrl.isEmpty()) {
                        post(() -> listener.onError("Releaseに更新APKがありません"));
                    } else if (isNewer(version, currentVersion)) {
                        UpdateInfo update = new UpdateInfo(version, apkUrl, releaseUrl, notes);
                        post(() -> listener.onUpdateAvailable(update));
                    } else {
                        post(listener::onUpToDate);
                    }
                } catch (Exception error) {
                    post(() -> listener.onError(safeMessage(error)));
                }
            }
        });
    }

    void shutdown() {
        client.dispatcher().cancelAll();
        client.dispatcher().executorService().shutdown();
        client.connectionPool().evictAll();
    }

    static boolean isNewer(String candidate, String current) {
        int[] left = versionParts(candidate);
        int[] right = versionParts(current);
        for (int index = 0; index < Math.max(left.length, right.length); index++) {
            int candidatePart = index < left.length ? left[index] : 0;
            int currentPart = index < right.length ? right[index] : 0;
            if (candidatePart != currentPart) {
                return candidatePart > currentPart;
            }
        }
        return false;
    }

    private void post(Runnable runnable) {
        mainHandler.post(runnable);
    }

    private static String findApkUrl(JSONArray assets) {
        if (assets == null) return "";
        for (int index = 0; index < assets.length(); index++) {
            JSONObject asset = assets.optJSONObject(index);
            if (asset != null && asset.optString("name")
                    .toLowerCase(Locale.ROOT).endsWith(".apk")) {
                return asset.optString("browser_download_url");
            }
        }
        return "";
    }

    private static String normalizeVersion(String value) {
        String version = value == null ? "" : value.trim();
        return version.startsWith("v") || version.startsWith("V")
                ? version.substring(1) : version;
    }

    private static int[] versionParts(String value) {
        String normalized = normalizeVersion(value).replaceAll("[^0-9.].*$", "");
        String[] parts = normalized.split("\\.");
        int[] result = new int[parts.length];
        for (int index = 0; index < parts.length; index++) {
            try {
                result[index] = Integer.parseInt(parts[index]);
            } catch (NumberFormatException ignored) {
                result[index] = 0;
            }
        }
        return result;
    }

    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank()
                ? error.getClass().getSimpleName() : message;
    }
}
