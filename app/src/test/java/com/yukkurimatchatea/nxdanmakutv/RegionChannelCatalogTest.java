package com.yukkurimatchatea.nxdanmakutv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

public class RegionChannelCatalogTest {
    @Test
    public void containsAllPrefecturesWithUniqueRemoteNumbers() {
        assertEquals(47, RegionChannelCatalog.all().size());
        for (RegionChannelCatalog.Region region : RegionChannelCatalog.all()) {
            assertFalse(region.name(), region.stations().isEmpty());
            Set<Integer> remotes = new HashSet<>();
            for (RegionChannelCatalog.Station station : region.stations()) {
                assertFalse(region.name() + " remote " + station.remoteNumber(),
                        !remotes.add(station.remoteNumber()));
            }
        }
    }

    @Test
    public void mapsIshikawaRemoteNumbersToNetworks() {
        RegionChannelCatalog.Region ishikawa = RegionChannelCatalog.byId("ishikawa");
        assertNotNull(ishikawa);
        assertEquals("jk1", ishikawa.byRemoteNumber(1).channelId());
        assertEquals("jk4", ishikawa.byRemoteNumber(4).channelId());
        assertEquals("jk5", ishikawa.byRemoteNumber(5).channelId());
        assertEquals("jk6", ishikawa.byRemoteNumber(6).channelId());
        assertEquals("jk8", ishikawa.byRemoteNumber(8).channelId());
        assertEquals(5, ishikawa.adjacent(4, 1).remoteNumber());
    }

    @Test
    public void handlesRegionsWhoseNetworkUsesDifferentRemoteNumber() {
        RegionChannelCatalog.Region aomori = RegionChannelCatalog.byId("aomori");
        assertEquals("jk4", aomori.byRemoteNumber(1).channelId());
        assertEquals("jk1", aomori.byRemoteNumber(3).channelId());

        RegionChannelCatalog.Region fukuoka = RegionChannelCatalog.byId("fukuoka");
        assertEquals("jk5", fukuoka.byRemoteNumber(1).channelId());
        assertEquals("jk1", fukuoka.byRemoteNumber(3).channelId());
        assertEquals("jk6", fukuoka.byRemoteNumber(4).channelId());
    }

    @Test
    public void detectsRegionalStationNamesAndMarksMixedAffiliatesUnsupported() {
        RegionChannelCatalog.Region ishikawa = RegionChannelCatalog.byId("ishikawa");
        assertEquals("jk5", ishikawa.detect("051 北陸朝日放送 HAB").channelId());

        RegionChannelCatalog.Station tos =
                RegionChannelCatalog.byId("oita").detect("テレビ大分 TOS");
        assertNotNull(tos);
        assertFalse(tos.supported());
    }
}
