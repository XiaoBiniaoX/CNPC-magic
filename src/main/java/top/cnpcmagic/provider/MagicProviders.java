package top.cnpcmagic.provider;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class MagicProviders {
    private static final Map<String, MagicProvider> PROVIDERS = new LinkedHashMap<>();

    private MagicProviders() {
    }

    public static void register(MagicProvider provider) {
        PROVIDERS.put(provider.getId(), provider);
    }

    public static Collection<MagicProvider> all() {
        return Collections.unmodifiableCollection(PROVIDERS.values());
    }

    public static MagicProvider get(String id) {
        return PROVIDERS.get(id);
    }

    public static MagicProvider forSpell(String spell) {
        int colon = spell == null ? -1 : spell.indexOf(':');
        if (colon < 0) return null;
        MagicProvider direct = get(spell.substring(0, colon));
        if (direct != null) return direct;
        // Addons register spells in their own namespace, but use the host mod's casting API.
        for (MagicProvider provider : PROVIDERS.values()) if (provider.isValidSpell(spell)) return provider;
        return null;
    }
}
