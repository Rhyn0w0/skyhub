package dev.skyhub.model;

import dev.skyhub.registry.FuzzySearchIndex;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FeatureCategoryTest {

    private static SettingEntry entry(String modId, String category, String subcategory, String name) {
        return SettingEntry.builder()
            .id(modId + ":" + category + ":" + name)
            .modId(modId)
            .modName(modId)
            .category(category)
            .subcategory(subcategory)
            .name(name)
            .type(SettingType.BOOLEAN)
            .access(new SettingAccess() {
                @Override public Object get() { return true; }
                @Override public boolean set(Object v) { return true; }
            })
            .build();
    }

    @Test
    void classifiesSettingsIntoUnifiedSkyBlockTypes() {
        assertEquals(FeatureCategory.MOD_SETTINGS, FeatureCategory.classify(entry("skyhanni", "About", "", "Update Stream")));
        assertEquals(FeatureCategory.MOD_SETTINGS, FeatureCategory.classify(entry("packcore", "General", "", "Config Pack")));
        assertEquals(FeatureCategory.MOD_SETTINGS, FeatureCategory.classify(entry("skyblocker", "Debug", "", "Debug Mode")));
        assertEquals(FeatureCategory.DUNGEONS, FeatureCategory.classify(entry("odin", "Floor 7", "Terminals", "Terminal Solver")));
        assertEquals(FeatureCategory.MINING, FeatureCategory.classify(entry("skyhanni", "Mining", "Crystal Hollows", "Powder Tracker")));
        assertEquals(FeatureCategory.FARMING, FeatureCategory.classify(entry("skyhanni", "Garden", "Pests", "Pest Highlighter")));
        assertEquals(FeatureCategory.FISHING, FeatureCategory.classify(entry("feesh", "General", "", "Bobber Counter")));
        assertEquals(FeatureCategory.CRIMSON_ISLE, FeatureCategory.classify(entry("skyhanni", "Crimson Isle", "Ashfang", "Gravity Orbs")));
        assertEquals(FeatureCategory.SLAYERS, FeatureCategory.classify(entry("skyblocker", "Slayers", "Enderman", "Yang Glyph Warning")));
        assertEquals(FeatureCategory.HUD, FeatureCategory.classify(entry("customscoreboard", "Appearance", "", "Show Purse")));
        assertEquals(FeatureCategory.ECONOMY, FeatureCategory.classify(entry("btrbz", "Flipper", "", "Auto Alert")));
    }

    @Test
    void filtersSearchBySelectedFeatureCategory() {
        List<SettingEntry> all = List.of(
            entry("odin", "Dungeons", "Solvers", "Terminal Solver"),
            entry("skyblocker", "Dungeons", "Map", "Dungeon Map"),
            entry("skyhanni", "Garden", "Crops", "Farming Fortune")
        );

        var dungeons = FuzzySearchIndex.filter(all, new FuzzySearchIndex.FilterOptions("", null, FeatureCategory.DUNGEONS, false, true));
        assertEquals(2, dungeons.size());
    }

    @Test
    void classifiesHudWidgetsIntoFeatureTypes() {
        HudWidget mining = HudWidget.builder()
            .id("sh:powder").modId("skyhanni").modName("SkyHanni").name("Powder Tracker").category("Mining")
            .position(() -> 0, () -> 0, x -> {}, y -> {}).build();
        HudWidget dungeon = HudWidget.builder()
            .id("odin:map").modId("odin").modName("Odin").name("Dungeon Map").category("Dungeons")
            .position(() -> 0, () -> 0, x -> {}, y -> {}).build();
        HudWidget generic = HudWidget.builder()
            .id("cs:sb").modId("customscoreboard").modName("Custom Scoreboard").name("Scoreboard").category("Overlay")
            .position(() -> 0, () -> 0, x -> {}, y -> {}).build();

        assertEquals(FeatureCategory.MINING, FeatureCategory.classify(mining));
        assertEquals(FeatureCategory.DUNGEONS, FeatureCategory.classify(dungeon));
        assertEquals(FeatureCategory.HUD, FeatureCategory.classify(generic));
    }
}
