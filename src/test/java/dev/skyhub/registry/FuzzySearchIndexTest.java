package dev.skyhub.registry;

import dev.skyhub.model.SettingAccess;
import dev.skyhub.model.SettingEntry;
import dev.skyhub.model.SettingType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class FuzzySearchIndexTest {

    private static SettingEntry boolSetting(String modId, String modName, String cat, String name, String desc, boolean initial) {
        AtomicBoolean state = new AtomicBoolean(initial);
        return SettingEntry.builder()
            .id(modId + ":" + cat + ":" + name)
            .modId(modId)
            .modName(modName)
            .category(cat)
            .name(name)
            .description(desc)
            .type(SettingType.BOOLEAN)
            .access(new SettingAccess() {
                @Override public Object get() { return state.get(); }
                @Override public boolean set(Object v) { state.set(Boolean.TRUE.equals(v)); return true; }
                @Override public Object defaultValue() { return false; }
            })
            .build();
    }

    @Test
    void ranksExactAndMultiTokenModQueriesFirst() {
        List<SettingEntry> entries = List.of(
            boolSetting("skyhanni", "SkyHanni", "Garden", "Farming Fortune Display", "Shows farming fortune HUD", true),
            boolSetting("odin", "Odin", "Dungeons", "Terminal Solver", "Solves Floor 7 terminals", true),
            boolSetting("skyblocker", "Skyblocker", "Dungeons", "Livid Solver", "Highlights real Livid", false),
            boolSetting("skyocean", "SkyOcean", "Fishing", "Sea Creature Tracker", "Tracks catches", true)
        );

        var res = FuzzySearchIndex.filter(entries, new FuzzySearchIndex.FilterOptions("odin term", null, false, true));
        assertEquals(1, res.size());
        assertEquals("Terminal Solver", res.getFirst().name());

        // Short alias search ("sh farm")
        var shRes = FuzzySearchIndex.filter(entries, new FuzzySearchIndex.FilterOptions("sh farm", null, false, true));
        assertEquals(1, shRes.size());
        assertEquals("Farming Fortune Display", shRes.getFirst().name());
    }

    @Test
    void supportsCurrentlyOnFilterAndModFilter() {
        List<SettingEntry> entries = List.of(
            boolSetting("skyblocker", "Skyblocker", "Dungeons", "Livid Solver", "", false),
            boolSetting("skyblocker", "Skyblocker", "Dungeons", "Secret Waypoints", "", true),
            boolSetting("odin", "Odin", "Dungeons", "Terminal Solver", "", true)
        );

        var onOnly = FuzzySearchIndex.filter(entries, new FuzzySearchIndex.FilterOptions("", null, true, true));
        assertEquals(2, onOnly.size());

        var sbOnOnly = FuzzySearchIndex.filter(entries, new FuzzySearchIndex.FilterOptions("", "skyblocker", true, true));
        assertEquals(1, sbOnOnly.size());
        assertEquals("Secret Waypoints", sbOnOnly.getFirst().name());
    }

    @Test
    void matchesFuzzySubsequence() {
        List<SettingEntry> entries = List.of(
            boolSetting("skyhanni", "SkyHanni", "Mining", "Powder Tracker", "Tracks gemstone powder", true)
        );
        var res = FuzzySearchIndex.filter(entries, new FuzzySearchIndex.FilterOptions("pwdr", null, false, true));
        assertEquals(1, res.size());
    }
}
