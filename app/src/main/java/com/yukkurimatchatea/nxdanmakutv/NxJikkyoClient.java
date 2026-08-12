package com.yukkurimatchatea.nxdanmakutv;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public final class NxJikkyoClient {
    public interface Listener {
        void onStatus(String status);
        void onConnected(String channelId);
        void onComment(String channelId, String content, String mail, String userId);
    }

    private static final String BASE =
            "wss://nx-jikkyo.tsukumijima.net/api/v1/channels/";
    private static final int SESSION_TIMEOUT_SECONDS = 15;

    private final Listener listener;
    private final OkHttpClient httpClient;
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor();
    private final AtomicInteger generation = new AtomicInteger();

    private volatile WebSocket watchSocket;
    private volatile WebSocket commentSocket;
    private volatile String channelId;
    private volatile boolean stopped = true;
    private volatile int reconnectAttempt;
    private ScheduledFuture<?> reconnectFuture;
    private ScheduledFuture<?> keepSeatFuture;
    private ScheduledFuture<?> roomTimeoutFuture;
    private ScheduledFuture<?> subscriptionTimeoutFuture;

    public NxJikkyoClient(Listener listener) {
        this.listener = listener;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .pingInterval(20, TimeUnit.SECONDS)
                .build();
    }

    public synchronized void connect(String nextChannelId) {
        stopped = false;
        reconnectAttempt = 0;
        channelId = nextChannelId;
        int currentGeneration = generation.incrementAndGet();
        cancelScheduledWork();
        closeSockets();
        emitStatus("接続中 " + nextChannelId);
        try {
            openWatchSocket(currentGeneration);
        } catch (RuntimeException error) {
            scheduleReconnect(currentGeneration,
                    "接続開始例外: " + safeMessage(error));
        }
    }

    public synchronized void stop() {
        stopped = true;
        generation.incrementAndGet();
        cancelScheduledWork();
        closeSockets();
        emitStatus("停止");
    }

    public void shutdown() {
        stop();
        scheduler.shutdownNow();
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }

    private void openWatchSocket(int expectedGeneration) {
        String url = BASE + channelId + "/ws/watch";
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "NXDanmakuTV/1.0.0")
                .build();
        watchSocket = httpClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                if (!isCurrent(expectedGeneration)) {
                    webSocket.close(1000, "stale");
                    return;
                }
                if (!webSocket.send("{\"type\":\"startWatching\",\"data\":{}}")) {
                    scheduleReconnect(expectedGeneration, "視聴開始の送信失敗");
                    return;
                }
                scheduleRoomTimeout(expectedGeneration);
                emitStatus("視聴セッション接続");
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                if (!isCurrent(expectedGeneration)) {
                    return;
                }
                handleWatchMessage(webSocket, text, expectedGeneration);
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                if (isCurrent(expectedGeneration)) {
                    scheduleReconnect(expectedGeneration, "視聴セッション切断");
                }
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable error, Response response) {
                if (isCurrent(expectedGeneration)) {
                    scheduleReconnect(expectedGeneration,
                            "接続失敗: " + safeMessage(error));
                }
            }
        });
    }

    private void handleWatchMessage(WebSocket webSocket, String text, int expectedGeneration) {
        try {
            JSONObject message = new JSONObject(text);
            String type = message.optString("type");
            JSONObject data = message.optJSONObject("data");
            switch (type) {
                case "ping" -> webSocket.send("{\"type\":\"pong\"}");
                case "seat" -> {
                    int interval = data == null ? 30 : data.optInt("keepIntervalSec", 30);
                    scheduleKeepSeat(webSocket, expectedGeneration, interval);
                }
                case "room" -> {
                    if (data != null) {
                        String threadId = data.optString("threadId");
                        String postKey = data.optString("yourPostKey");
                        JSONObject messageServer = data.optJSONObject("messageServer");
                        String uri = messageServer == null ? "" : messageServer.optString("uri");
                        if (!uri.isEmpty() && !threadId.isEmpty()) {
                            cancelRoomTimeout();
                            openCommentSocket(uri, threadId, postKey, expectedGeneration);
                        }
                    }
                }
                case "disconnect" -> scheduleReconnect(expectedGeneration, "サーバーから切断");
                default -> {
                    // serverTime, schedule and statistics are informational.
                }
            }
        } catch (JSONException error) {
            emitStatus("視聴データ解析エラー: " + safeMessage(error));
        } catch (RuntimeException error) {
            emitStatus("視聴処理例外: " + safeMessage(error));
            scheduleReconnect(expectedGeneration, "視聴処理を復旧");
        }
    }

    private void openCommentSocket(
            String uri,
            String threadId,
            String postKey,
            int expectedGeneration
    ) {
        WebSocket previous = commentSocket;
        if (previous != null) {
            previous.close(1000, "switch room");
        }

        Request request = new Request.Builder()
                .url(uri)
                .header("User-Agent", "NXDanmakuTV/1.0.0")
                .build();
        commentSocket = httpClient.newWebSocket(request, new WebSocketListener() {
            private boolean connectedNotified;

            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                if (!isCurrent(expectedGeneration)) {
                    webSocket.close(1000, "stale");
                    return;
                }
                if (!webSocket.send(buildSubscription(threadId, postKey))) {
                    scheduleReconnect(expectedGeneration, "コメント購読の送信失敗");
                    return;
                }
                scheduleSubscriptionTimeout(expectedGeneration);
                emitStatus("コメント接続確認中 " + channelId);
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                if (isCurrent(expectedGeneration)) {
                    boolean subscriptionAccepted = handleCommentMessage(text);
                    if (subscriptionAccepted && !connectedNotified) {
                        connectedNotified = true;
                        cancelSubscriptionTimeout();
                        markConnected(expectedGeneration);
                        emitConnected(channelId);
                    }
                }
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable error, Response response) {
                if (isCurrent(expectedGeneration) && webSocket == commentSocket) {
                    scheduleReconnect(expectedGeneration,
                            "コメント接続失敗: " + safeMessage(error));
                }
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                if (isCurrent(expectedGeneration) && webSocket == commentSocket) {
                    scheduleReconnect(expectedGeneration, "コメント接続終了");
                }
            }
        });
    }

    private boolean handleCommentMessage(String text) {
        try {
            JSONObject message = new JSONObject(text);
            JSONObject thread = message.optJSONObject("thread");
            if (thread != null && thread.optInt("resultcode", -1) == 0) {
                return true;
            }
            JSONObject chat = message.optJSONObject("chat");
            if (chat == null) {
                return false;
            }
            String content = chat.optString("content");
            if (content.isBlank() || content.startsWith("/")) {
                return false;
            }
            emitComment(channelId, content,
                    chat.optString("mail"), chat.optString("user_id"));
        } catch (JSONException error) {
            emitStatus("コメント解析エラー: " + safeMessage(error));
        } catch (RuntimeException error) {
            emitStatus("コメント処理例外: " + safeMessage(error));
        }
        return false;
    }

    private void scheduleKeepSeat(
            WebSocket socket,
            int expectedGeneration,
            int intervalSeconds
    ) {
        synchronized (this) {
            if (keepSeatFuture != null) {
                keepSeatFuture.cancel(false);
            }
            int safeInterval = Math.max(5, Math.min(120, intervalSeconds));
            keepSeatFuture = scheduler.scheduleWithFixedDelay(() -> {
                if (isCurrent(expectedGeneration)
                        && socket == watchSocket
                        && !socket.send("{\"type\":\"keepSeat\"}")) {
                    scheduleReconnect(expectedGeneration, "視聴維持の送信失敗");
                }
            }, safeInterval, safeInterval, TimeUnit.SECONDS);
        }
    }

    private synchronized void scheduleRoomTimeout(int expectedGeneration) {
        cancelRoomTimeout();
        roomTimeoutFuture = scheduler.schedule(() -> {
            synchronized (NxJikkyoClient.this) {
                roomTimeoutFuture = null;
            }
            scheduleReconnect(expectedGeneration, "コメントルーム取得タイムアウト");
        }, SESSION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private synchronized void scheduleSubscriptionTimeout(int expectedGeneration) {
        cancelSubscriptionTimeout();
        subscriptionTimeoutFuture = scheduler.schedule(() -> {
            synchronized (NxJikkyoClient.this) {
                subscriptionTimeoutFuture = null;
            }
            scheduleReconnect(expectedGeneration, "コメント購読タイムアウト");
        }, SESSION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private synchronized void cancelRoomTimeout() {
        if (roomTimeoutFuture != null) {
            roomTimeoutFuture.cancel(false);
            roomTimeoutFuture = null;
        }
    }

    private synchronized void cancelSubscriptionTimeout() {
        if (subscriptionTimeoutFuture != null) {
            subscriptionTimeoutFuture.cancel(false);
            subscriptionTimeoutFuture = null;
        }
    }

    private synchronized void scheduleReconnect(int expectedGeneration, String reason) {
        if (!isCurrent(expectedGeneration)
                || (reconnectFuture != null && !reconnectFuture.isDone())) {
            return;
        }
        int delaySeconds = Math.min(30, 1 << Math.min(reconnectAttempt++, 4));
        emitStatus(reason + " / " + delaySeconds + "秒後に再接続");
        try {
            reconnectFuture = scheduler.schedule(() -> {
                synchronized (NxJikkyoClient.this) {
                    reconnectFuture = null;
                    if (isCurrent(expectedGeneration)) {
                        int nextGeneration = generation.incrementAndGet();
                        cancelScheduledWork();
                        closeSockets();
                        try {
                            openWatchSocket(nextGeneration);
                        } catch (RuntimeException error) {
                            scheduleReconnect(nextGeneration,
                                    "再接続例外: " + safeMessage(error));
                        }
                    }
                }
            }, delaySeconds, TimeUnit.SECONDS);
        } catch (RuntimeException error) {
            reconnectFuture = null;
            emitStatus("再接続予約エラー: " + safeMessage(error));
        }
    }

    private synchronized void markConnected(int expectedGeneration) {
        if (isCurrent(expectedGeneration)) {
            reconnectAttempt = 0;
            if (reconnectFuture != null) {
                reconnectFuture.cancel(false);
                reconnectFuture = null;
            }
        }
    }

    private boolean isCurrent(int expectedGeneration) {
        return !stopped && generation.get() == expectedGeneration;
    }

    private void closeSockets() {
        WebSocket watch = watchSocket;
        WebSocket comment = commentSocket;
        watchSocket = null;
        commentSocket = null;
        if (watch != null) {
            safeClose(watch);
        }
        if (comment != null) {
            safeClose(comment);
        }
    }

    private synchronized void cancelScheduledWork() {
        if (reconnectFuture != null) {
            reconnectFuture.cancel(false);
            reconnectFuture = null;
        }
        if (keepSeatFuture != null) {
            keepSeatFuture.cancel(false);
            keepSeatFuture = null;
        }
        cancelRoomTimeout();
        cancelSubscriptionTimeout();
    }

    private static String buildSubscription(String threadId, String postKey) {
        try {
            JSONArray messages = new JSONArray();
            messages.put(new JSONObject().put("ping",
                    new JSONObject().put("content", "rs:0")));
            messages.put(new JSONObject().put("ping",
                    new JSONObject().put("content", "ps:0")));
            messages.put(new JSONObject().put("thread", new JSONObject()
                    .put("version", "20061206")
                    .put("thread", threadId)
                    .put("threadkey", postKey)
                    .put("user_id", "")
                    .put("res_from", 0)));
            messages.put(new JSONObject().put("ping",
                    new JSONObject().put("content", "pf:0")));
            messages.put(new JSONObject().put("ping",
                    new JSONObject().put("content", "rf:0")));
            return messages.toString();
        } catch (JSONException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank()
                ? error.getClass().getSimpleName()
                : message;
    }

    private void emitStatus(String status) {
        try {
            listener.onStatus(status);
        } catch (RuntimeException ignored) {
            // A UI callback must never terminate the WebSocket dispatcher.
        }
    }

    private void emitConnected(String connectedChannelId) {
        try {
            listener.onConnected(connectedChannelId);
        } catch (RuntimeException error) {
            emitStatus("接続通知エラー: " + safeMessage(error));
        }
    }

    private void emitComment(
            String commentChannelId,
            String content,
            String mail,
            String userId
    ) {
        try {
            listener.onComment(commentChannelId, content, mail, userId);
        } catch (RuntimeException error) {
            emitStatus("コメント受け渡しエラー: " + safeMessage(error));
        }
    }

    private static void safeClose(WebSocket socket) {
        try {
            socket.close(1000, "client switch");
        } catch (RuntimeException ignored) {
            socket.cancel();
        }
    }
}
