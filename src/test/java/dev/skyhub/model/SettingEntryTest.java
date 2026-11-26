package dev.skyhub.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class SettingEntryTest {

    @Test
    void writesDirectlyToBackingConfigAndCallsSaveHook() {
        AtomicReference<Double> backing = new AtomicReference<>(1.0);
        AtomicInteger saves = new AtomicInteger(0);

        SettingEntry entry = SettingEntry.builder()
            .id("test:scale")
            .modId("skyhanni")
            .modName("SkyHanni")
            .name("Overlay Scale")
            .type(SettingType.DOUBLE)
            .range(0.5, 3.0, 0.25)
            .access(new SettingAccess() {
                @Override public Object get() { return backing.get(); }
                @Override public boolean set(Object v) { backing.set(((Number) v).doubleValue()); return true; }
                @Override public Object defaultValue() { return 1.0; }
                @Override public void save() { saves.incrementAndGet(); }
            })
            .build();

        assertFalse(entry.isModified());
        assertTrue(entry.stepNumeric(1));
        assertEquals(1.25, backing.get(), 1e-6);
        assertEquals(1, saves.get());
        assertTrue(entry.isModified());

        // Clamp upper bound
        assertTrue(entry.setNumeric(99.0));
        assertEquals(3.0, backing.get(), 1e-6);
        assertEquals(2, saves.get());

        // Reset restores default
        assertTrue(entry.reset());
        assertEquals(1.0, backing.get(), 1e-6);
        assertFalse(entry.isModified());
    }

    @Test
    void isolatesThrowingBackingAccessWithoutCrashing() {
        SettingEntry broken = SettingEntry.builder()
            .id("broken:bool")
            .modId("broken")
            .modName("BrokenMod")
            .name("Broken Toggle")
            .type(SettingType.BOOLEAN)
            .access(new SettingAccess() {
                @Override public Object get() { throw new IllegalStateException("mod changed internals"); }
                @Override public boolean set(Object v) { throw new LinkageError("method removed"); }
            })
            .build();

        assertDoesNotThrow(broken::getValue);
        assertFalse(broken.getBooleanValue());
        assertFalse(broken.toggle());
        assertEquals("—", broken.formattedValue());
    }

    @Test
    void cyclesEnumsForwardAndBackward() {
        AtomicReference<String> val = new AtomicReference<>("A");
        SettingEntry entry = SettingEntry.builder()
            .id("test:enum")
            .modId("odin")
            .modName("Odin")
            .name("Mode")
            .type(SettingType.ENUM)
            .enumOptions(List.of("Alpha", "Beta", "Gamma"), List.of("A", "B", "C"))
            .access(new SettingAccess() {
                @Override public Object get() { return val.get(); }
                @Override public boolean set(Object v) { val.set(String.valueOf(v)); return true; }
            })
            .build();

        assertEquals("Alpha", entry.formattedValue());
        entry.cycleEnum(1);
        assertEquals("Beta", entry.formattedValue());
        entry.cycleEnum(-2);
        assertEquals("Gamma", entry.formattedValue());
    }
}
