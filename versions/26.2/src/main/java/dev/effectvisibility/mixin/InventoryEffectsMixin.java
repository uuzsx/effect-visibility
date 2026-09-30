package dev.effectvisibility.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.effectvisibility.EffectVisibility;
import java.util.Collection;
import net.minecraft.client.gui.screens.inventory.EffectsInInventory;
import net.minecraft.world.effect.MobEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = EffectsInInventory.class, remap = false)
abstract class InventoryEffectsMixin {
    @ModifyExpressionValue(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;getActiveEffects()Ljava/util/Collection;"))
    private Collection<MobEffectInstance> effectVisibility$filter(Collection<MobEffectInstance> original) {
        return EffectVisibility.filter(original);
    }
}
