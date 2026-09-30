package dev.effectvisibility;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class ClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec.BooleanValue HIDE_ALL = BUILDER
            .comment("Hide all status effects in both the HUD and inventory. Actual effects stay active.")
            .define("hideAll", false);
    public static final ModConfigSpec.ConfigValue<List<? extends String>> HIDDEN = BUILDER
            .comment("Effect IDs to hide, e.g. [\"minecraft:night_vision\", \"minecraft:speed\"].", "Unknown mod IDs are retained until that mod is installed.")
            .defineListAllowEmpty("hiddenEffects", List.of(), () -> "minecraft:night_vision",
                    value -> value instanceof String id && Identifier.tryParse(id) != null);
    public static final ModConfigSpec SPEC = BUILDER.build();
    private static volatile VisibilityRules rules = VisibilityRules.defaults();

    public static VisibilityRules rules() { return rules; }

    public static void refresh() {
        Set<String> ids = HIDDEN.get().stream().map(Identifier::parse).map(Identifier::toString).collect(Collectors.toSet());
        rules = new VisibilityRules(HIDE_ALL.get(), ids);
    }

    public static void save(VisibilityRules value) {
        HIDE_ALL.set(value.hideAll());
        HIDDEN.set(value.hiddenEffects().stream().sorted().toList());
        SPEC.save();
        refresh();
    }

    private ClientConfig() {}
}
