package dev.effectvisibility;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.Collection;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = EffectVisibility.MOD_ID, dist = Dist.CLIENT)
public final class EffectVisibility {
    public static final String MOD_ID = "effect_visibility";
    private static final String CATEGORY = "key.category.effect_visibility.main";
    private static KeyMapping settingsKey;

    public EffectVisibility(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> new EffectSettingsScreen(parent));
        modBus.addListener((ModConfigEvent.Loading event) -> refreshConfig(event));
        modBus.addListener((ModConfigEvent.Reloading event) -> refreshConfig(event));
        modBus.addListener(EffectVisibility::registerKeys);
        NeoForge.EVENT_BUS.addListener(EffectVisibility::clientTick);
    }

    private static void refreshConfig(ModConfigEvent event) {
        if (event.getConfig().getSpec() == ClientConfig.SPEC) ClientConfig.refresh();
    }

    private static void registerKeys(RegisterKeyMappingsEvent event) {
        settingsKey = new KeyMapping("key.effect_visibility.settings", KeyConflictContext.IN_GAME, InputConstants.UNKNOWN, CATEGORY);
        event.register(settingsKey);
    }

    private static void clientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (settingsKey == null) return;
        while (settingsKey.consumeClick()) {
            if (mc.player != null && mc.screen == null) mc.setScreen(new EffectSettingsScreen(null));
        }
    }

    public static Collection<MobEffectInstance> filter(Collection<MobEffectInstance> effects) {
        return ClientConfig.rules().filter(effects,
                effect -> BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value()).toString());
    }
}
