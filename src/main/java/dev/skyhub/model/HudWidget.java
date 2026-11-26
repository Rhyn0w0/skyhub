package dev.skyhub.model;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * Normalized HUD element managed by the Phase 2 Unified HUD Canvas.
 * Reads and writes live coordinates/scale directly into the owning mod's HUD state.
 */
public final class HudWidget {
    private final String id;
    private final String modId;
    private final String modName;
    private final int modAccentColor;
    private final String name;
    private final String category;
    private final BooleanSupplier enabledGetter;
    private final Consumer<Boolean> enabledSetter;
    private final IntSupplier xGetter;
    private final IntSupplier yGetter;
    private final IntConsumer xSetter;
    private final IntConsumer ySetter;
    private final IntSupplier widthGetter;
    private final IntSupplier heightGetter;
    private final DoubleSupplier scaleGetter;
    private final DoubleConsumer scaleSetter;
    private final Runnable saveHook;
    private final Runnable resetHook;
    private boolean dirty;

    private HudWidget(Builder b) {
        this.id = Objects.requireNonNull(b.id, "id");
        this.modId = Objects.requireNonNull(b.modId, "modId");
        this.modName = Objects.requireNonNull(b.modName, "modName");
        this.modAccentColor = b.modAccentColor;
        this.name = Objects.requireNonNull(b.name, "name");
        this.category = b.category != null ? b.category : "HUD";
        this.enabledGetter = b.enabledGetter != null ? b.enabledGetter : () -> true;
        this.enabledSetter = b.enabledSetter;
        this.xGetter = Objects.requireNonNull(b.xGetter, "xGetter");
        this.yGetter = Objects.requireNonNull(b.yGetter, "yGetter");
        this.xSetter = Objects.requireNonNull(b.xSetter, "xSetter");
        this.ySetter = Objects.requireNonNull(b.ySetter, "ySetter");
        this.widthGetter = b.widthGetter != null ? b.widthGetter : () -> 110;
        this.heightGetter = b.heightGetter != null ? b.heightGetter : () -> 22;
        this.scaleGetter = b.scaleGetter != null ? b.scaleGetter : () -> 1.0;
        this.scaleSetter = b.scaleSetter;
        this.saveHook = b.saveHook != null ? b.saveHook : () -> {};
        this.resetHook = b.resetHook;
    }

    public String id() { return id; }
    public String modId() { return modId; }
    public String modName() { return modName; }
    public int modAccentColor() { return modAccentColor; }
    public String name() { return name; }
    public String category() { return category; }
    public boolean canToggle() { return enabledSetter != null; }
    public boolean canScale() { return scaleSetter != null; }
    public boolean canReset() { return resetHook != null; }
    public boolean isDirty() { return dirty; }

    public boolean isEnabled() {
        try {
            return enabledGetter.getAsBoolean();
        } catch (Throwable ignored) {
            return true;
        }
    }

    public void setEnabled(boolean enabled) {
        if (enabledSetter == null) return;
        try {
            enabledSetter.accept(enabled);
            dirty = true;
        } catch (Throwable ignored) {
        }
    }

    public int getX() {
        try {
            return xGetter.getAsInt();
        } catch (Throwable ignored) {
            return 10;
        }
    }

    public int getY() {
        try {
            return yGetter.getAsInt();
        } catch (Throwable ignored) {
            return 10;
        }
    }

    public void setPosition(int x, int y) {
        try {
            xSetter.accept(x);
            ySetter.accept(y);
            dirty = true;
        } catch (Throwable ignored) {
        }
    }

    public double getScale() {
        try {
            double s = scaleGetter.getAsDouble();
            return s > 0.05 ? s : 1.0;
        } catch (Throwable ignored) {
            return 1.0;
        }
    }

    public void setScale(double scale) {
        if (scaleSetter == null) return;
        double clamped = Math.max(0.25, Math.min(4.0, Math.round(scale * 20.0) / 20.0));
        try {
            scaleSetter.accept(clamped);
            dirty = true;
        } catch (Throwable ignored) {
        }
    }

    public int getWidth() {
        try {
            return Math.max(40, widthGetter.getAsInt());
        } catch (Throwable ignored) {
            return 100;
        }
    }

    public int getHeight() {
        try {
            return Math.max(16, heightGetter.getAsInt());
        } catch (Throwable ignored) {
            return 22;
        }
    }

    public int getScaledWidth() {
        return Math.max(44, (int) Math.round(getWidth() * getScale()));
    }

    public int getScaledHeight() {
        return Math.max(18, (int) Math.round(getHeight() * getScale()));
    }

    public void reset() {
        if (resetHook == null) return;
        try {
            resetHook.run();
            dirty = true;
        } catch (Throwable ignored) {
        }
    }

    public void save() {
        try {
            saveHook.run();
            dirty = false;
        } catch (Throwable ignored) {
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String id;
        private String modId;
        private String modName;
        private int modAccentColor = 0xFF38BDF8;
        private String name;
        private String category = "HUD";
        private BooleanSupplier enabledGetter;
        private Consumer<Boolean> enabledSetter;
        private IntSupplier xGetter;
        private IntSupplier yGetter;
        private IntConsumer xSetter;
        private IntConsumer ySetter;
        private IntSupplier widthGetter;
        private IntSupplier heightGetter;
        private DoubleSupplier scaleGetter;
        private DoubleConsumer scaleSetter;
        private Runnable saveHook;
        private Runnable resetHook;

        public Builder id(String id) { this.id = id; return this; }
        public Builder modId(String modId) { this.modId = modId; return this; }
        public Builder modName(String modName) { this.modName = modName; return this; }
        public Builder modAccentColor(int color) { this.modAccentColor = color; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder category(String category) { this.category = category; return this; }
        public Builder enabled(BooleanSupplier getter, Consumer<Boolean> setter) {
            this.enabledGetter = getter;
            this.enabledSetter = setter;
            return this;
        }
        public Builder position(IntSupplier xGet, IntSupplier yGet, IntConsumer xSet, IntConsumer ySet) {
            this.xGetter = xGet;
            this.yGetter = yGet;
            this.xSetter = xSet;
            this.ySetter = ySet;
            return this;
        }
        public Builder size(IntSupplier wGet, IntSupplier hGet) {
            this.widthGetter = wGet;
            this.heightGetter = hGet;
            return this;
        }
        public Builder scale(DoubleSupplier getter, DoubleConsumer setter) {
            this.scaleGetter = getter;
            this.scaleSetter = setter;
            return this;
        }
        public Builder saveHook(Runnable hook) { this.saveHook = hook; return this; }
        public Builder resetHook(Runnable hook) { this.resetHook = hook; return this; }

        public HudWidget build() {
            return new HudWidget(this);
        }
    }
}
