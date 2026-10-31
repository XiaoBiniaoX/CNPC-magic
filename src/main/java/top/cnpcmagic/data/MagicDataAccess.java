package top.cnpcmagic.data;

import java.util.UUID;

/** Only a reference is carried by NPCs; rules remain in the world's separate data store. */
public interface MagicDataAccess {
    UUID cnpcmagic$getDataId();
    void cnpcmagic$setDataId(UUID dataId);
}
