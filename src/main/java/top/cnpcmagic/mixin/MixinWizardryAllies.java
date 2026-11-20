package top.cnpcmagic.mixin;

import com.binaris.wizardry.core.AllyDesignation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.binaris.wizardry.core.AllyDesignation", remap = false)
public abstract class MixinWizardryAllies {
    @Inject(method = "isAllied", at = @At("RETURN"), cancellable = true, remap = false)
    private static void cnpcmagic$npcAllies(LivingEntity caster, Entity target, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && caster instanceof EntityNPCInterface npc && (npc.getOwner() == target
            || target instanceof EntityNPCInterface other && npc.faction.id == other.faction.id
            || target instanceof Player player && npc.faction.isFriendlyToPlayer(player))) cir.setReturnValue(true);
    }

    @Inject(method = "isValidTarget", at = @At("RETURN"), cancellable = true, remap = false)
    private static void cnpcmagic$noFriendlyFire(Entity caster, Entity target, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && caster instanceof EntityNPCInterface npc && AllyDesignation.isAllied(npc, target))
            cir.setReturnValue(false);
    }
}
