package dev.skyhub.model;

/**
 * Health state of the adapter providing settings/HUD data for a mod.
 */
public enum AdapterHealth {
    /** Live settings and/or HUD widgets indexed directly from the mod's config/registry. */
    HEALTHY,
    /** Partially indexed or recovered from a non-fatal error; native fallback still available. */
    DEGRADED,
    /** Deep adapter unavailable or disabled; one-click fallback to native screen/command. */
    FALLBACK_ONLY
}
