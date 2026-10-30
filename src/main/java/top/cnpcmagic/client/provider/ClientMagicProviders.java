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
}
