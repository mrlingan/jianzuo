package com.jianzuo.writing;

import org.json.JSONObject;
import org.junit.Test;
import java.time.*;
import java.util.*;
import static org.junit.Assert.*;

public class WritingTimeTest {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final long WALL = ZonedDateTime.of(2026, 10, 8, 12, 0, 0, 0, ZONE).toInstant().toEpochMilli();
    private long total(Map<String, Long> times) { return times.values().stream().mapToLong(Long::longValue).sum(); }
    @Test public void openingAndReadingDoNotStartTime() {
        WritingTime time = new WritingTime(); assertTrue(time.collect(100_000).isEmpty()); assertFalse(time.running());
    }
    @Test public void consecutiveTicksPreserveFractionsWithoutCountingTwice() {
        WritingTime time = new WritingTime(); time.edit(100, WALL, ZONE);
        assertEquals(499, total(time.collect(599))); assertEquals(501, total(time.collect(1100)));
        assertTrue(time.collect(1100).isEmpty()); assertEquals(1, total(time.pause(1101))); assertFalse(time.running());
    }
    @Test public void idleCapsAtSixtySecondsAndAnotherEditStartsFresh() {
        WritingTime time = new WritingTime(); time.edit(0, WALL, ZONE);
        assertEquals(60_000, total(time.collect(3_600_000))); assertFalse(time.running());
        assertTrue(time.edit(3_600_000, WALL + 3_600_000, ZONE).isEmpty());
        assertEquals(2000, total(time.pause(3_602_000)));
    }
    @Test public void pauseRequiresNewEditAndDoesNotCountBackground() {
        WritingTime time = new WritingTime(); time.edit(0, WALL, ZONE); assertEquals(1200, total(time.pause(1200)));
        assertTrue(time.collect(50_000).isEmpty()); assertTrue(time.pause(55_000).isEmpty());
        time.edit(60_000, WALL + 60_000, ZONE); assertEquals(800, total(time.pause(60_800)));
    }
    @Test public void twoEditorsShareOneSessionAndEditsRenewIdleDeadline() {
        WritingTime time = new WritingTime(); time.edit(0, WALL, ZONE);
        assertEquals(40_000, total(time.edit(40_000, WALL + 40_000, ZONE)));
        assertEquals(20_000, total(time.collect(60_000))); assertEquals(40_000, total(time.collect(200_000)));
    }
    @Test public void midnightSplitsActualMillisecondsBetweenLocalDates() {
        WritingTime time = new WritingTime(); long wall = LocalDate.of(2026, 10, 8).atStartOfDay(ZONE).toInstant().toEpochMilli() - 500;
        time.edit(200, wall, ZONE); assertEquals(Map.of("2026-10-07", 500L, "2026-10-08", 1500L), time.pause(2200));
    }
    @Test public void clockChangesDoNotCreateOrSubtractElapsedTime() {
        WritingTime time = new WritingTime(); time.edit(0, WALL, ZONE);
        assertEquals(Map.of("2026-10-08", 2000L), time.edit(2000, WALL - 86_400_000, ZONE));
        assertEquals(Map.of("2026-10-07", 3000L), time.edit(5000, WALL + 86_400_000, ZONE));
        assertEquals(Map.of("2026-10-09", 1000L), time.pause(6000));
    }
    @Test public void zoneChangeUsesOldZoneUntilNextEdit() {
        WritingTime time = new WritingTime(); long wall = Instant.parse("2026-10-08T01:00:00Z").toEpochMilli();
        time.edit(0, wall, ZONE); assertEquals(Map.of("2026-10-08", 1000L), time.edit(1000, wall + 1000, ZoneId.of("America/Los_Angeles")));
        assertEquals(Map.of("2026-10-07", 1000L), time.pause(2000));
    }
    @Test public void daylightSavingTransitionKeepsRealElapsedDuration() {
        for (String wall : List.of("2026-03-08T06:59:30Z", "2026-11-01T05:59:30Z")) {
            WritingTime time = new WritingTime(); time.edit(0, Instant.parse(wall).toEpochMilli(), ZoneId.of("America/New_York"));
            assertEquals(60_000, total(time.pause(60_000)));
        }
    }
    @Test public void monotonicClockResetStopsSession() {
        WritingTime time = new WritingTime(); time.edit(2000, WALL, ZONE);
        assertTrue(time.collect(1000).isEmpty()); assertFalse(time.running());
    }
    @Test public void schemaFourAndBackupRoundTripKeepDurationAndEmptyVolumes() throws Exception {
        Library library = new Library(); library.addSection(library.notebooks.get(0), "空卷"); library.dailyWritingMillis.put("2026-10-08", 12345L);
        assertEquals(4, new JSONObject(LibraryCodec.encode(library)).getInt("version"));
        Library restored = BackupCodec.decode(BackupCodec.encode(library)); assertEquals(library.dailyWritingMillis, restored.dailyWritingMillis); assertEquals("空卷", restored.sections.get(0).name);
        JSONObject damaged = new JSONObject(BackupCodec.encode(library)); damaged.put("payload", damaged.getString("payload").replace("12345", "54321"));
        assertThrows(Exception.class, () -> BackupCodec.decode(damaged.toString()));
    }
    @Test public void oldSchemasMigrateWithoutInventingWritingTime() throws Exception {
        Library library = new Library(); Document doc = library.create("标题", "旧正文", library.notebooks.get(0)); library.assignSection(doc, library.addSection(doc.notebook, "旧卷")); library.dailyWords.put("2026-10-01", 80);
        for (int version = 1; version <= 3; version++) {
            JSONObject old = new JSONObject(LibraryCodec.encode(library)); old.put("version", version); old.remove("dailyWritingMillis");
            if (version < 3) old.remove("sections");
            Library restored = LibraryCodec.decode(old.toString()); assertTrue(restored.dailyWritingMillis.isEmpty()); assertEquals("旧正文", restored.documents.get(0).body);
            if (version >= 2) assertEquals(80, (int) restored.dailyWords.get("2026-10-01"));
        }
    }
    @Test public void rejectsImpossibleDatesCoercionAndInvalidDurations() throws Exception {
        String[] dates = {"2026-02-30", "2026-13-01", "2026-1-01", "not-a-date"};
        for (String day : dates) {
            JSONObject root = new JSONObject(LibraryCodec.encode(new Library())); root.put("dailyWritingMillis", new JSONObject().put(day, 1000));
            assertThrows(Exception.class, () -> LibraryCodec.decode(root.toString()));
        }
        for (Object value : List.of(-1, 1.5, "1000", true, WritingTime.MAX_DAY_MILLIS + 1)) {
            JSONObject root = new JSONObject(LibraryCodec.encode(new Library())); root.put("dailyWritingMillis", new JSONObject().put("2026-10-08", value));
            assertThrows(Exception.class, () -> LibraryCodec.decode(root.toString()));
        }
        JSONObject missing = new JSONObject(LibraryCodec.encode(new Library())); missing.remove("dailyWritingMillis");
        assertThrows(Exception.class, () -> LibraryCodec.decode(missing.toString()));
    }
    @Test public void statisticsRestoreIsOptInAndRepeatedMergeTakesDailyMaximum() {
        Library local = new Library(), incoming = new Library(); local.dailyWords.put("2026-10-08", 60); local.dailyWritingMillis.put("2026-10-08", 5000L);
        incoming.dailyWords.put("2026-10-08", 40); incoming.dailyWritingMillis.put("2026-10-08", 6000L); incoming.dailyWritingMillis.put("2026-10-07", 1000L);
        local.merge(incoming); assertEquals(Map.of("2026-10-08", 5000L), local.dailyWritingMillis);
        local.merge(incoming, true); local.merge(incoming, true);
        assertEquals(Map.of("2026-10-08", 6000L, "2026-10-07", 1000L), local.dailyWritingMillis); assertEquals(60, (int) local.dailyWords.get("2026-10-08"));
    }
    @Test public void annualDurationIncludesLeapDayButNotOtherYearsOrInvalidDates() {
        var stats = WritingStats.timeYear(Map.of("2024-02-29", 1234L, "2024-03-01", 2000L, "2023-12-31", 99L, "2024-02-30", 9999L), 2024);
        assertEquals(3234, stats.millis()); assertEquals(2, stats.activeDays()); assertEquals(1234, stats.months()[1]); assertEquals(2000, stats.months()[2]);
        assertEquals("0 秒", WritingTime.format(0)); assertEquals("1 分 5 秒", WritingTime.format(65_000)); assertEquals("2 小时 1 分", WritingTime.format(7_260_000));
    }
}
