package ssg.pex.converter.spi;

import ssg.pex.converter.ConversionConfig;
import ssg.pex.converter.Converter;
import ssg.pex.converter.TargetLanguage;

import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ConverterRegistry {

    private final Map<TargetLanguage, ConverterFactory> factories = new ConcurrentHashMap<>();

    private ConverterRegistry() {
        reload();
    }

    private static final class Holder {
        static final ConverterRegistry INSTANCE = new ConverterRegistry();
    }

    public static ConverterRegistry getInstance() {
        return Holder.INSTANCE;
    }

    public Optional<Converter> getConverter(TargetLanguage target, ConversionConfig config) {
        var factory = factories.get(target);
        return factory != null ? Optional.of(factory.create(config)) : Optional.empty();
    }

    public Optional<Converter> getConverter(TargetLanguage target) {
        return getConverter(target, ConversionConfig.defaults());
    }

    public Set<TargetLanguage> availableTargets() {
        return Set.copyOf(factories.keySet());
    }

    public void register(ConverterFactory factory) {
        factories.put(factory.target(), factory);
    }

    public void reload() {
        factories.clear();
        ServiceLoader.load(ConverterFactory.class).forEach(f -> factories.put(f.target(), f));
    }
}
