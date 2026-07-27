package ssg.pex.spi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.ServiceLoader;

public class PluginRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(PluginRegistry.class);

    private List<PexPlugin> plugins;

    private PluginRegistry() {}

    private static final class Holder {
        static final PluginRegistry INSTANCE = new PluginRegistry();
    }

    public static PluginRegistry getInstance() {
        return Holder.INSTANCE;
    }

    public List<PexPlugin> loadPlugins() {
        if (plugins == null) {
            reload();
        }
        return List.copyOf(plugins);
    }

    public void initializeAll(PluginContext ctx) {
        for (var plugin : loadPlugins()) {
            LOG.info("Initializing plugin: {} (order={})", plugin.name(), plugin.loadOrder());
            try {
                plugin.initialize(ctx);
            } catch (Exception e) {
                LOG.error("Failed to initialize plugin: {}", plugin.name(), e);
            }
        }
    }

    public void reload() {
        var loaded = new ArrayList<PexPlugin>();
        for (var plugin : ServiceLoader.load(PexPlugin.class)) {
            LOG.debug("Discovered plugin: {} (order={})", plugin.name(), plugin.loadOrder());
            loaded.add(plugin);
        }
        loaded.sort(Comparator.comparingInt(PexPlugin::loadOrder));
        this.plugins = loaded;
        LOG.info("Loaded {} plugins", loaded.size());
    }
}
