package com.yukkurimatchatea.nxdanmakutv;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ChannelCatalog {
    public record Channel(String id, String name, int remoteNumber, List<String> aliases) {}

    private static final List<Channel> CHANNELS;
    private static final Map<String, Channel> BY_ID;

    static {
        List<Channel> channels = new ArrayList<>();
        channels.add(new Channel("jk1", "NHK総合", 1, List.of(
                "nhk総合", "nhk g", "総合テレビ", "nhkg", "nhk1")));
        channels.add(new Channel("jk2", "NHK Eテレ", 2, List.of(
                "nhk eテレ", "eテレ", "教育テレビ", "nhke")));
        channels.add(new Channel("jk4", "日本テレビ系", 4, List.of(
                "日本テレビ", "日テレ", "ntv", "読売テレビ", "ytv", "中京テレビ", "札幌テレビ",
                "福岡放送", "ミヤギテレビ", "広島テレビ")));
        channels.add(new Channel("jk5", "テレビ朝日系", 5, List.of(
                "テレビ朝日", "テレ朝", "ex", "朝日放送", "abcテレビ", "メ～テレ", "北海道テレビ",
                "九州朝日放送", "khb", "広島ホームテレビ")));
        channels.add(new Channel("jk6", "TBS系", 6, List.of(
                "tbsテレビ", "tbs", "毎日放送", "mbs", "cbcテレビ", "北海道放送", "rkb毎日放送",
                "東北放送", "中国放送")));
        channels.add(new Channel("jk7", "テレビ東京系", 7, List.of(
                "テレビ東京", "テレ東", "tv tokyo", "テレビ大阪", "テレビ愛知", "テレビ北海道",
                "tvq九州放送", "テレビせとうち")));
        channels.add(new Channel("jk8", "フジテレビ系", 8, List.of(
                "フジテレビ", "フジ", "cx", "関西テレビ", "カンテレ", "東海テレビ", "北海道文化放送",
                "テレビ西日本", "仙台放送", "テレビ新広島")));
        channels.add(new Channel("jk9", "TOKYO MX", 9, List.of(
                "tokyo mx", "東京mx", "mx1", "mx2")));
        channels.add(new Channel("jk10", "テレ玉", 10, List.of(
                "テレ玉", "テレビ埼玉")));
        CHANNELS = Collections.unmodifiableList(channels);

        Map<String, Channel> byId = new LinkedHashMap<>();
        for (Channel channel : channels) {
            byId.put(channel.id(), channel);
        }
        BY_ID = Collections.unmodifiableMap(byId);
    }

    private ChannelCatalog() {}

    public static List<Channel> all() {
        return CHANNELS;
    }

    public static Channel byId(String id) {
        return BY_ID.get(id);
    }

    public static Channel byRemoteNumber(int number) {
        for (Channel channel : CHANNELS) {
            if (channel.remoteNumber() == number) {
                return channel;
            }
        }
        return null;
    }

    public static Channel adjacent(String currentId, int direction) {
        for (int i = 0; i < CHANNELS.size(); i++) {
            if (CHANNELS.get(i).id().equals(currentId)) {
                int next = Math.floorMod(i + direction, CHANNELS.size());
                return CHANNELS.get(next);
            }
        }
        return null;
    }

    public static Channel detect(String visibleText) {
        String normalized = normalize(visibleText);
        if (normalized.isEmpty()) {
            return null;
        }

        Channel best = null;
        int bestLength = 0;
        for (Channel channel : CHANNELS) {
            for (String alias : channel.aliases()) {
                String candidate = normalize(alias);
                if (candidate.length() > bestLength && normalized.contains(candidate)) {
                    best = channel;
                    bestLength = candidate.length();
                }
            }
        }
        return best;
    }

    static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", "");
    }
}
