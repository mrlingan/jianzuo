package com.jianzuo.writing;

import org.junit.Test;
import java.util.HashSet;
import static org.junit.Assert.*;

public class WritingAppearanceTest {
    @Test public void oldMissingAndUnknownThemesKeepTheOriginalPalette() {
        for (boolean dark : new boolean[]{false, true}) for (String id : new String[]{null, "", "future-theme", "CLASSIC"}) {
            assertEquals(WritingAppearance.palette("classic", dark), WritingAppearance.palette(id, dark));
            assertEquals(0, WritingAppearance.index(id));
        }
        assertEquals(0xFFFAF7F1, WritingAppearance.palette("classic", false).paper());
        assertEquals(0xFF302E29, WritingAppearance.palette("classic", false).ink());
        assertEquals(0xFF1D1C1A, WritingAppearance.palette("classic", true).paper());
        assertEquals(0xFFEDE7DD, WritingAppearance.palette("classic", true).ink());
    }
    @Test public void presetsHaveStableUniqueIdsAndDifferentDayAndNightColors() {
        assertEquals(5, WritingAppearance.IDS.length); assertEquals(5, new HashSet<>(java.util.List.of(WritingAppearance.IDS)).size());
        HashSet<Integer> light = new HashSet<>(), dark = new HashSet<>();
        for (int i = 0; i < WritingAppearance.IDS.length; i++) {
            String id = WritingAppearance.IDS[i]; assertEquals(i, WritingAppearance.index(id));
            var day = WritingAppearance.palette(id, false); var night = WritingAppearance.palette(id, true);
            assertNotEquals(day, night); light.add(day.paper()); dark.add(night.paper());
        }
        assertEquals(5, light.size()); assertEquals(5, dark.size());
    }
    @Test public void allPaletteColorsAreOpaqueAndTextRemainsReadableOnEverySurface() {
        for (String id : WritingAppearance.IDS) for (boolean dark : new boolean[]{false, true}) {
            var p = WritingAppearance.palette(id, dark);
            for (int color : new int[]{p.paper(), p.sidebar(), p.ink(), p.muted(), p.accent(), p.line(), p.card()}) assertEquals(255, color >>> 24);
            for (int background : new int[]{p.paper(), p.sidebar(), p.card()}) {
                assertTrue(id + " body contrast", contrast(p.ink(), background) >= 7);
                assertTrue(id + " muted contrast", contrast(p.muted(), background) >= 3);
                if (!id.equals("classic")) assertTrue(id + " new muted contrast", contrast(p.muted(), background) >= 4.5);
            }
            assertTrue(id + " accent contrast", contrast(p.accent(), p.paper()) >= 3);
            assertTrue(id + " filled button contrast", contrast(WritingAppearance.foregroundOn(p.accent()), p.accent()) >= 4.5);
            for (int level = 0; level <= 4; level++) {
                float mix = level / 4f; int color = 0xFF000000;
                for (int shift : new int[]{16, 8, 0}) color |= Math.round(((p.sidebar() >> shift) & 255) * (1 - mix) + ((p.accent() >> shift) & 255) * mix) << shift;
                assertTrue(id + " statistics cell contrast at " + level, contrast(WritingAppearance.foregroundOn(color), color) >= 4.5);
            }
        }
    }
    private double contrast(int first, int second) {
        double a = luminance(first), b = luminance(second); return (Math.max(a, b) + .05) / (Math.min(a, b) + .05);
    }
    private double luminance(int color) { return channel((color >> 16) & 255)*.2126 + channel((color >> 8) & 255)*.7152 + channel(color & 255)*.0722; }
    private double channel(int value) { double c = value/255.0; return c <= .04045 ? c/12.92 : Math.pow((c+.055)/1.055, 2.4); }
}
