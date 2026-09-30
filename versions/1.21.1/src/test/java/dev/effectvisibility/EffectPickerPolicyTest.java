package dev.effectvisibility;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EffectPickerPolicyTest {
    @Test void excludesXaeroControlFlagsIncludingAllCategoryVariants() {
        for (String id : List.of("xaerominimap:no_minimap", "xaerominimap:no_entity_radar",
                "xaerominimap:no_waypoints", "xaerominimap:no_cave_maps",
                "xaeroworldmap:no_world_map", "xaeroworldmap:no_cave_maps")) {
            assertFalse(EffectPickerPolicy.isSelectable(id), id);
            assertFalse(EffectPickerPolicy.isSelectable(id + "_harmful"), id);
            assertFalse(EffectPickerPolicy.isSelectable(id + "_beneficial"), id);
        }
    }

    @Test void preservesVanillaAndUnrelatedModEffectsRegardlessOfDisplayName() {
        for (String id : List.of("minecraft:speed", "minecraft:night_vision", "othermod:no_world_map",
                "othermod:no_cave_maps", "xaerominimap_extra:no_minimap", "xaeroworldmap:speed")) {
            assertTrue(EffectPickerPolicy.isSelectable(id), id);
        }
    }

    @Test void menuExclusionDoesNotChangeDisplayRulesOrStoredSelections() {
        String control = "xaeroworldmap:no_world_map";
        assertFalse(EffectPickerPolicy.isSelectable(control));
        assertEquals(List.of(control), VisibilityRules.defaults().filter(List.of(control), id -> id));
        assertTrue(new VisibilityRules(true, Set.of()).filter(List.of(control), id -> id).isEmpty());
        var previouslySelected = new VisibilityRules(false, Set.of(control, "minecraft:speed"));
        assertTrue(previouslySelected.hides(control));
        assertTrue(previouslySelected.hiddenEffects().contains(control));
    }
}
