package dev.effectvisibility;

/** Excludes map control flags from the settings picker, without changing actual effects. */
public final class EffectPickerPolicy {
    public static boolean isSelectable(String id) {
        int separator = id.indexOf(':');
        if (separator < 0) return true;
        String namespace = id.substring(0, separator);
        String path = id.substring(separator + 1);
        return !(namespace.equals("xaerominimap") || namespace.equals("xaeroworldmap"))
                || !path.startsWith("no_");
    }

    private EffectPickerPolicy() {}
}
