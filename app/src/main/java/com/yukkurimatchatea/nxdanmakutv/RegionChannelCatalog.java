package com.yukkurimatchatea.nxdanmakutv;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Regional terrestrial remote-control presets. Physical RF channels are intentionally excluded. */
public final class RegionChannelCatalog {
    public record Station(int remoteNumber, String name, String channelId, List<String> aliases) {
        public boolean supported() {
            return channelId != null && !channelId.isBlank();
        }
    }

    public record Region(String id, String name, List<Station> stations) {
        public Station byRemoteNumber(int number) {
            for (Station station : stations) {
                if (station.remoteNumber() == number) return station;
            }
            return null;
        }

        public Station stationForChannel(String channelId) {
            if (channelId == null) return null;
            for (Station station : stations) {
                if (channelId.equals(station.channelId())) return station;
            }
            return null;
        }

        public Station adjacent(int currentRemoteNumber, int direction) {
            if (stations.isEmpty()) return null;
            for (int i = 0; i < stations.size(); i++) {
                if (stations.get(i).remoteNumber() == currentRemoteNumber) {
                    return stations.get(Math.floorMod(i + direction, stations.size()));
                }
            }
            return direction >= 0 ? stations.get(0) : stations.get(stations.size() - 1);
        }

        public Station detect(String visibleText) {
            String normalized = ChannelCatalog.normalize(visibleText);
            if (normalized.isEmpty()) return null;
            Station best = null;
            int bestLength = 0;
            for (Station station : stations) {
                List<String> candidates = new ArrayList<>();
                candidates.add(station.name());
                candidates.addAll(station.aliases());
                for (String alias : candidates) {
                    String candidate = ChannelCatalog.normalize(alias);
                    if (candidate.length() > bestLength
                            && ChannelCatalog.matchesAlias(visibleText, alias)) {
                        best = station;
                        bestLength = candidate.length();
                    }
                }
            }
            if (best != null) return best;
            ChannelCatalog.Channel network = ChannelCatalog.detect(visibleText);
            return network == null ? null : stationForChannel(network.id());
        }
    }

    private static final List<Region> REGIONS;
    private static final Map<String, Region> BY_ID;

    static {
        List<Region> regions = new ArrayList<>();
        regions.add(r("hokkaido", "北海道",
                s(1,"NHK総合・札幌","jk1","NHK総合"), s(2,"NHK Eテレ","jk2"),
                s(4,"札幌テレビ","jk4","STV"), s(5,"北海道テレビ","jk5","HTB"),
                s(6,"北海道放送","jk6","HBC"), s(7,"テレビ北海道","jk7","TVh"),
                s(8,"北海道文化放送","jk8","UHB")));
        regions.add(r("aomori", "青森県",
                s(1,"青森放送","jk4","RAB"), s(2,"NHK Eテレ","jk2"),
                s(3,"NHK総合・青森","jk1","NHK総合"), s(5,"青森朝日放送","jk5","ABA"),
                s(6,"青森テレビ","jk6","ATV")));
        regions.add(r("iwate", "岩手県",
                s(1,"NHK総合・盛岡","jk1","NHK総合"), s(2,"NHK Eテレ","jk2"),
                s(4,"テレビ岩手","jk4","TVI"), s(5,"岩手朝日テレビ","jk5","IAT"),
                s(6,"IBC岩手放送","jk6","IBC"), s(8,"岩手めんこいテレビ","jk8","mit")));
        regions.add(r("miyagi", "宮城県",
                s(1,"東北放送","jk6","tbcテレビ","TBC"), s(2,"NHK Eテレ","jk2"),
                s(3,"NHK総合・仙台","jk1","NHK総合"), s(4,"ミヤギテレビ","jk4","MMT"),
                s(5,"東日本放送","jk5","khb"), s(8,"仙台放送","jk8","OX")));
        regions.add(r("akita", "秋田県",
                s(1,"NHK総合・秋田","jk1","NHK総合"), s(2,"NHK Eテレ","jk2"),
                s(4,"秋田放送","jk4","ABS"), s(5,"秋田朝日放送","jk5","AAB"),
                s(8,"秋田テレビ","jk8","AKT")));
        regions.add(r("yamagata", "山形県",
                s(1,"NHK総合・山形","jk1","NHK総合"), s(2,"NHK Eテレ","jk2"),
                s(4,"山形放送","jk4","YBC"), s(5,"山形テレビ","jk5","YTS"),
                s(6,"テレビユー山形","jk6","TUY"), s(8,"さくらんぼテレビ","jk8","SAY")));
        regions.add(r("fukushima", "福島県",
                s(1,"NHK総合・福島","jk1","NHK総合"), s(2,"NHK Eテレ","jk2"),
                s(4,"福島中央テレビ","jk4","FCT"), s(5,"福島放送","jk5","KFB"),
                s(6,"テレビユー福島","jk6","TUF"), s(8,"福島テレビ","jk8","FTV")));
        regions.add(r("ibaraki", "茨城県", kanto("NHK総合・水戸", null)));
        regions.add(r("tochigi", "栃木県", kanto("NHK総合・宇都宮", s(3,"とちぎテレビ",null,"GYT"))));
        regions.add(r("gunma", "群馬県", kanto("NHK総合・前橋", s(3,"群馬テレビ",null,"群テレ","GTV"))));
        regions.add(r("saitama", "埼玉県", kanto("NHK総合・東京", s(3,"テレビ埼玉","jk10","テレ玉"))));
        regions.add(r("chiba", "千葉県", kanto("NHK総合・東京", s(3,"千葉テレビ",null,"チバテレ","CTC"))));
        regions.add(r("tokyo", "東京都", kanto("NHK総合・東京", s(9,"TOKYO MX","jk9","東京MX","MX1","MX2"))));
        regions.add(r("kanagawa", "神奈川県", kanto("NHK総合・東京", s(3,"テレビ神奈川",null,"tvk"))));
        regions.add(r("niigata", "新潟県",
                s(1,"NHK総合・新潟","jk1","NHK総合"), s(2,"NHK Eテレ","jk2"),
                s(4,"テレビ新潟","jk4","TeNY"), s(5,"新潟テレビ21","jk5","UX"),
                s(6,"新潟放送","jk6","BSN"), s(8,"新潟総合テレビ","jk8","NST")));
        regions.add(r("toyama", "富山県",
                s(1,"北日本放送","jk4","KNB"), s(2,"NHK Eテレ","jk2"),
                s(3,"NHK総合・富山","jk1","NHK総合"), s(6,"チューリップテレビ","jk6","TUT"),
                s(8,"富山テレビ","jk8","BBT")));
        regions.add(r("ishikawa", "石川県",
                s(1,"NHK総合・金沢","jk1","NHK総合"), s(2,"NHK Eテレ","jk2"),
                s(4,"テレビ金沢","jk4","KTK"), s(5,"北陸朝日放送","jk5","HAB"),
                s(6,"北陸放送","jk6","MRO"), s(8,"石川テレビ","jk8","ITC")));
        regions.add(r("fukui", "福井県",
                s(1,"NHK総合・福井","jk1","NHK総合"), s(2,"NHK Eテレ","jk2"),
                s(7,"福井放送","jk4","FBC"), s(8,"福井テレビ","jk8","FTB")));
        regions.add(r("yamanashi", "山梨県",
                s(1,"NHK総合・甲府","jk1","NHK総合"), s(2,"NHK Eテレ","jk2"),
                s(4,"山梨放送","jk4","YBS"), s(6,"テレビ山梨","jk6","UTY")));
        regions.add(r("nagano", "長野県",
                s(1,"NHK総合・長野","jk1","NHK総合"), s(2,"NHK Eテレ","jk2"),
                s(4,"テレビ信州","jk4","TSB"), s(5,"長野朝日放送","jk5","abn"),
                s(6,"信越放送","jk6","SBC"), s(8,"長野放送","jk8","NBS")));
        regions.add(r("gifu", "岐阜県", tokai("NHK総合・岐阜", s(8,"岐阜放送",null,"ぎふチャン"))));
        regions.add(r("shizuoka", "静岡県",
                s(1,"NHK総合・静岡","jk1","NHK総合"), s(2,"NHK Eテレ","jk2"),
                s(4,"静岡第一テレビ","jk4","Daiichi-TV","SDT"), s(5,"静岡朝日テレビ","jk5","SATV"),
                s(6,"静岡放送","jk6","SBS"), s(8,"テレビ静岡","jk8","SUT")));
        regions.add(r("aichi", "愛知県", tokai("NHK総合・名古屋", null)));
        regions.add(r("mie", "三重県", tokai("NHK総合・津", s(7,"三重テレビ",null,"MTV"))));
        regions.add(r("shiga", "滋賀県", kinki("NHK総合・大津", s(3,"びわ湖放送",null,"BBCびわ湖放送"))));
        regions.add(r("kyoto", "京都府", kinki("NHK総合・京都", s(5,"KBS京都",null,"京都放送"))));
        regions.add(r("osaka", "大阪府", kinkiOsaka()));
        regions.add(r("hyogo", "兵庫県", kinki("NHK総合・神戸", s(3,"サンテレビ",null,"SUN"))));
        regions.add(r("nara", "奈良県", kinki("NHK総合・奈良", s(9,"奈良テレビ",null,"TVN"))));
        regions.add(r("wakayama", "和歌山県", kinki("NHK総合・和歌山", s(5,"テレビ和歌山",null,"WTV"))));
        regions.add(r("tottori", "鳥取県", sanin("NHK総合・鳥取")));
        regions.add(r("shimane", "島根県", sanin("NHK総合・松江")));
        regions.add(r("okayama", "岡山県", okayamaKagawa("NHK総合・岡山")));
        regions.add(r("hiroshima", "広島県",
                s(1,"NHK総合・広島","jk1","NHK総合"), s(2,"NHK Eテレ","jk2"),
                s(4,"広島テレビ","jk4","HTV"), s(5,"広島ホームテレビ","jk5","HOME"),
                s(6,"中国放送","jk6","RCC"), s(8,"テレビ新広島","jk8","TSS")));
        regions.add(r("yamaguchi", "山口県",
                s(1,"山口放送","jk4","KRY"), s(2,"NHK Eテレ","jk2"),
                s(3,"テレビ山口","jk6","tys"), s(4,"NHK総合・山口","jk1","NHK総合"),
                s(5,"山口朝日放送","jk5","yab")));
        regions.add(r("tokushima", "徳島県",
                s(1,"NHK総合・徳島","jk1","NHK総合"), s(2,"NHK Eテレ","jk2"),
                s(3,"四国放送","jk4","JRT")));
        regions.add(r("kagawa", "香川県", okayamaKagawa("NHK総合・高松")));
        regions.add(r("ehime", "愛媛県",
                s(1,"NHK総合・松山","jk1","NHK総合"), s(2,"NHK Eテレ","jk2"),
                s(4,"南海放送","jk4","RNB"), s(5,"愛媛朝日テレビ","jk5","eat"),
                s(6,"あいテレビ","jk6","ITV"), s(8,"テレビ愛媛","jk8","EBC")));
        regions.add(r("kochi", "高知県",
                s(1,"NHK総合・高知","jk1","NHK総合"), s(2,"NHK Eテレ","jk2"),
                s(4,"高知放送","jk4","RKC"), s(6,"テレビ高知","jk6","KUTV"),
                s(8,"高知さんさんテレビ","jk8","KSS")));
        regions.add(r("fukuoka", "福岡県",
                s(1,"九州朝日放送","jk5","KBC"), s(2,"NHK Eテレ","jk2"),
                s(3,"NHK総合・福岡","jk1","NHK総合"), s(4,"RKB毎日放送","jk6","RKB"),
                s(5,"福岡放送","jk4","FBS"), s(7,"TVQ九州放送","jk7","TVQ"),
                s(8,"テレビ西日本","jk8","TNC")));
        regions.add(r("saga", "佐賀県",
                s(1,"NHK総合・佐賀","jk1","NHK総合"), s(2,"NHK Eテレ","jk2"),
                s(3,"サガテレビ","jk8","STS")));
        regions.add(r("nagasaki", "長崎県",
                s(1,"NHK総合・長崎","jk1","NHK総合"), s(2,"NHK Eテレ","jk2"),
                s(3,"長崎放送","jk6","NBC"), s(4,"長崎国際テレビ","jk4","NIB"),
                s(5,"長崎文化放送","jk5","NCC"), s(8,"テレビ長崎","jk8","KTN")));
        regions.add(r("kumamoto", "熊本県",
                s(1,"NHK総合・熊本","jk1","NHK総合"), s(2,"NHK Eテレ","jk2"),
                s(3,"熊本放送","jk6","RKK"), s(4,"熊本県民テレビ","jk4","KKT"),
                s(5,"熊本朝日放送","jk5","KAB"), s(8,"テレビ熊本","jk8","TKU")));
        regions.add(r("oita", "大分県",
                s(1,"NHK総合・大分","jk1","NHK総合"), s(2,"NHK Eテレ","jk2"),
                s(3,"大分放送","jk6","OBS"), s(4,"テレビ大分",null,"TOS"),
                s(5,"大分朝日放送","jk5","OAB")));
        regions.add(r("miyazaki", "宮崎県",
                s(1,"NHK総合・宮崎","jk1","NHK総合"), s(2,"NHK Eテレ","jk2"),
                s(3,"テレビ宮崎",null,"UMK"), s(6,"宮崎放送","jk6","MRT")));
        regions.add(r("kagoshima", "鹿児島県",
                s(1,"南日本放送","jk6","MBC"), s(2,"NHK Eテレ","jk2"),
                s(3,"NHK総合・鹿児島","jk1","NHK総合"), s(4,"鹿児島読売テレビ","jk4","KYT"),
                s(5,"鹿児島放送","jk5","KKB"), s(8,"鹿児島テレビ","jk8","KTS")));
        regions.add(r("okinawa", "沖縄県",
                s(1,"NHK総合・沖縄","jk1","NHK総合"), s(2,"NHK Eテレ","jk2"),
                s(3,"琉球放送","jk6","RBC"), s(5,"琉球朝日放送","jk5","QAB"),
                s(8,"沖縄テレビ","jk8","OTV")));

        REGIONS = Collections.unmodifiableList(regions);
        Map<String, Region> byId = new LinkedHashMap<>();
        for (Region region : regions) byId.put(region.id(), region);
        BY_ID = Collections.unmodifiableMap(byId);
    }

    private RegionChannelCatalog() {}

    public static List<Region> all() { return REGIONS; }
    public static Region byId(String id) { return id == null ? null : BY_ID.get(id); }

    private static Region r(String id, String name, Station... stations) {
        List<Station> sorted = new ArrayList<>(List.of(stations));
        sorted.sort((a, b) -> Integer.compare(a.remoteNumber(), b.remoteNumber()));
        return new Region(id, name, Collections.unmodifiableList(sorted));
    }

    private static Station s(int remote, String name, String channelId, String... aliases) {
        return new Station(remote, name, channelId, List.of(aliases));
    }

    private static Station[] kanto(String nhkName, Station local) {
        List<Station> s = new ArrayList<>(List.of(
                s(1,nhkName,"jk1","NHK総合"), s(2,"NHK Eテレ","jk2"),
                s(4,"日本テレビ","jk4","日テレ"), s(5,"テレビ朝日","jk5","テレ朝"),
                s(6,"TBSテレビ","jk6","TBS"), s(7,"テレビ東京","jk7","テレ東"),
                s(8,"フジテレビ","jk8","フジ")));
        if (local != null) s.add(local);
        return s.toArray(new Station[0]);
    }

    private static Station[] tokai(String nhkName, Station local) {
        List<Station> s = new ArrayList<>(List.of(
                s(1,"東海テレビ","jk8","THK"), s(2,"NHK Eテレ","jk2"),
                s(3,nhkName,"jk1","NHK総合"), s(4,"中京テレビ","jk4","CTV"),
                s(5,"CBCテレビ","jk6","CBC"), s(6,"メ～テレ","jk5","名古屋テレビ"),
                s(10,"テレビ愛知","jk7","TVA")));
        if (local != null) s.add(local);
        return s.toArray(new Station[0]);
    }

    private static Station[] kinki(String nhkName, Station local) {
        List<Station> s = new ArrayList<>(List.of(
                s(1,nhkName,"jk1","NHK総合"), s(2,"NHK Eテレ","jk2"),
                s(4,"毎日放送","jk6","MBS"), s(6,"朝日放送テレビ","jk5","ABCテレビ"),
                s(8,"関西テレビ","jk8","カンテレ"), s(10,"読売テレビ","jk4","ytv")));
        if (local != null) s.add(local);
        return s.toArray(new Station[0]);
    }

    private static Station[] kinkiOsaka() {
        List<Station> s = new ArrayList<>(List.of(kinki("NHK総合・大阪", null)));
        s.add(s(7,"テレビ大阪","jk7","TVO"));
        return s.toArray(new Station[0]);
    }

    private static Station[] sanin(String nhkName) {
        return new Station[]{s(1,"日本海テレビ","jk4","NKT"), s(2,"NHK Eテレ","jk2"),
                s(3,nhkName,"jk1","NHK総合"), s(6,"山陰放送","jk6","BSS"),
                s(8,"山陰中央テレビ","jk8","TSK")};
    }

    private static Station[] okayamaKagawa(String nhkName) {
        return new Station[]{s(1,nhkName,"jk1","NHK総合"), s(2,"NHK Eテレ","jk2"),
                s(4,"西日本放送","jk4","RNC"), s(5,"瀬戸内海放送","jk5","KSB"),
                s(6,"RSK山陽放送","jk6","RSK"), s(7,"テレビせとうち","jk7","TSC"),
                s(8,"岡山放送","jk8","OHK")};
    }
}
