package top.cnpcmagic.server;

import net.minecraft.world.DimensionType;
import net.minecraft.world.WorldProviderSurface;

/** Uses a dedicated relative save folder instead of Forge's DIM{id} folder. */
public final class MagicPreviewWorldProvider extends WorldProviderSurface {
    @Override
    public DimensionType getDimensionType() {
        return MagicPreview.DIMENSION_TYPE;
    }

    @Override
    public String getSaveFolder() {
        return MagicPreview.getSaveFolder();
    }
}
