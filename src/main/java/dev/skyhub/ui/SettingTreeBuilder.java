package dev.skyhub.ui;

import dev.skyhub.model.FeatureCategory;
import dev.skyhub.model.ModCatalog;
import dev.skyhub.model.SettingEntry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Builds a recursive collapsible section tree (iPad Settings style) from a flat list of
 * SettingEntry items and flattens visible rows based on currently expanded section keys.
 */
public final class SettingTreeBuilder {

    public static final class SectionNode {
        private final String key;
        private final String title;
        private final int depth;
        private final int accentColor;
        private final String modBadge; // non-null if this section represents a mod
        private final Map<String, SectionNode> children = new LinkedHashMap<>();
        private final List<SettingEntry> entries = new ArrayList<>();
        private int totalSettings;

        public SectionNode(String key, String title, int depth, int accentColor, String modBadge) {
            this.key = key;
            this.title = title;
            this.depth = depth;
            this.accentColor = accentColor;
            this.modBadge = modBadge;
        }

        public String key() { return key; }
        public String title() { return title; }
        public int depth() { return depth; }
        public int accentColor() { return accentColor; }
        public String modBadge() { return modBadge; }
        public Map<String, SectionNode> children() { return children; }
        public List<SettingEntry> entries() { return entries; }
        public int totalSettings() { return totalSettings; }
    }

    public sealed interface VisibleRow permits SectionHeaderRow, SettingItemRow {
        int depth();
        int height();
    }

    public record SectionHeaderRow(
        SectionNode node,
        boolean expanded,
        int depth
    ) implements VisibleRow {
        @Override
        public int height() {
            return depth == 0 ? 30 : 26;
        }
    }

    public record SettingItemRow(
        SettingEntry entry,
        int depth,
        boolean firstInGroup,
        boolean lastInGroup
    ) implements VisibleRow {
        @Override
        public int height() {
            return 38;
        }
    }

    private SettingTreeBuilder() {}

    /**
     * Builds top-level SectionNodes recursively from filtered settings.
     *
     * @param settings        matching settings
     * @param includeModRoot  true when viewing By Type or global search (groups by Mod -> Category -> Subcategories),
     *                        false when viewing a single selected Mod (groups by Category -> Subcategories)
     * @param redundantPrefix optional sidebar category title to skip if duplicated as the first segment
     */
    public static List<SectionNode> buildTree(
        List<SettingEntry> settings,
        boolean includeModRoot,
        FeatureCategory redundantPrefix
    ) {
        if (settings == null || settings.isEmpty()) {
            return List.of();
        }

        Map<String, SectionNode> roots = new LinkedHashMap<>();
        String skipLower = redundantPrefix != null ? redundantPrefix.displayName().toLowerCase(Locale.ROOT) : "";

        for (SettingEntry entry : settings) {
            List<String> segments = extractPathSegments(entry, includeModRoot, skipLower);
            if (segments.isEmpty()) {
                segments = List.of("General");
            }

            Map<String, SectionNode> level = roots;
            StringBuilder keyBuilder = new StringBuilder();
            SectionNode current = null;

            for (int depth = 0; depth < segments.size(); depth++) {
                String seg = segments.get(depth);
                if (!keyBuilder.isEmpty()) keyBuilder.append('\u001F');
                keyBuilder.append(seg.toLowerCase(Locale.ROOT));
                String key = keyBuilder.toString();

                boolean isModNode = includeModRoot && depth == 0;
                int accent = isModNode ? entry.modAccentColor() : SkyHubTheme.ACCENT_CYAN;
                String badge = isModNode ? ModCatalog.shortLabel(entry.modId(), entry.modName()) : null;

                final int d = depth;
                current = level.computeIfAbsent(key, k -> new SectionNode(k, seg, d, accent, badge));
                level = current.children;
            }

            if (current != null) {
                current.entries.add(entry);
            }
        }

        List<SectionNode> rootList = new ArrayList<>(roots.values());
        for (SectionNode root : rootList) {
            computeTotals(root);
        }

        // Preserve logical order (mods by curated priority when includeModRoot, else alphabetical)
        if (includeModRoot) {
            // Roots already roughly in search/priority order; keep stable insertion order
        } else {
            rootList.sort(Comparator.comparing(SectionNode::title, String.CASE_INSENSITIVE_ORDER));
        }

        return rootList;
    }

    private static List<String> extractPathSegments(SettingEntry entry, boolean includeModRoot, String skipLower) {
        List<String> out = new ArrayList<>(4);
        if (includeModRoot) {
            out.add(entry.modName());
        }

        String cat = entry.category() != null ? entry.category().trim() : "";
        if (!cat.isBlank() && !isRedundant(cat, skipLower) && !cat.equalsIgnoreCase(entry.modName())) {
            out.add(cat);
        }

        String sub = entry.subcategory() != null ? entry.subcategory().trim() : "";
        if (!sub.isBlank()) {
            // Split nested subcategories like "A / B" or "A › B"
            String[] parts = sub.split("\\s*[/\u203A>]\\s*");
            for (String p : parts) {
                String trimmed = p.trim();
                if (trimmed.isBlank()) continue;
                if (isRedundant(trimmed, skipLower)) continue;
                if (!out.isEmpty() && out.getLast().equalsIgnoreCase(trimmed)) continue;
                out.add(trimmed);
            }
        }

        // Ensure at least one section header exists
        if (out.isEmpty()) {
            out.add(cat.isBlank() ? "General" : cat);
        }
        return out;
    }

    private static boolean isRedundant(String segment, String skipLower) {
        if (skipLower.isEmpty()) return false;
        String lower = segment.toLowerCase(Locale.ROOT);
        return lower.equals(skipLower)
            || (skipLower.contains("&") && skipLower.contains(lower) && lower.length() >= 4);
    }

    private static int computeTotals(SectionNode node) {
        int sum = node.entries.size();
        for (SectionNode child : node.children.values()) {
            sum += computeTotals(child);
        }
        node.totalSettings = sum;
        return sum;
    }

    /**
     * Flattens visible section headers and setting rows respecting collapsed/expanded state.
     * When {@code forceExpandAll} is true (e.g. active search query), all sections render expanded.
     */
    public static List<VisibleRow> flatten(
        List<SectionNode> roots,
        Set<String> expandedKeys,
        boolean forceExpandAll
    ) {
        List<VisibleRow> rows = new ArrayList<>();
        for (SectionNode root : roots) {
            flattenNode(root, expandedKeys, forceExpandAll, rows);
        }
        return rows;
    }

    private static void flattenNode(
        SectionNode node,
        Set<String> expandedKeys,
        boolean forceExpandAll,
        List<VisibleRow> out
    ) {
        boolean expanded = forceExpandAll || expandedKeys.contains(node.key());
        out.add(new SectionHeaderRow(node, expanded, node.depth()));
        if (!expanded) {
            return;
        }

        // Direct settings in this section appear first as a grouped iPad-style inset card
        List<SettingEntry> entries = node.entries();
        int count = entries.size();
        for (int i = 0; i < count; i++) {
            out.add(new SettingItemRow(entries.get(i), node.depth() + 1, i == 0, i == count - 1));
        }

        // Recursive child sections underneath
        for (SectionNode child : node.children().values()) {
            flattenNode(child, expandedKeys, forceExpandAll, out);
        }
    }
}
