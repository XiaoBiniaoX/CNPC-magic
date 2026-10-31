package top.cnpcmagic.data;

import java.util.UUID;

/**
 * Lightweight link carried by an NPC's own NBT so a soulstone or stored clone keeps
 * pointing at the external rule snapshot. Only the reference is stored in NBT.
 */
public interface MagicDataAccess {
    UUID cnpcmagic$getDataId();

    void cnpcmagic$setDataId(UUID dataId);
}
