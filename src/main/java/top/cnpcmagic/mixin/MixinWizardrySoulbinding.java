package top.cnpcmagic.mixin;

import com.binaris.wizardry.api.content.event.EBLivingHurtEvent;
import com.binaris.wizardry.api.content.spell.internal.CastContext;
import com.binaris.wizardry.api.content.util.MagicDamageSource;
import com.binaris.wizardry.setup.registries.EBDamageSources;
import com.binaris.wizardry.setup.registries.EBMobEffects;
import com.binaris.wizardry.setup.registries.EBSounds;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.cnpcmagic.data.MagicRuntimeData;

/** NPC runtime curse links accompany the native persistent effects, never editor rules. */
@Pseudo
@Mixin(targets = "com.binaris.wizardry.content.spell.necromancy.CurseOfSoulbinding", remap = false)
public abstract class MixinWizardrySoulbinding {
    @Inject(method = "onEntityHit", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcmagic$npcBinding(CastContext ctx, EntityHitResult hit, Vec3 origin, CallbackInfoReturnable<Boolean> cir) {
        if (!(ctx.caster() instanceof EntityNPCInterface npc) || !(hit.getEntity() instanceof LivingEntity target)) return;
        if (ctx.world().isClientSide) { cir.setReturnValue(true); return; }
        var runtime = MagicRuntimeData.get(npc.getServer());
        var links = runtime.getSoulbound(npc.getUUID());
        for (Tag link : links) if (NbtUtils.loadUUID(link).equals(target.getUUID())) { cir.setReturnValue(false); return; }
        links.add(NbtUtils.createUUID(target.getUUID()));
        runtime.putSoulbound(npc.getUUID(), links);
        target.addEffect(new MobEffectInstance(EBMobEffects.CURSE_OF_SOULBINDING.get(), Integer.MAX_VALUE));
        cir.setReturnValue(true);
    }

    @Inject(method = "onLivingHurt", at = @At("HEAD"), remap = false)
    private static void cnpcmagic$npcRetaliation(EBLivingHurtEvent event, CallbackInfo ci) {
        if (!(event.getDamagedEntity() instanceof EntityNPCInterface npc) || !(npc.level() instanceof ServerLevel level)) return;
        var runtime = MagicRuntimeData.get(level.getServer());
        var links = runtime.getSoulbound(npc.getUUID());
        boolean removed = false;
        for (int i = links.size() - 1; i >= 0; i--) {
            var entity = level.getEntity(NbtUtils.loadUUID(links.get(i)));
            if (!(entity instanceof LivingEntity target) || !target.hasEffect(EBMobEffects.CURSE_OF_SOULBINDING.get())) { links.remove(i); removed = true; }
            else if (target.hurt(MagicDamageSource.causeDirectMagicDamage(npc, EBDamageSources.SORCERY), event.getAmount()))
                target.playSound(EBSounds.SPELL_CURSE_OF_SOULBINDING_RETALIATE.get(), 1, level.random.nextFloat() * 0.2f + 1);
        }
        if (removed) runtime.putSoulbound(npc.getUUID(), links);
    }
}
