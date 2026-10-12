package com.jianzuo.writing;

import java.time.Instant;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

/** One foreground session for both editors. Elapsed time measures duration; wall time only assigns dates. */
public final class WritingTime {
    public static final long IDLE_MILLIS = 60_000;
    // Allows a repeated local date after travel across time zones without accepting corrupt enormous totals.
    public static final long MAX_DAY_MILLIS = 48 * 60 * 60 * 1000L;
    private boolean running;
    private long anchorElapsed, anchorWall, lastEdit, cursor;
    private ZoneId zone;

    public Map<String, Long> edit(long elapsed, long wall, ZoneId zone) {
        Map<String, Long> result = collect(elapsed);
        running = true; anchorElapsed = cursor = lastEdit = elapsed; anchorWall = wall; this.zone = zone;
        return result;
    }
    public Map<String, Long> collect(long elapsed) {
        Map<String, Long> result = new LinkedHashMap<>();
        if (!running) return result;
        if (elapsed < cursor) { running = false; return result; }
        long end = Math.min(elapsed, lastEdit + IDLE_MILLIS);
        long from = anchorWall + cursor - anchorElapsed, to = anchorWall + end - anchorElapsed;
        while (from < to) {
            var date = Instant.ofEpochMilli(from).atZone(zone).toLocalDate();
            long boundary = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
            long next = Math.min(to, boundary);
            result.merge(date.toString(), next - from, Long::sum); from = next;
        }
        cursor = end;
        if (elapsed >= lastEdit + IDLE_MILLIS) running = false;
        return result;
    }
    public Map<String, Long> pause(long elapsed) {
        Map<String, Long> result = collect(elapsed); running = false; return result;
    }
    public boolean running() { return running; }
    public static String format(long millis) {
        long seconds = Math.max(0, millis) / 1000;
        if (seconds < 60) return seconds + " 秒";
        long minutes = seconds / 60;
        return minutes < 60 ? minutes + " 分 " + seconds % 60 + " 秒" : minutes / 60 + " 小时 " + minutes % 60 + " 分";
    }
}
