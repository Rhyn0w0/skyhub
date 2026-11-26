package dev.skyhub.model;

import java.util.Locale;

/**
 * Unified Hypixel SkyBlock feature categories for grouping settings by type
 * (Mod Settings, Dungeons, Mining, Farming, Fishing, Slayers, etc.) across all installed mods.
 */
public enum FeatureCategory {
    MOD_SETTINGS("Mod Settings", 0xFF94A3B8),
    DUNGEONS("Dungeons", 0xFFA855F7),
    MINING("Mining", 0xFF06B6D4),
    FARMING("Farming & Garden", 0xFF22C55E),
    FISHING("Fishing", 0xFF3B82F6),
    SLAYERS("Slayers", 0xFFEF4444),
    CRIMSON_ISLE("Crimson Isle", 0xFFF97316),
    THE_END("The End", 0xFFD946EF),
    FORAGING("Foraging & Hunting", 0xFF10B981),
    COMBAT("Combat & Bosses", 0xFFF43F5E),
    ECONOMY("Economy & Bazaar", 0xFFF59E0B),
    INVENTORY("Inventory & Items", 0xFFEAB308),
    CHAT("Chat & Party", 0xFF6366F1),
    HUD("HUD & Visuals", 0xFF38BDF8),
    GENERAL("General & Misc", 0xFF64748B);

    private final String displayName;
    private final int accentColor;

    FeatureCategory(String displayName, int accentColor) {
        this.displayName = displayName;
        this.accentColor = accentColor;
    }

    public String displayName() {
        return displayName;
    }

    public int accentColor() {
        return accentColor;
    }

    /**
     * Classifies any normalized SettingEntry into a unified SkyBlock feature type
     * based on its category, subcategory, mod, name, and tags.
     */
    public static FeatureCategory classify(SettingEntry entry) {
        if (entry == null) return GENERAL;

        String modId = entry.modId().toLowerCase(Locale.ROOT);
        String cat = entry.category().toLowerCase(Locale.ROOT);
        String sub = entry.subcategory().toLowerCase(Locale.ROOT);
        String name = entry.name().toLowerCase(Locale.ROOT);
        String primary = cat + " " + sub;
        String full = primary + " " + name;

        // 1. Meta / mod-management settings always go to Mod Settings (e.g. Update Stream, About, Debug, Repo, PackCore)
        if (isModSetting(modId, cat, primary, full)) {
            return MOD_SETTINGS;
        }

        // 2. Single-domain mod strong hints
        if ("feesh".equals(modId)) return FISHING;
        if ("btrbz".equals(modId) || "marketguard".equals(modId)) return ECONOMY;
        if ("customscoreboard".equals(modId)) return HUD;
        if ("enhanced_chat".equals(modId) || "better_hypixel_chat".equals(modId)) return CHAT;
        if ("enhanced_storage".equals(modId) || "inventorybuttons".equals(modId)) return INVENTORY;
        if ("ping-offset-miner".equals(modId)) return MINING;

        // 3. Primary category/subcategory classification
        FeatureCategory fromCat = matchKeywords(primary);
        if (fromCat != null) {
            return fromCat;
        }

        // 4. Fallback to full title + category
        FeatureCategory fromFull = matchKeywords(full);
        if (fromFull != null) {
            return fromFull;
        }

        return GENERAL;
    }

    /**
     * Classifies a HUD widget into a unified SkyBlock feature type for HUD Canvas filtering.
     */
    public static FeatureCategory classify(HudWidget widget) {
        if (widget == null) return HUD;

        String modId = widget.modId().toLowerCase(Locale.ROOT);
        String primary = (widget.category() + " " + widget.name()).toLowerCase(Locale.ROOT);

        if ("feesh".equals(modId)) return FISHING;
        if ("btrbz".equals(modId) || "marketguard".equals(modId)) return ECONOMY;
        if ("customscoreboard".equals(modId)) return HUD;
        if ("enhanced_chat".equals(modId) || "better_hypixel_chat".equals(modId)) return CHAT;
        if ("ping-offset-miner".equals(modId)) return MINING;

        FeatureCategory matched = matchKeywords(primary);
        if (matched != null) {
            return matched;
        }
        return HUD;
    }

    private static boolean isModSetting(String modId, String cat, String primary, String full) {
        if ("packcore".equals(modId)) {
            return true;
        }
        if (cat.equals("about") || cat.equals("debug") || cat.equals("dev") || cat.equals("developer")
            || cat.equals("config") || cat.equals("updates") || cat.equals("updater")
            || cat.equals("repository") || cat.equals("repo")) {
            return true;
        }
        return containsAny(primary,
            "about", "update stream", "auto update", "auto-update", "updater", "changelog",
            "debug", "developer", "dev ", " dev", "repo ", "repository", "neu repo",
            "config backup", "backup", "telemetry", "analytics", "api key", "hypixel api",
            "modmenu", "packcore", "setup wizard", "crash", "logging", "migration", "compat"
        ) || containsAny(full,
            "update stream", "auto update", "auto-update", "check for updates", "update checker",
            "download updates", "notify updates", "debug mode", "dev mode", "developer mode",
            "repo branch", "repo user", "repo name", "auto-update repo", "config backup",
            "api key", "config style", "mod version"
        );
    }

    private static FeatureCategory matchKeywords(String s) {
        if (containsAny(s, "dungeon", "catacomb", "floor 7", "f7", "m7", "kuudra", "terminal",
            "livid", "secret", "puzzle", "blaze", "tic tac", "leap", "melody", "wither",
            "blood room", "spirit bear", "watcher", "boss", "simon says", "water board",
            "creeper beams", "boulder", "silverfish", "weirdos", "croesus", "dungeon map", "score calc")) {
            if (s.contains("kuudra")) return CRIMSON_ISLE;
            return DUNGEONS;
        }
        if (containsAny(s, "mining", "dwarven", "crystal hollow", "glacite", "powder", "mithril",
            "gemstone", "commission", "drill", "pickobulus", "mineshaft", "corpse", "nucleus",
            "metal detector", "wishing compass", "forge", "hotm", "goblin", "titanium", "fossil")) {
            return MINING;
        }
        if (containsAny(s, "garden", "farming", "crop", "pest", "jacob", "composter", "visitor",
            "greenhouse", "plot", "contest", "farming fortune", "barn", "pelt", "finnegan",
            "sprayonator", "vacuum", "pitchin", "fermento", "melons", "wheat", "carrot", "potato")) {
            return FARMING;
        }
        if (containsAny(s, "fishing", "sea creature", "trophy fish", "bobber", "lava fish",
            "chum", "jawbus", "thunder", "plhlegblast", "barn fish", "worm", "rod", "shark",
            "hotspot", "backwater", "bayou", "feesh")) {
            return FISHING;
        }
        if (containsAny(s, "slayer", "revenant", "tarantula", "sven", "voidgloom", "inferno demon",
            "riftstalker", "vampire", "blaze slayer", "eman", "enderman slayer", "beacon",
            "yang glyph", "attunement", "quazii", "minis")) {
            return SLAYERS;
        }
        if (containsAny(s, "crimson", "ashfang", "kuudra", "dojo", "mage", "barbarian", "vanquisher",
            "volcano", "sulphur", "blazing", "nether", "magma boss", "matriarch", "elle", "faction")) {
            return CRIMSON_ISLE;
        }
        if (containsAny(s, "the end", "end island", "dragon", "zealot", "endstone", "ender node",
            "golem", "end race", "void sepulture", "enderman")) {
            return THE_END;
        }
        if (containsAny(s, "foraging", "galatea", "park", "hunting", "tree", "sweep", "lasso",
            "axe", "log", "forest", "trapper", "shard", "attribute")) {
            return FORAGING;
        }
        if (containsAny(s, "bazaar", "auction", "ah ", "economy", "price", "networth", "coins",
            "profit", "flip", "purse", "bank", "bits", "market", "lowest bin", "lbin", "npc sell",
            "sack", "trade", "scam")) {
            return ECONOMY;
        }
        if (containsAny(s, "inventory", "storage", "backpack", "ender chest", "wardrobe", "slot",
            "tooltip", "item", "pet", "armor", "equipment", "museum", "accessory", "talisman",
            "recipe", "craft", "anvil", "enchant", "reforge", "experiment", "superpair", "ultrasequencer",
            "chronomatron", "search bar", "hotkey", "lock slot")) {
            return INVENTORY;
        }
        if (containsAny(s, "chat", "party", "guild", "filter", "spam", "mention", "message",
            "whisper", "coop", "compact chat", "auto gg", "pfinder", "party finder")) {
            return CHAT;
        }
        if (containsAny(s, "combat", "diana", "mythological", "burrow", "inquisitor", "griffin",
            "damage", "bestiary", "rift", "vampire", "event", "spooky", "jerry", "hoppity",
            "chocolate", "bingo", "mayor", "carnival", "waypoint")) {
            return COMBAT;
        }
        if (containsAny(s, "hud", "gui", "ui", "visual", "scoreboard", "overlay", "tab", "title",
            "bar", "render", "glow", "highlight", "outline", "color", "colour", "scale", "sound",
            "display", "tracker", "timer", "notification", "alert", "warp", "misc")) {
            return HUD;
        }
        return null;
    }

    private static boolean containsAny(String haystack, String... needles) {
        for (String n : needles) {
            if (haystack.contains(n)) {
                return true;
            }
        }
        return false;
    }
}
