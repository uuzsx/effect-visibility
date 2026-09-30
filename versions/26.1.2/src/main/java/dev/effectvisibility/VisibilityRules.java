package dev.effectvisibility;

import java.util.Collection;
import java.util.Set;
import java.util.function.Function;

/** Immutable display policy. Never changes the source collection or its effects. */
public record VisibilityRules(boolean hideAll, Set<String> hiddenEffects) {
    public VisibilityRules { hiddenEffects = Set.copyOf(hiddenEffects); }

    public static VisibilityRules defaults() {
        return new VisibilityRules(false, Set.of());
    }

    public boolean hides(String id) {
        return hideAll || hiddenEffects.contains(id);
    }

    public <T> Collection<T> filter(Collection<T> effects, Function<T, String> id) {
        if (hideAll) return java.util.List.of();
        if (hiddenEffects.isEmpty()) return effects;
        return effects.stream().filter(effect -> !hides(id.apply(effect)))
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
    }
}
