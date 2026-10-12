package com.jianzuo.writing;

import java.time.LocalDate;
import java.util.Map;

public final class WritingStats {
    public record Year(long words, int activeDays, int bestStreak, int bestDayWords, LocalDate bestDay, long[] months) {}
    public record TimeYear(long millis, int activeDays, long[] months) {}
    public static TimeYear timeYear(Map<String, Long> days, int year) {
        long millis = 0; int active = 0; long[] months = new long[12];
        for (LocalDate date = LocalDate.of(year, 1, 1); date.getYear() == year; date = date.plusDays(1)) {
            long time = Math.max(0, days.getOrDefault(date.toString(), 0L));
            millis += time; months[date.getMonthValue() - 1] += time; if (time > 0) active++;
        }
        return new TimeYear(millis, active, months);
    }
    public static Year year(Map<String, Integer> days, int year) {
        long words = 0; int active = 0, streak = 0, bestStreak = 0, best = 0; LocalDate bestDay = null;
        long[] months = new long[12];
        for (LocalDate date = LocalDate.of(year, 1, 1); date.getYear() == year; date = date.plusDays(1)) {
            int count = Math.max(0, days.getOrDefault(date.toString(), 0));
            words += count; months[date.getMonthValue() - 1] += count;
            if (count > 0) { active++; bestStreak = Math.max(bestStreak, ++streak); } else streak = 0;
            if (count > best) { best = count; bestDay = date; }
        }
        return new Year(words, active, bestStreak, best, bestDay, months);
    }
    public static int intensity(int words, int goal) {
        if (words <= 0) return 0;
        double ratio = (double) words / Math.max(1, goal);
        return ratio < .25 ? 1 : ratio < .5 ? 2 : ratio < 1 ? 3 : 4;
    }
    private WritingStats() {}
}
