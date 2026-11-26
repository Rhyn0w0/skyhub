package dev.skyhub.model;

import net.minecraft.client.gui.screens.Screen;

import java.util.function.Function;

/**
 * Normalized metadata and runtime state for an installed mod visible in SkyHub.
 */
public final class ModEntry {
    private final String modId;
    private final String displayName;
    private final String version;
    private final int accentColor;
    private final int sortPriority;
    private final boolean skyBlockMod;

    private String adapterName = "Fallback";
    private AdapterHealth health = AdapterHealth.FALLBACK_ONLY;
    private String statusDetail = "Native menu fallback";
    private int settingCount;
    private int hudWidgetCount;
    private Function<Screen, Screen> nativeMenuOpener;
    private Runnable nativeHudOpener;
    private String nativeConfigCommand;
    private String nativeHudCommand;

    public ModEntry(String modId, String displayName, String version) {
        this.modId = modId;
        this.displayName = displayName != null && !displayName.isBlank() ? displayName : modId;
        this.version = version != null ? version : "";
        this.accentColor = ModCatalog.accentColor(modId);
        this.sortPriority = ModCatalog.sortPriority(modId);
        this.skyBlockMod = ModCatalog.isSkyBlockMod(modId);
        this.nativeConfigCommand = ModCatalog.nativeConfigCommand(modId);
        this.nativeHudCommand = ModCatalog.nativeHudCommand(modId);
    }

    public String modId() { return modId; }
    public String displayName() { return displayName; }
    public String version() { return version; }
    public int accentColor() { return accentColor; }
    public int sortPriority() { return sortPriority; }
    public boolean isSkyBlockMod() { return skyBlockMod; }
    public String shortLabel() { return ModCatalog.shortLabel(modId, displayName); }

    public String adapterName() { return adapterName; }
    public AdapterHealth health() { return health; }
    public String statusDetail() { return statusDetail; }
    public int settingCount() { return settingCount; }
    public int hudWidgetCount() { return hudWidgetCount; }
    public Function<Screen, Screen> nativeMenuOpener() { return nativeMenuOpener; }
    public Runnable nativeHudOpener() { return nativeHudOpener; }
    public String nativeConfigCommand() { return nativeConfigCommand; }
    public String nativeHudCommand() { return nativeHudCommand; }

    public boolean hasNativeMenu() {
        return nativeMenuOpener != null || (nativeConfigCommand != null && !nativeConfigCommand.isBlank());
    }

    public boolean hasNativeHud() {
        return nativeHudOpener != null || (nativeHudCommand != null && !nativeHudCommand.isBlank());
    }

    public void setAdapterStatus(String adapterName, AdapterHealth health, String detail) {
        this.adapterName = adapterName;
        this.health = health;
        this.statusDetail = detail != null ? detail : "";
    }

    public void setSettingCount(int count) { this.settingCount = count; }
    public void setHudWidgetCount(int count) { this.hudWidgetCount = count; }

    public void setNativeMenuOpenerIfAbsent(Function<Screen, Screen> opener) {
        if (this.nativeMenuOpener == null && opener != null) {
            this.nativeMenuOpener = opener;
        }
    }

    public void setNativeMenuOpener(Function<Screen, Screen> opener) {
        if (opener != null) {
            this.nativeMenuOpener = opener;
        }
    }

    public void setNativeHudOpenerIfAbsent(Runnable opener) {
        if (this.nativeHudOpener == null && opener != null) {
            this.nativeHudOpener = opener;
        }
    }

    public void setNativeHudOpener(Runnable opener) {
        if (opener != null) {
            this.nativeHudOpener = opener;
        }
    }

    public void setNativeConfigCommandIfAbsent(String command) {
        if ((this.nativeConfigCommand == null || this.nativeConfigCommand.isBlank()) && command != null) {
            this.nativeConfigCommand = command;
        }
    }

    public void setNativeHudCommandIfAbsent(String command) {
        if ((this.nativeHudCommand == null || this.nativeHudCommand.isBlank()) && command != null) {
            this.nativeHudCommand = command;
        }
    }
}
