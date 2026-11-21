package top.cnpcmagic.mixin;

import com.binaris.wizardry.core.platform.Services;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcmagic.client.PreviewScene;
import java.util.Map;

/** The native effect draws the detached containers rather than searching around the real player. */
@Pseudo
@Mixin(targets = "com.binaris.wizardry.client.effect.ArcaneLockRender", remap = false)
public abstract class MixinPreviewArcaneLockRender {
    @Shadow @Final private static Map<BlockPos, AABB> cache;

    @Inject(method = "rebuildCache", at = @At("HEAD"), cancellable = true, remap = false)
    private static void cnpcmagic$previewLocks(Level level, Vec3 origin, ChunkPos chunk, CallbackInfo ci) {
        if (!(level instanceof PreviewScene scene)) return;
        cache.clear();
        for (var blockEntity : scene.previewBlockEntities()) if (blockEntity instanceof BaseContainerBlockEntity container) {
            var data = Services.OBJECT_DATA.getArcaneLockData(container);
            if (data != null && data.isArcaneLocked()) {
                BlockPos pos = container.getBlockPos();
                cache.put(pos, scene.getBlockState(pos).getShape(scene, pos).bounds().inflate(0.05).move(pos));
            }
        }
        ci.cancel();
    }
}
