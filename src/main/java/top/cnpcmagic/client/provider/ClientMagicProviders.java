package top.cnpcmagic.client.provider;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ClientMagicProviders {
    private static final Map<String, ClientMagicProvider> PROVIDERS = new LinkedHashMap<>();

    private ClientMagicProviders() {
    }

    public static void register(ClientMagicProvider provider) {
        PROVIDERS.put(provider.getId(), provider);
    }

    public static Collection<ClientMagicProvider> all() {
        return Collections.unmodifiableCollection(PROVIDERS.values());
    }

    public static ClientMagicProvider get(String id) {
        return PROVIDERS.get(id);
    }

    public static ClientMagicProvider forSpell(String spell) {
        int colon = spell == null ? -1 : spell.indexOf(':');
        if (colon < 0) return null;
        ClientMagicProvider direct = get(spell.substring(0, colon));
        if (direct != null) return direct;
        for (ClientMagicProvider provider : PROVIDERS.values()) if (provider.getSpellIds().contains(spell)) return provider;
        return null;
    }
}
