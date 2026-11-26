package dev.skyhub.registry;

import dev.skyhub.model.ModCatalog;
import dev.skyhub.model.SettingEntry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Multi-signal fuzzy search engine over normalized SettingEntry items.
 * Supports multi-token queries (e.g. "odin term", "sh farm", "sb solver"),
 * exact/prefix boosts, acronym matching, and subsequence fuzzy matching.
 */
public final class FuzzySearchIndex {
    public record FilterOptions(
        String query,
        String selectedModId,
        boolean onlyCurrentlyOn,
        boolean onlySkyBlockMods
    ) {}

    public record SearchMatch(SettingEntry entry, int score) {}

    private FuzzySearchIndex() {}

    public static List<SettingEntry> filter(List<SettingEntry> all, FilterOptions opts) {
        if (all == null || all.isEmpty()) {
            return List.of();
        }
        String rawQuery = opts.query() == null ? "" : opts.query().trim().toLowerCase(Locale.ROOT);
        String[] tokens = rawQuery.isEmpty() ? new String[0] : rawQuery.split("\\s+");

        List<SearchMatch> matches = new ArrayList<>(Math.min(all.size(), 256));
        for (SettingEntry entry : all) {
            if (opts.selectedModId() != null && !opts.selectedModId().isBlank()
                && !opts.selectedModId().equals(entry.modId())) {
                continue;
            }
            if (opts.onlySkyBlockMods() && !ModCatalog.isSkyBlockMod(entry.modId())) {
                continue;
            }
            if (opts.onlyCurrentlyOn() && !entry.isCurrentlyOn()) {
                continue;
            }

            if (tokens.length == 0) {
                int baseScore = 10000 - ModCatalog.sortPriority(entry.modId());
                matches.add(new SearchMatch(entry, baseScore));
                continue;
            }

            int score = scoreEntry(entry, rawQuery, tokens);
            if (score > 0) {
                matches.add(new SearchMatch(entry, score));
            }
        }

        if (tokens.length == 0) {
            matches.sort(Comparator
                .comparingInt((SearchMatch m) -> ModCatalog.sortPriority(m.entry().modId()))
                .thenComparing(m -> m.entry().modName(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(m -> m.entry().category(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(m -> m.entry().name(), String.CASE_INSENSITIVE_ORDER));
        } else {
            matches.sort(Comparator
                .comparingInt(SearchMatch::score).reversed()
                .thenComparingInt(m -> ModCatalog.sortPriority(m.entry().modId()))
                .thenComparing(m -> m.entry().name(), String.CASE_INSENSITIVE_ORDER));
        }

        List<SettingEntry> out = new ArrayList<>(matches.size());
        for (SearchMatch m : matches) {
            out.add(m.entry());
        }
        return out;
    }

    public static int scoreEntry(SettingEntry entry, String fullQuery, String[] tokens) {
        String name = entry.name().toLowerCase(Locale.ROOT);
        String category = entry.category().toLowerCase(Locale.ROOT);
        String subcat = entry.subcategory().toLowerCase(Locale.ROOT);
        String modName = entry.modName().toLowerCase(Locale.ROOT);
        String modId = entry.modId().toLowerCase(Locale.ROOT);
        String shortLabel = ModCatalog.shortLabel(entry.modId(), entry.modName()).toLowerCase(Locale.ROOT);
        String desc = entry.description().toLowerCase(Locale.ROOT);

        int total = 0;

        // Full-query bonus on title
        if (name.equals(fullQuery)) {
            total += 2000;
        } else if (name.startsWith(fullQuery)) {
            total += 1200;
        } else if (name.contains(fullQuery)) {
            total += 700;
        }

        // Every token must match at least one searchable field (AND semantics)
        for (String token : tokens) {
            int bestTokenScore = 0;

            bestTokenScore = Math.max(bestTokenScore, matchField(name, token, 500, 320, 180, 90));
            bestTokenScore = Math.max(bestTokenScore, matchField(category, token, 240, 170, 100, 45));
            if (!subcat.isEmpty()) {
                bestTokenScore = Math.max(bestTokenScore, matchField(subcat, token, 220, 150, 90, 40));
            }
            bestTokenScore = Math.max(bestTokenScore, matchField(modName, token, 260, 180, 110, 0));
            bestTokenScore = Math.max(bestTokenScore, matchField(modId, token, 250, 170, 100, 0));
            if (shortLabel.equals(token) || shortLabel.startsWith(token)) {
                bestTokenScore = Math.max(bestTokenScore, shortLabel.equals(token) ? 260 : 180);
            }
            for (String tag : entry.searchTags()) {
                String t = tag.toLowerCase(Locale.ROOT);
                bestTokenScore = Math.max(bestTokenScore, matchField(t, token, 300, 200, 120, 50));
            }
            if (!desc.isEmpty()) {
                bestTokenScore = Math.max(bestTokenScore, matchField(desc, token, 110, 80, 60, 0));
            }

            if (bestTokenScore == 0) {
                return 0;
            }
            total += bestTokenScore;
        }

        // Slight tie-break boost for currently ON settings
        if (entry.isCurrentlyOn()) {
            total += 15;
        }

        return total;
    }

    private static int matchField(String field, String token, int exact, int prefix, int contains, int fuzzy) {
        if (field.isEmpty() || token.isEmpty()) {
            return 0;
        }
        if (field.equals(token)) {
            return exact;
        }
        if (field.startsWith(token)) {
            return prefix;
        }
        int idx = field.indexOf(token);
        if (idx >= 0) {
            // Word boundary bonus
            char prev = field.charAt(idx - 1);
            if (prev == ' ' || prev == '_' || prev == '-' || prev == '/') {
                return contains + 60;
            }
            return contains;
        }
        if (fuzzy > 0 && token.length() >= 2 && isSubsequence(field, token)) {
            // Favor denser subsequences
            int compactness = Math.max(1, field.length() - token.length());
            return Math.max(15, fuzzy - Math.min(fuzzy / 2, compactness * 2));
        }
        return 0;
    }

    public static boolean isSubsequence(String haystack, String needle) {
        int hi = 0;
        int ni = 0;
        while (hi < haystack.length() && ni < needle.length()) {
            if (haystack.charAt(hi) == needle.charAt(ni)) {
                ni++;
            }
            hi++;
        }
        return ni == needle.length();
    }
}
