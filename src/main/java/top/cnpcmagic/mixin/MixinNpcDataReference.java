package top.cnpcmagic.mixin;

import net.minecraft.nbt.CompoundTag;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcmagic.data.MagicDataAccess;

import java.util.UUID;

/**
 * CNPC strips UUID/UUIDMost/UUIDLeast when saving an NPC into a soulstone or clone,
 * so a placed copy cannot look up rules by the old entity UUID. Persist one short
 * reference instead; the rules stay in the world SavedData.
 */
@Mixin(value = EntityNPCInterface.class, remap = false)
public abstract class MixinNpcDataReference implements MagicDataAccess {
    private static final String CNPCMAGIC_DATA_ID = "CNPCMagicDataId";

    @Unique
    private UUID cnpcmagic$dataId;

    @Inject(method = "m_7380_", at = @At("RETURN"), require = 1)
    private void cnpcmagic$writeDataReference(CompoundTag tag, CallbackInfo callback) {
        EntityNPCInterface npc = (EntityNPCInterface) (Object) this;
        UUID reference = cnpcmagic$dataId == null ? npc.getUUID() : cnpcmagic$dataId;
        tag.putString(CNPCMAGIC_DATA_ID, reference.toString());
    }

    @Inject(method = "m_7378_", at = @At("RETURN"), require = 1)
    private void cnpcmagic$readDataReference(CompoundTag tag, CallbackInfo callback) {
        cnpcmagic$dataId = null;
        String reference = tag.getString(CNPCMAGIC_DATA_ID);
        if (reference.length() != 36) return;
        try {
            cnpcmagic$dataId = UUID.fromString(reference);
        } catch (IllegalArgumentException ignored) {
            // Old NPCs or hand-edited data fall back to the entity UUID.
        }
    }

    @Override
    public UUID cnpcmagic$getDataId() {
        return cnpcmagic$dataId;
    }

    @Override
    public void cnpcmagic$setDataId(UUID dataId) {
        cnpcmagic$dataId = dataId;
    }
}
