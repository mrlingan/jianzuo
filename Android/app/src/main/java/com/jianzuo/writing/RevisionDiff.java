package com.jianzuo.writing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Line diff, from the saved revision to today's text. Work is bounded for long novels. */
public final class RevisionDiff {
    public record Line(char kind, String text) {}
    public record Result(List<Line> lines, boolean grouped) {}
    public static Result compare(String before, String after) {
        String[] a = lines(before), b = lines(after);
        int prefix = 0, suffix = 0;
        while (prefix < Math.min(a.length, b.length) && a[prefix].equals(b[prefix])) prefix++;
        while (suffix < Math.min(a.length, b.length) - prefix && a[a.length - 1 - suffix].equals(b[b.length - 1 - suffix])) suffix++;
        List<Line> result = new ArrayList<>();
        for (int i = 0; i < prefix; i++) result.add(new Line(' ', a[i]));
        int n = a.length - prefix - suffix, m = b.length - prefix - suffix;
        boolean grouped = (long) (n + 1) * (m + 1) > 1_000_000;
        if (grouped || n == 0 || m == 0) {
            for (int i = 0; i < n; i++) result.add(new Line('-', a[prefix + i]));
            for (int j = 0; j < m; j++) result.add(new Line('+', b[prefix + j]));
        } else {
            int[][] lcs = new int[n + 1][m + 1];
            for (int i = n - 1; i >= 0; i--) for (int j = m - 1; j >= 0; j--)
                lcs[i][j] = a[prefix + i].equals(b[prefix + j]) ? 1 + lcs[i + 1][j + 1] : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
            int i = 0, j = 0;
            while (i < n || j < m) {
                if (i < n && j < m && a[prefix + i].equals(b[prefix + j])) { result.add(new Line(' ', a[prefix + i++])); j++; }
                else if (i < n && (j == m || lcs[i + 1][j] >= lcs[i][j + 1])) result.add(new Line('-', a[prefix + i++]));
                else result.add(new Line('+', b[prefix + j++]));
            }
        }
        for (int i = a.length - suffix; i < a.length; i++) result.add(new Line(' ', a[i]));
        return new Result(result, grouped);
    }
    private static String[] lines(String text) {
        // Retain newline characters so trailing-newline changes are not lost.
        return Arrays.stream(text.split("(?<=\n)", -1)).filter(s -> !s.isEmpty()).toArray(String[]::new);
    }
    private RevisionDiff() {}
}
