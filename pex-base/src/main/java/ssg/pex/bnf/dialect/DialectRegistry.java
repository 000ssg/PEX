package ssg.pex.bnf.dialect;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ssg.pex.bnf.model.Grammar;
import ssg.pex.spi.GrammarProvider;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;

public final class DialectRegistry {

    private static final Logger log = LoggerFactory.getLogger(DialectRegistry.class);

    private final Map<String, GrammarProvider> providers = new HashMap<>();

    private DialectRegistry() {
        reload();
    }

    private static final class Holder {
        static final DialectRegistry INSTANCE = new DialectRegistry();
    }

    public static DialectRegistry getInstance() {
        return Holder.INSTANCE;
    }

    public void register(GrammarProvider provider) {
        log.debug("Registering grammar provider: {}", provider.dialectName());
        providers.put(provider.dialectName(), provider);
    }

    public void reload() {
        providers.clear();
        var loader = ServiceLoader.load(GrammarProvider.class);
        for (var provider : loader) {
            log.debug("Discovered grammar provider via ServiceLoader: {}", provider.dialectName());
            providers.put(provider.dialectName(), provider);
        }
        log.info("DialectRegistry loaded {} provider(s)", providers.size());
    }

    public Grammar resolveGrammar(String dialectName) {
        var provider = providers.get(dialectName);
        if (provider == null) {
            throw new IllegalArgumentException("No grammar provider found for dialect: " + dialectName);
        }

        // Resolve the base grammar first (either from this provider or its parent)
        Grammar grammar;
        var basedOn = provider.basedOn();
        if (basedOn != null) {
            log.debug("Resolving dialect chain: {} -> {}", dialectName, basedOn);
            grammar = resolveGrammar(basedOn);
        } else {
            grammar = provider.provideGrammar();
        }

        // Apply any dialect extensions
        for (var ext : provider.provideExtensions()) {
            grammar = grammar.withDialect(ext);
        }

        return grammar;
    }
}
