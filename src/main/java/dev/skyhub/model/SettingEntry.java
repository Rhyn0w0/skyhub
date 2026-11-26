package dev.skyhub.model;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Normalized representation of a single setting across any SkyBlock Enhanced mod.
 */
public final class SettingEntry {
    private final String id;
    private final String modId;
    private final String modName;
    private final int modAccentColor;
    private final String category;
    private final String subcategory;
    private final String name;
    private final String description;
    private final List<String> searchTags;
    private final SettingType type;
    private final SettingAccess access;
    private final double min;
    private final double max;
    private final double step;
    private final List<String> enumLabels;
    private final List<Object> enumValues;

    private SettingEntry(Builder b) {
        this.id = Objects.requireNonNull(b.id, "id");
        this.modId = Objects.requireNonNull(b.modId, "modId");
        this.modName = Objects.requireNonNull(b.modName, "modName");
        this.modAccentColor = b.modAccentColor;
        this.category = b.category != null && !b.category.isBlank() ? b.category : "General";
        this.subcategory = b.subcategory != null ? b.subcategory : "";
        this.name = Objects.requireNonNull(b.name, "name");
        this.description = b.description != null ? b.description : "";
        this.searchTags = b.searchTags != null ? List.copyOf(b.searchTags) : List.of();
        this.type = Objects.requireNonNull(b.type, "type");
        this.access = Objects.requireNonNull(b.access, "access");
        this.min = b.min;
        this.max = b.max;
        this.step = b.step > 0 ? b.step : (b.type == SettingType.INT ? 1.0 : 0.1);
        this.enumLabels = b.enumLabels != null ? List.copyOf(b.enumLabels) : List.of();
        this.enumValues = b.enumValues != null ? List.copyOf(b.enumValues) : List.of();
    }

    public String id() { return id; }
    public String modId() { return modId; }
    public String modName() { return modName; }
    public int modAccentColor() { return modAccentColor; }
    public String category() { return category; }
    public String subcategory() { return subcategory; }
    public String name() { return name; }
    public String description() { return description; }
    public List<String> searchTags() { return searchTags; }
    public SettingType type() { return type; }
    public double min() { return min; }
    public double max() { return max; }
    public double step() { return step; }
    public List<String> enumLabels() { return enumLabels; }
    public List<Object> enumValues() { return enumValues; }

    public String breadcrumb() {
        if (subcategory.isBlank() || subcategory.equalsIgnoreCase(category)) {
            return category;
        }
        return category + " \u203A " + subcategory;
    }

    public Object getValue() {
        try {
            return access.get();
        } catch (Throwable ignored) {
            return null;
        }
    }

    public Object getDefaultValue() {
        try {
            return access.defaultValue();
        } catch (Throwable ignored) {
            return null;
        }
    }

    public boolean getBooleanValue() {
        Object val = getValue();
        return val instanceof Boolean b && b;
    }

    public double getDoubleValue() {
        Object val = getValue();
        if (val instanceof Number n) {
            return n.doubleValue();
        }
        return min;
    }

    public String getStringValue() {
        Object val = getValue();
        return val == null ? "" : String.valueOf(val);
    }

    public int getColorValue() {
        Object val = getValue();
        if (val instanceof Number n) {
            return n.intValue();
        }
        if (val instanceof String s) {
            String hex = s.startsWith("#") ? s.substring(1) : s;
            try {
                int parsed = (int) Long.parseLong(hex, 16);
                return hex.length() <= 6 ? (0xFF000000 | parsed) : parsed;
            } catch (NumberFormatException ignored) {
            }
        }
        return 0xFFFFFFFF;
    }

    public boolean isCurrentlyOn() {
        Object val = getValue();
        return switch (type) {
            case BOOLEAN -> Boolean.TRUE.equals(val);
            case INT, FLOAT, DOUBLE -> val instanceof Number n && n.doubleValue() != 0.0;
            case STRING -> val instanceof String s && !s.isBlank();
            case ENUM -> {
                if (val == null) yield false;
                String s = val.toString().toUpperCase(Locale.ROOT);
                yield !s.equals("OFF") && !s.equals("NONE") && !s.equals("DISABLED");
            }
            case COLOR -> true;
            case ACTION -> false;
        };
    }

    public boolean isModified() {
        Object def = getDefaultValue();
        if (def == null) {
            return false;
        }
        Object cur = getValue();
        if (cur instanceof Number cn && def instanceof Number dn) {
            return Math.abs(cn.doubleValue() - dn.doubleValue()) > 1e-5;
        }
        return !Objects.equals(cur, def);
    }

    public boolean toggle() {
        if (type != SettingType.BOOLEAN) {
            return false;
        }
        return setAndSave(!getBooleanValue());
    }

    public boolean setNumeric(double raw) {
        double clamped = Math.max(min, Math.min(max, raw));
        Object boxed = switch (type) {
            case INT -> (int) Math.round(clamped);
            case FLOAT -> (float) clamped;
            case DOUBLE -> clamped;
            default -> null;
        };
        if (boxed == null) {
            return false;
        }
        return setAndSave(boxed);
    }

    public boolean stepNumeric(int direction) {
        return setNumeric(getDoubleValue() + (step * direction));
    }

    public boolean cycleEnum(int delta) {
        if (type != SettingType.ENUM || enumValues.isEmpty()) {
            return false;
        }
        Object current = getValue();
        int idx = 0;
        for (int i = 0; i < enumValues.size(); i++) {
            if (Objects.equals(enumValues.get(i), current)) {
                idx = i;
                break;
            }
        }
        int next = Math.floorMod(idx + delta, enumValues.size());
        return setAndSave(enumValues.get(next));
    }

    public boolean setString(String text) {
        if (type != SettingType.STRING) {
            return false;
        }
        return setAndSave(text == null ? "" : text);
    }

    public boolean triggerAction() {
        if (type != SettingType.ACTION) {
            return false;
        }
        return setAndSave(Boolean.TRUE);
    }

    public boolean reset() {
        Object def = getDefaultValue();
        if (def == null) {
            return false;
        }
        return setAndSave(def);
    }

    public boolean setAndSave(Object value) {
        try {
            boolean ok = access.set(value);
            if (ok) {
                access.save();
            }
            return ok;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public String formattedValue() {
        Object val = getValue();
        if (val == null) {
            return "—";
        }
        return switch (type) {
            case BOOLEAN -> Boolean.TRUE.equals(val) ? "ON" : "OFF";
            case INT -> String.valueOf(((Number) val).intValue());
            case FLOAT, DOUBLE -> {
                double d = ((Number) val).doubleValue();
                if (Math.abs(d - Math.rint(d)) < 1e-4) {
                    yield String.format(Locale.ROOT, "%.0f", d);
                }
                yield String.format(Locale.ROOT, "%.2f", d);
            }
            case ENUM -> {
                for (int i = 0; i < enumValues.size(); i++) {
                    if (Objects.equals(enumValues.get(i), val) && i < enumLabels.size()) {
                        yield enumLabels.get(i);
                    }
                }
                yield formatIdentifier(val.toString());
            }
            case STRING -> val.toString();
            case COLOR -> String.format(Locale.ROOT, "#%06X", getColorValue() & 0xFFFFFF);
            case ACTION -> "Run";
        };
    }

    public static String formatIdentifier(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        // Strip MC formatting codes (§x)
        String cleaned = raw.replaceAll("§.", "").trim();
        if (cleaned.contains(" ")) {
            return cleaned;
        }
        String spaced = cleaned
            .replace('_', ' ')
            .replace('-', ' ')
            .replaceAll("([a-z0-9])([A-Z])", "$1 $2");
        StringBuilder out = new StringBuilder(spaced.length());
        boolean capNext = true;
        for (int i = 0; i < spaced.length(); i++) {
            char c = spaced.charAt(i);
            if (Character.isWhitespace(c)) {
                capNext = true;
                out.append(' ');
            } else if (capNext) {
                out.append(Character.toUpperCase(c));
                capNext = false;
            } else {
                out.append(Character.toLowerCase(c));
            }
        }
        return out.toString().trim();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String id;
        private String modId;
        private String modName;
        private int modAccentColor = 0xFF38BDF8;
        private String category = "General";
        private String subcategory = "";
        private String name;
        private String description = "";
        private List<String> searchTags = List.of();
        private SettingType type;
        private SettingAccess access;
        private double min = 0.0;
        private double max = 100.0;
        private double step = 1.0;
        private List<String> enumLabels = List.of();
        private List<Object> enumValues = List.of();

        public Builder id(String id) { this.id = id; return this; }
        public Builder modId(String modId) { this.modId = modId; return this; }
        public Builder modName(String modName) { this.modName = modName; return this; }
        public Builder modAccentColor(int color) { this.modAccentColor = color; return this; }
        public Builder category(String category) { this.category = category; return this; }
        public Builder subcategory(String subcategory) { this.subcategory = subcategory; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder searchTags(List<String> tags) { this.searchTags = tags; return this; }
        public Builder type(SettingType type) { this.type = type; return this; }
        public Builder access(SettingAccess access) { this.access = access; return this; }
        public Builder range(double min, double max, double step) {
            this.min = min;
            this.max = max;
            this.step = step;
            return this;
        }
        public Builder enumOptions(List<String> labels, List<Object> values) {
            this.enumLabels = labels;
            this.enumValues = values;
            return this;
        }

        public SettingEntry build() {
            return new SettingEntry(this);
        }
    }
}
