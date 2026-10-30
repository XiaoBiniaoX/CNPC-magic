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
}
