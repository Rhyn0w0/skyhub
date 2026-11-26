package dev.skyhub.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Curated metadata for the SkyBlock Enhanced 5.4.9 modpack.
 * Provides mod accent colors, display ordering, SkyBlock classification,
 * and fallback native commands if a mod does not register a ModMenu screen.
 */
public final class ModCatalog {
    public record ModMeta(
        String modId,
        String shortLabel,
        int accentColor,
        int sortPriority,
        boolean skyBlockMod,
        String nativeConfigCommand,
        String nativeHudCommand
    ) {}

    private static final Map<String, ModMeta> CATALOG = new LinkedHashMap<>();

    static {
        register("skyhanni", "SH", 0xFFF59E0B, 10, true, "sh", "shgui");
        register("skyblocker", "SB", 0xFF38BDF8, 20, true, "skyblocker config", "skyblocker hud");
        register("odin", "ODIN", 0xFFA855F7, 30, true, "odin", "odin hud");
        register("skyocean", "OCEAN", 0xFF06B6D4, 40, true, "skyocean", "meowdding overlays");
        register("customscoreboard", "CSB", 0xFF22C55E, 50, true, "cs", "meowdding overlays");
        register("feesh", "FEESH", 0xFF3B82F6, 60, true, "feesh", "feesh moveAllGuis");
        register("skyblockpv", "PV", 0xFFEC4899, 70, true, "pv config", null);
        register("stella", "STELLA", 0xFFC084FC, 80, true, "stella", "stella hud");
        register("sbo", "SBO", 0xFFEAB308, 90, true, "sbo", "sbo hud");
        register("packcore", "PACK", 0xFFF97316, 100, true, "packcore", null);
        register("skyblock_enhancements", "SBE+", 0xFF10B981, 110, true, "sbe", null);
        register("skyrecipes", "REC", 0xFF14B8A6, 120, true, null, null);
        register("btrbz", "BTRBZ", 0xFFFBBF24, 130, true, "btrbz", null);
        register("marketguard", "MG", 0xFFEF4444, 140, true, "marketguard", null);
        register("scamscreener", "SCAM", 0xFFF43F5E, 150, true, "scamscreener", null);
        register("modernwarpmenu", "WARP", 0xFF8B5CF6, 160, true, null, null);
        register("enhanced_chat", "CHAT", 0xFF6366F1, 170, true, null, null);
        register("enhanced_storage", "STOR", 0xFF818CF8, 180, true, null, null);
        register("enhancedsoundcontrol", "SFX", 0xFF60A5FA, 190, true, null, null);
        register("better_hypixel_chat", "BHC", 0xFF34D399, 200, true, null, null);
        register("ping-offset-miner", "POM", 0xFF2DD4BF, 210, true, null, null);
        register("inventorybuttons", "INV", 0xFFFB923C, 220, true, null, null);
        register("scaleme", "SCALE", 0xFFA3E635, 230, true, null, null);
        register("compacting", "COMP", 0xFF94A3B8, 240, true, null, null);
        register("catharsis", "CATH", 0xFFE879F9, 250, true, null, null);
        register("firmament", "FIRM", 0xFF60A5FA, 260, true, "firm config", null);
    }

    private static void register(
        String modId,
        String shortLabel,
        int accentColor,
        int sortPriority,
        boolean skyBlockMod,
        String nativeConfigCommand,
        String nativeHudCommand
    ) {
        CATALOG.put(modId, new ModMeta(modId, shortLabel, accentColor, sortPriority, skyBlockMod, nativeConfigCommand, nativeHudCommand));
    }

    private ModCatalog() {}

    public static ModMeta get(String modId) {
        return CATALOG.get(modId);
    }

    public static boolean isSkyBlockMod(String modId) {
        ModMeta m = CATALOG.get(modId);
        return m != null && m.skyBlockMod();
    }

    public static int accentColor(String modId) {
        ModMeta m = CATALOG.get(modId);
        if (m != null) {
            return m.accentColor();
        }
        // Deterministic pleasant pastel-ish color from modId hash
        int hash = Math.abs(modId.hashCode());
        int[] palette = {
            0xFF64748B, 0xFF38BDF8, 0xFF34D399, 0xFFA78BFA,
            0xFFFBBF24, 0xFFF472B6, 0xFF2DD4BF, 0xFFFB923C
        };
        return palette[hash % palette.length];
    }

    public static int sortPriority(String modId) {
        ModMeta m = CATALOG.get(modId);
        return m != null ? m.sortPriority() : 1000;
    }

    public static String shortLabel(String modId, String displayName) {
        ModMeta m = CATALOG.get(modId);
        if (m != null) {
            return m.shortLabel();
        }
        String src = displayName != null && !displayName.isBlank() ? displayName : modId;
        return src.length() <= 6 ? src.toUpperCase() : src.substring(0, 6).toUpperCase();
    }

    public static String nativeConfigCommand(String modId) {
        ModMeta m = CATALOG.get(modId);
        return m != null ? m.nativeConfigCommand() : null;
    }

    public static String nativeHudCommand(String modId) {
        ModMeta m = CATALOG.get(modId);
        return m != null ? m.nativeHudCommand() : null;
    }
}
