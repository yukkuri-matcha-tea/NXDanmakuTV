package com.yukkurimatchatea.nxdanmakutv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class ChannelCatalogTest {
    @Test
    public void detectsTokyoAndAffiliateNames() {
        assertEquals("jk6", ChannelCatalog.detect("061 TBSテレビ").id());
        assertEquals("jk6", ChannelCatalog.detect("4 毎日放送 MBS").id());
        assertEquals("jk4", ChannelCatalog.detect("読売テレビ 10").id());
        assertEquals("jk8", ChannelCatalog.detect("関西テレビ").id());
        assertEquals("jk9", ChannelCatalog.detect("TOKYO MX1").id());
    }

    @Test
    public void ignoresUnknownText() {
        assertNull(ChannelCatalog.detect("番組表"));
        assertNull(ChannelCatalog.detect("NEXT PROGRAM"));
        assertNull(ChannelCatalog.detect(null));
    }

    @Test
    public void detectsShortLatinAliasOnlyAsWholeToken() {
        assertEquals("jk5", ChannelCatalog.detect("テレビ朝日 EX").id());
    }

    @Test
    public void followsCatalogOrder() {
        assertEquals("jk5", ChannelCatalog.adjacent("jk4", 1).id());
        assertEquals("jk10", ChannelCatalog.adjacent("jk1", -1).id());
    }
}
