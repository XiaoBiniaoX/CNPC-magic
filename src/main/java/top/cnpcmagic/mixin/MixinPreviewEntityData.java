package top.cnpcmagic.mixin;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.network.syncher.SynchedEntityData;
import org.spongepowered.asm.mixin.*;
import top.cnpcmagic.server.MagicPreview;
import java.util.List;

/** Snapshot without consuming the dirty flags used by normal entity tracking. */
@Mixin(SynchedEntityData.class)
public abstract class MixinPreviewEntityData implements MagicPreview.EntityData {
    @Shadow @Final private Int2ObjectMap<SynchedEntityData.DataItem<?>> itemsById;
    @Override public List<SynchedEntityData.DataValue<?>> cnpcmagic$values() {
        List<SynchedEntityData.DataValue<?>> values = new java.util.ArrayList<>();
        for (var item : itemsById.values()) values.add(item.value());
        return values;
    }
}
