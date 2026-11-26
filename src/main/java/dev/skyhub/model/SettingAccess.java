package dev.skyhub.model;

/**
 * Direct live access to a native mod's config field/property.
 * SkyHub never stores duplicate config files — every write goes directly
 * to the owning mod's live config object and triggers its native save hook.
 */
public interface SettingAccess {
    Object get();

    boolean set(Object value);

    default Object defaultValue() {
        return null;
    }

    default void save() {
    }
}
