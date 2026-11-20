package top.cnpcmagic.mixin;

import com.binaris.wizardry.api.content.spell.internal.CastContext;
import com.binaris.wizardry.core.platform.Services;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native lock data already stores an entity UUID; NPCs need no substitute player owner. */
@Pseudo
@Mixin(targets = "com.binaris.wizardry.content.spell.sorcery.ArcaneLockSpell", remap = false)
public abstract class MixinWizardryArcaneLock {
    @Shadow private static BlockPos getConnectedChest(Level level, BlockPos pos) { throw new AssertionError(); }

    @Inject(method = "onBlockHit", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcmagic$npcLock(CastContext ctx, BlockHitResult hit, Vec3 origin, CallbackInfoReturnable<Boolean> cir) {
        if (!(ctx.caster() instanceof EntityNPCInterface)) return;
        if (ctx.world().isClientSide) { cir.setReturnValue(true); return; }
        boolean changed = cnpcmagic$toggle(ctx, hit.getBlockPos());
        BlockPos other = getConnectedChest(ctx.world(), hit.getBlockPos());
        if (changed && other != null) cnpcmagic$toggle(ctx, other);
        cir.setReturnValue(changed);
    }

    @Unique private boolean cnpcmagic$toggle(CastContext ctx, BlockPos pos) {
        if (!(ctx.world().getBlockEntity(pos) instanceof BaseContainerBlockEntity container)) return false;
        var data = Services.OBJECT_DATA.getArcaneLockData(container);
        if (data == null || data.isArcaneLocked() && !ctx.caster().getUUID().equals(data.getArcaneLockOwnerUUID())) return false;
        data.setArcaneLockOwner(data.isArcaneLocked() ? null : ctx.caster().getUUID().toString());
        container.setChanged();
        ctx.world().sendBlockUpdated(pos, ctx.world().getBlockState(pos), ctx.world().getBlockState(pos), 3);
        return true;
    }
}
