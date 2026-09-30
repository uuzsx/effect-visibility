package dev.effectvisibility;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VisibilityRulesTest {
    private record Effect(String id, int amplifier, int duration) {}
    private static final Effect SPEED = new Effect("minecraft:speed", 2, 600);
    private static final Effect MOD_SPEED = new Effect("example:speed", 4, 1200);
    private static final Effect INFINITE = new Effect("minecraft:night_vision", 0, -1);

    private static List<Effect> filter(VisibilityRules rules, List<Effect> effects) {
        return new ArrayList<>(rules.filter(effects, Effect::id));
    }

    @Test void defaultsHideNothing() {
        assertEquals(List.of(SPEED, INFINITE), filter(VisibilityRules.defaults(), List.of(SPEED, INFINITE)));
    }

    @Test void matchesFullIdAndKeepsModNamespaceDistinct() {
        var rules = new VisibilityRules(false, Set.of("minecraft:speed"));
        assertEquals(List.of(MOD_SPEED), filter(rules, List.of(SPEED, MOD_SPEED)));
    }

    @Test void noEffectObjectsOrSourceCollectionAreModified() {
        var source = new ArrayList<>(List.of(SPEED, MOD_SPEED, INFINITE));
        var rules = new VisibilityRules(false, Set.of("minecraft:speed"));
        var filtered = filter(rules, source);
        assertEquals(List.of(SPEED, MOD_SPEED, INFINITE), source);
        assertSame(MOD_SPEED, filtered.getFirst());
        assertSame(INFINITE, filtered.getLast());
        filtered.clear();
        assertEquals(3, source.size());
        assertEquals(600, SPEED.duration());
        assertEquals(2, SPEED.amplifier());
    }

    @Test void hideAllCoversFiniteInfiniteAndNewEffectsWithoutDiscardingSelection() {
        var rules = new VisibilityRules(true, Set.of(SPEED.id()));
        var source = List.of(SPEED, MOD_SPEED, INFINITE, new Effect("newmod:new_effect", 0, 300));
        assertTrue(filter(rules, source).isEmpty());
        assertTrue(rules.hides("futuremod:unknown"));
        assertEquals(4, source.size());
        var restored = new VisibilityRules(false, rules.hiddenEffects());
        assertEquals(source.subList(1, 4), filter(restored, source));
    }

    @Test void unknownIdsDoNotHideOtherEffectsAndAreRetained() {
        var rules = new VisibilityRules(false, Set.of("uninstalled:custom"));
        assertEquals(List.of(SPEED), filter(rules, List.of(SPEED)));
        assertTrue(rules.hiddenEffects().contains("uninstalled:custom"));
    }

    @Test void draftMutationsCannotChangePublishedRules() {
        var draft = new java.util.HashSet<>(Set.of(SPEED.id()));
        var rules = new VisibilityRules(false, draft);
        draft.clear();
        assertTrue(rules.hides(SPEED.id()));
        assertThrows(UnsupportedOperationException.class, () -> rules.hiddenEffects().clear());
    }

    @Test void handlesEmptyAndAllSelectedWithoutGaps() {
        var rules = new VisibilityRules(false, Set.of(SPEED.id(), MOD_SPEED.id()));
        assertTrue(filter(rules, List.of()).isEmpty());
        assertTrue(filter(rules, List.of(SPEED, MOD_SPEED)).isEmpty());
        assertTrue(filter(new VisibilityRules(true, Set.of()), List.of()).isEmpty());
    }

    @Test void resettingRestoresEveryEffect() {
        var selected = new VisibilityRules(true, Set.of(SPEED.id(), MOD_SPEED.id()));
        assertTrue(filter(selected, List.of(SPEED, MOD_SPEED, INFINITE)).isEmpty());
        var reset = VisibilityRules.defaults();
        assertFalse(reset.hideAll());
        assertTrue(reset.hiddenEffects().isEmpty());
        assertEquals(List.of(SPEED, MOD_SPEED, INFINITE), filter(reset, List.of(SPEED, MOD_SPEED, INFINITE)));
    }
}
