package dev.effectvisibility.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.effectvisibility.EffectVisibility;
import java.util.Collection;
import net.minecraft.client.gui.Hud;
import net.minecraft.world.effect.MobEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = Hud.class, remap = false)
abstract class HudEffectsMixin {
    @ModifyExpressionValue(method = "extractEffects", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;getActiveEffects()Ljava/util/Collection;"))
    private Collection<MobEffectInstance> effectVisibility$filter(Collection<MobEffectInstance> original) {
        return EffectVisibility.filter(original);
    }
}
