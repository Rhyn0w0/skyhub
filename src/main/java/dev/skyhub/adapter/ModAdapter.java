package dev.skyhub.adapter;

/**
 * Contract for Tier-1 framework adapters, Tier-2 mod-specific adapters,
 * and Tier-3 fallback adapters.
 */
public interface ModAdapter {
    /** Human-readable adapter name (e.g. "MoulConfig", "ResourcefulConfig", "Odin"). */
    String name();

    /**
     * Execution priority (lower runs first).
     * Tier-2 mod-specific adapters: 10..49
     * Tier-1 framework adapters:    50..89
     * Tier-3 fallback adapters:     90..100
     */
    int priority();

    /**
     * Collect normalized settings, HUD widgets, and native screen/HUD openers.
     * Implementations MUST check FabricLoader.isModLoaded(...) / class presence
     * before touching optional mod internals.
     */
    void collect(AdapterContext ctx);
}
