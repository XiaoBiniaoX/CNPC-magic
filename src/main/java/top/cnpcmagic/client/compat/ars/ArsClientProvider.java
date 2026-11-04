package top.cnpcmagic.client.compat.ars;

import com.hollingsworth.arsnouveau.api.registry.GlyphRegistry;
import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import net.minecraft.resources.ResourceLocation;
import top.cnpcmagic.client.provider.ClientMagicProvider;
import top.cnpcmagic.compat.ars.ArsAdapter;

import java.util.List;

public class ArsClientProvider implements ClientMagicProvider {
    @Override
    public String getId() {
        return "ars_nouveau";
    }

    @Override
    public List<String> getSpellIds() {
        return ArsAdapter.getSpellIds();
    }

    @Override
    public String getDisplayName(String id) {
        int split = id == null ? -1 : id.indexOf('+');
        if (split <= 0) {
            return id;
        }
        return name(id.substring(0, split)) + " · " + name(id.substring(split + 1));
    }

    private String name(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        AbstractSpellPart part = location == null ? null : GlyphRegistry.getSpellpartMap().get(location);
        return part == null ? id : part.getLocaleName();
    }

    @Override
    public int getMaxLevel(String id) {
        return ArsAdapter.getMaxLevel(id);
    }
}
