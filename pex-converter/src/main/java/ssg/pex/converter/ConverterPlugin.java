package ssg.pex.converter;

import ssg.pex.spi.PexPlugin;
import ssg.pex.spi.PluginContext;

public class ConverterPlugin implements PexPlugin {

    @Override
    public String name() {
        return "converter";
    }

    @Override
    public int loadOrder() {
        return 500;
    }

    @Override
    public void initialize(PluginContext ctx) {
        // Converter factories are registered via ServiceLoader through ConverterRegistry.
        // No handler or grammar registration needed for the converter module.
    }
}
