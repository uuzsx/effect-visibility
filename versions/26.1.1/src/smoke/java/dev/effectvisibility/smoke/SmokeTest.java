package dev.effectvisibility.smoke;

import dev.effectvisibility.ClientConfig;
import dev.effectvisibility.EffectSettingsScreen;
import dev.effectvisibility.EffectPickerPolicy;
import dev.effectvisibility.EffectVisibility;
import dev.effectvisibility.VisibilityRules;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.EffectsInInventory;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(value = "effect_visibility_smoke", dist = Dist.CLIENT)
public final class SmokeTest {
    private static final Logger LOG = LoggerFactory.getLogger("EffectVisibilitySmoke");
    private static final net.neoforged.neoforge.registries.DeferredRegister<net.minecraft.world.effect.MobEffect> EFFECTS =
            net.neoforged.neoforge.registries.DeferredRegister.create(net.minecraft.core.registries.Registries.MOB_EFFECT, "effect_visibility_smoke");
    private static final net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.world.effect.MobEffect, TestEffect> SAMPLE =
            EFFECTS.register("sample_effect", TestEffect::new);
    private static final net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.world.effect.MobEffect, TestEffect> MAP_NAMED =
            EFFECTS.register("no_world_map", TestEffect::new);
    private static final class TestEffect extends net.minecraft.world.effect.MobEffect {
        TestEffect() { super(net.minecraft.world.effect.MobEffectCategory.BENEFICIAL, 0x00AABB); }
    }
    private VisibilityRules original;
    private int ticks;
    private boolean started, finished;

    public SmokeTest(net.neoforged.bus.api.IEventBus modBus) {
        EFFECTS.register(modBus);
        NeoForge.EVENT_BUS.addListener(this::tick);
    }

    private void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (finished || mc.getOverlay() != null) return;
        try {
            if (!started) {
                if (mc.screen == null) return;
                started = true;
                original = ClientConfig.rules();
                Class.forName(EffectsInInventory.class.getName()); // force inventory mixin transformation
                check(!ComponentKey.category().equals("key.category.effect_visibility.main"), "key category translation resolves");
                ClientConfig.save(VisibilityRules.defaults());
                Screen parent = mc.screen;
                mc.setScreen(new EffectSettingsScreen(parent));
                check(mc.screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                        .filter(button -> button.getY() == 42).count() == 2, "only Hide all and Reset at top");
                var xaeroControls = BuiltInRegistries.MOB_EFFECT.keySet().stream()
                        .filter(id -> !EffectPickerPolicy.isSelectable(id.toString())).toList();
                if (net.neoforged.fml.ModList.get().isLoaded("xaerominimap")
                        || net.neoforged.fml.ModList.get().isLoaded("xaeroworldmap")) {
                    check(!xaeroControls.isEmpty(), "actual Xaero control effects found");
                }
                for (var control : xaeroControls) {
                    var registered = BuiltInRegistries.MOB_EFFECT.getValue(control);
                    search(mc.screen).setValue(control.toString());
                    check(effectRowCount(mc.screen) == 0, "Xaero control excluded from ID search: " + control);
                    search(mc.screen).setValue(registered.getDisplayName().getString());
                    check(mc.screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                            .noneMatch(button -> button.getMessage().getString().contains(registered.getDisplayName().getString())),
                            "Xaero control excluded from name search: " + control);
                    check(BuiltInRegistries.MOB_EFFECT.getValue(control) == registered, "Xaero effect remains registered");
                }
                LOG.info("XAERO_PICKER_CHECK: excluded {} registered map control effects from ID and localized name searches", xaeroControls.size());
                search(mc.screen).setValue("effect_visibility_smoke:sample_effect");
                check(effectRowCount(mc.screen) == 1, "registered third-party effect available in picker");
                pressEffect(mc.screen);
                pressSave(mc.screen);
                var sampleInstance = new MobEffectInstance(SAMPLE, 900, 2);
                var modEffects = new ArrayList<>(List.of(sampleInstance));
                check(EffectVisibility.filter(modEffects).isEmpty(), "registered mod effect can be hidden");
                check(modEffects.size() == 1 && sampleInstance.getDuration() == 900 && sampleInstance.getAmplifier() == 2,
                        "registered mod effect state unchanged");
                mc.setScreen(new EffectSettingsScreen(parent));
                search(mc.screen).setValue("effect_visibility_smoke:no_world_map");
                check(effectRowCount(mc.screen) == 1, "unrelated mod effect with map-like name remains selectable");
                mc.screen.onClose();
                ClientConfig.save(VisibilityRules.defaults());
                mc.setScreen(new EffectSettingsScreen(parent));
                EditBox search = search(mc.screen);
                search.setValue("night_vision");
                pressEffect(mc.screen);
                check(ClientConfig.rules().hiddenEffects().isEmpty(), "draft must not apply before save");
                mc.screen.resize(320, 240);
                check(search(mc.screen).getValue().equals("night_vision"), "search survives resize");
                check(effectButton(mc.screen).getMessage().getString().equals(net.minecraft.network.chat.Component
                        .translatable("effect_visibility.hidden", MobEffects.NIGHT_VISION.value().getDisplayName()).getString()),
                        "selection survives resize");
                pressSave(mc.screen);
                check(mc.screen == parent, "save returns to parent");
                check(ClientConfig.rules().hiddenEffects().equals(Set.of("minecraft:night_vision")), "save applies exact ID");
                ClientConfig.refresh();
                check(ClientConfig.rules().hiddenEffects().contains("minecraft:night_vision"), "saved config survives refresh");
                var night = new MobEffectInstance(MobEffects.NIGHT_VISION, 600, 1);
                var speed = new MobEffectInstance(MobEffects.SPEED, 1200, 2);
                var source = new ArrayList<>(List.of(night, speed));
                var filtered = EffectVisibility.filter(source);
                check(filtered.size() == 1 && filtered.iterator().next() == speed, "real registry effects filtered");
                check(source.size() == 2 && night.getDuration() == 600 && night.getAmplifier() == 1, "actual effect state unchanged");
                mc.setScreen(new EffectSettingsScreen(parent));
                search(mc.screen).setValue("night_vision");
                pressEffect(mc.screen);
                mc.screen.onClose();
                check(ClientConfig.rules().hiddenEffects().contains("minecraft:night_vision"), "cancel discards draft");
                mc.setScreen(new EffectSettingsScreen(parent));
                pressTop(mc.screen, false);
                check(!ClientConfig.rules().hideAll(), "Hide all stays a draft until save");
                check(!effectButton(mc.screen).active, "individual toggles disabled while hiding all");
                mc.screen.resize(320, 240);
                check(!effectButton(mc.screen).active, "Hide all survives resize");
                pressSave(mc.screen);
                check(ClientConfig.rules().hideAll() && EffectVisibility.filter(source).isEmpty(), "Hide all applies to every real effect");
                mc.setScreen(new EffectSettingsScreen(parent));
                pressTop(mc.screen, false);
                pressSave(mc.screen);
                check(!ClientConfig.rules().hideAll() && EffectVisibility.filter(source).size() == 1,
                        "turning off Hide all preserves individual selection");
                mc.setScreen(new EffectSettingsScreen(parent));
                pressTop(mc.screen, false);
                pressSave(mc.screen);
                mc.setScreen(new EffectSettingsScreen(parent));
                pressTop(mc.screen, true);
                check(ClientConfig.rules().hideAll(), "reset stays a draft until save");
                mc.screen.onClose();
                check(ClientConfig.rules().hideAll(), "cancel reset keeps saved rules");
                mc.setScreen(new EffectSettingsScreen(parent));
                pressTop(mc.screen, true);
                pressSave(mc.screen);
                check(ClientConfig.rules().equals(VisibilityRules.defaults()), "reset clears selection and Hide all");
                check(EffectVisibility.filter(source).size() == 2, "reset restores every real effect");
                mc.setScreen(new EffectSettingsScreen(parent));
                search(mc.screen).setValue(MobEffects.NIGHT_VISION.value().getDisplayName().getString());
                pressEffect(mc.screen);
                pressSave(mc.screen);
                check(ClientConfig.rules().hiddenEffects().contains("minecraft:night_vision"), "search by localized name");
                mc.setScreen(new EffectSettingsScreen(parent));
                search(mc.screen).setValue("night_vision");
                check(mc.screen.width >= 320 && mc.screen.height >= 240, "minimum layout dimensions");
                LOG.info("SMOKE_ASSERTIONS_PASSED: both GUI mixins; simplified controls; Hide all; reset; save/cancel/resize; localized and ID search; real effects unchanged");
            }
            if (++ticks == 35) {
                search(mc.screen).setValue("xaero");
                check(effectRowCount(mc.screen) == 0, "Xaero control effects absent from namespace search");
            }
            if (ticks == 40) {
                Screenshot.grab(mc.gameDirectory, "effect-visibility-xaero-filter.png", mc.getMainRenderTarget(), 1,
                        message -> LOG.info("XAERO_PICKER_SCREENSHOT: {}", message.getString()));
            }
            if (ticks == 45) search(mc.screen).setValue("night_vision");
            if (ticks == 50) {
                Screenshot.grab(mc.gameDirectory, "effect-visibility-settings.png", mc.getMainRenderTarget(), 1, message -> {
                    LOG.info("SMOKE_SCREENSHOT: {}", message.getString());
                    mc.execute(() -> { ClientConfig.save(original); finished = true; mc.stop(); });
                });
            }
            if (ticks > 200) throw new AssertionError("Screenshot timed out");
        } catch (Throwable failure) {
            LOG.error("SMOKE_FAILED", failure);
            finished = true;
            if (original != null) ClientConfig.save(original);
            mc.stop();
        }
    }

    private static EditBox search(Screen screen) {
        return screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow();
    }
    private static Button effectButton(Screen screen) {
        return screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.getY() >= 98 && button.getY() < screen.height - 54).findFirst().orElseThrow();
    }
    private static void pressEffect(Screen screen) { effectButton(screen).onPress(new KeyEvent(257, 0, 0)); }
    private static long effectRowCount(Screen screen) {
        return screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.getY() >= 98 && button.getY() < screen.height - 54).count();
    }
    private static void pressTop(Screen screen, boolean reset) {
        var buttons = screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.getY() == 42).sorted(java.util.Comparator.comparingInt(Button::getX)).toList();
        buttons.get(reset ? 1 : 0).onPress(new KeyEvent(257, 0, 0));
    }
    private static void pressSave(Screen screen) {
        screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.getY() == screen.height - 28).findFirst().orElseThrow().onPress(new KeyEvent(257, 0, 0));
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static final class ComponentKey {
        static String category() { return net.minecraft.network.chat.Component.translatable("key.category.effect_visibility.main").getString(); }
    }
}
