package dev.skyhub.ui;

import dev.skyhub.model.FeatureCategory;
import dev.skyhub.model.SettingAccess;
import dev.skyhub.model.SettingEntry;
import dev.skyhub.model.SettingType;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SettingTreeBuilderTest {

    private static SettingEntry setting(String modId, String modName, String cat, String sub, String name) {
        return SettingEntry.builder()
            .id(modId + ":" + cat + ":" + sub + ":" + name)
            .modId(modId)
            .modName(modName)
            .category(cat)
            .subcategory(sub)
            .name(name)
            .type(SettingType.BOOLEAN)
            .access(new SettingAccess() {
                @Override public Object get() { return true; }
                @Override public boolean set(Object v) { return true; }
            })
            .build();
    }

    @Test
    void buildsRecursiveCollapsibleSectionsAndFlattensCorrectly() {
        List<SettingEntry> entries = List.of(
            setting("skyhanni", "SkyHanni", "Ashfang", "Gravity Orbs", "Enabled"),
            setting("skyhanni", "SkyHanni", "Ashfang", "Gravity Orbs", "Color"),
            setting("skyhanni", "SkyHanni", "Ashfang", "Blazing Souls", "Enabled"),
            setting("skyhanni", "SkyHanni", "Ashfang", "", "Freeze Cooldown")
        );

        var roots = SettingTreeBuilder.buildTree(entries, true, FeatureCategory.CRIMSON_ISLE);
        assertEquals(1, roots.size());
        assertEquals("SkyHanni", roots.getFirst().title());
        assertEquals(4, roots.getFirst().totalSettings());

        // Collapsed by default -> only 1 header row visible
        var collapsed = SettingTreeBuilder.flatten(roots, Set.of(), false);
        assertEquals(1, collapsed.size());

        // Expand SkyHanni -> Ashfang child section appears
        Set<String> expanded = new HashSet<>();
        expanded.add(roots.getFirst().key());
        var level1 = SettingTreeBuilder.flatten(roots, expanded, false);
        assertEquals(2, level1.size());

        // Force expand all (search mode) -> all headers + all 4 settings visible
        var allExpanded = SettingTreeBuilder.flatten(roots, Set.of(), true);
        long settingCount = allExpanded.stream().filter(r -> r instanceof SettingTreeBuilder.SettingItemRow).count();
        long headerCount = allExpanded.stream().filter(r -> r instanceof SettingTreeBuilder.SectionHeaderRow).count();
        assertEquals(4, settingCount);
        assertEquals(4, headerCount); // SkyHanni, Ashfang, Gravity Orbs, Blazing Souls
    }

    @Test
    void stripsRedundantSelectedCategoryPrefix() {
        List<SettingEntry> entries = List.of(
            setting("skyblocker", "Skyblocker", "Dungeons", "Solvers / Terminals", "Highlight")
        );

        var roots = SettingTreeBuilder.buildTree(entries, true, FeatureCategory.DUNGEONS);
        var expanded = SettingTreeBuilder.flatten(roots, Set.of(), true);
        // Should be Skyblocker -> Solvers -> Terminals -> Highlight (skipping redundant "Dungeons")
        List<String> headerTitles = expanded.stream()
            .filter(r -> r instanceof SettingTreeBuilder.SectionHeaderRow)
            .map(r -> ((SettingTreeBuilder.SectionHeaderRow) r).node().title())
            .toList();
        assertEquals(List.of("Skyblocker", "Solvers", "Terminals"), headerTitles);
    }
}
