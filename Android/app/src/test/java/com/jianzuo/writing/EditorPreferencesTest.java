package com.jianzuo.writing;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class EditorPreferencesTest {
    @Test public void legacyShortcutsKeepMultilinePipesAndLiteralCharacters() {
        ShortcutConfig config = ShortcutConfig.load("", "备注|\\n> 中文|𠀀\ninvalid\n美元|$1\\路径");
        assertEquals(15, config.items.size()); assertEquals("\n> 中文|𠀀", config.items.get(13).value());
        assertEquals("$1\\路径", config.items.get(14).value());
        ShortcutConfig restored = ShortcutConfig.load(config.encode(), "不会覆盖|旧项");
        assertEquals(config.items, restored.items);
    }
    @Test public void reorderedHiddenAndDuplicateLabelsRoundTripWithoutMixingIdentity() throws Exception {
        List<ShortcutConfig.Item> entries = new ArrayList<>(ShortcutConfig.BUILTINS);
        entries.set(0, entries.get(0).enabled(false));
        entries.add(0, ShortcutConfig.item("custom:one", "日期", "一个|\n𠀀")); entries.add(1, ShortcutConfig.item("custom:two", "日期", "$1\\n"));
        ShortcutConfig config = ShortcutConfig.decode(new ShortcutConfig(entries, 3).encode());
        assertEquals(entries, config.items); assertEquals(3, config.rows);
        assertEquals("custom:one", config.visibleRows().get(0).get(0).id());
        assertEquals(14, config.visibleRows().stream().mapToInt(List::size).sum());
        assertEquals("$1\\n", config.items.get(1).value());
    }
    @Test public void emptyAndSmallToolbarsHaveNoEmptyRowsOrDivisionByZero() {
        List<ShortcutConfig.Item> hidden = ShortcutConfig.BUILTINS.stream().map(item -> item.enabled(false)).toList();
        assertTrue(new ShortcutConfig(hidden, 3).visibleRows().isEmpty());
        List<ShortcutConfig.Item> entries = new ArrayList<>(hidden); entries.add(ShortcutConfig.item("custom:single", "一句", "正文"));
        assertEquals(1, new ShortcutConfig(entries, 3).visibleRows().size());
    }
    @Test public void invalidConfigurationsCannotReplaceTheWorkingCopy() {
        List<ShortcutConfig.Item> original = new ArrayList<>(ShortcutConfig.BUILTINS), invalid = new ArrayList<>(original);
        invalid.add(ShortcutConfig.item("custom:long", "a".repeat(25), "text"));
        assertThrows(IllegalArgumentException.class, () -> new ShortcutConfig(invalid, 1));
        invalid.clear(); invalid.addAll(original); invalid.add(ShortcutConfig.item("undo", "假撤销", "overwrite"));
        assertThrows(IllegalArgumentException.class, () -> new ShortcutConfig(invalid, 1));
        assertThrows(IllegalArgumentException.class, () -> new ShortcutConfig(original.subList(1, original.size()), 1));
        assertThrows(IllegalArgumentException.class, () -> new ShortcutConfig(original, 0));
        assertEquals(ShortcutConfig.BUILTINS, original);
    }
    @Test public void damagedSettingsRecoverLegacyAndEnforceCustomLimits() {
        assertEquals("原文", ShortcutConfig.load("{broken", "旧项|原文").items.get(13).value());
        List<ShortcutConfig.Item> entries = new ArrayList<>(ShortcutConfig.BUILTINS);
        for (int i = 0; i < 31; i++) entries.add(ShortcutConfig.item("custom:" + i, "项" + i, "字"));
        assertThrows(IllegalArgumentException.class, () -> new ShortcutConfig(entries, 1));
        entries.clear(); entries.addAll(ShortcutConfig.BUILTINS); entries.add(ShortcutConfig.item("custom:big", "大项", "字".repeat(8001)));
        assertThrows(IllegalArgumentException.class, () -> new ShortcutConfig(entries, 1));
    }
    @Test public void countingModesUseUnicodeCodePointsAndTreatAllWhitespaceConsistently() {
        String source = "甲𠀀 A-b\t\u00a0！\n🙂";
        assertEquals(3, WritingMetrics.Mode.MIXED.count(source)); assertEquals(11, WritingMetrics.Mode.CHARACTERS.count(source));
        assertEquals(7, WritingMetrics.Mode.NONSPACE.count(source)); assertEquals(2, WritingMetrics.Mode.HAN.count(source));
        for (WritingMetrics.Mode mode : WritingMetrics.Mode.values()) assertEquals(0, mode.count(""));
        assertEquals(WritingMetrics.Mode.MIXED, WritingMetrics.Mode.at(-1)); assertEquals(WritingMetrics.Mode.MIXED, WritingMetrics.Mode.at(100));
    }
}
