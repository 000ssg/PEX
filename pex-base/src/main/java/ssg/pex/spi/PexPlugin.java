package ssg.pex.spi;

public interface PexPlugin {

    String name();

    default int loadOrder() {
        return 1000;
    }

    default void initialize(PluginContext ctx) {}
}
