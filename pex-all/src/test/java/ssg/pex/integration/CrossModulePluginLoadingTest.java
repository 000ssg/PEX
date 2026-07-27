package ssg.pex.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ssg.pex.arithmetics.ArithmeticsPlugin;
import ssg.pex.converter.ConverterPlugin;
import ssg.pex.exec.ExecutionEngine;
import ssg.pex.exec.FunctionRegistry;
import ssg.pex.exec.HandlerRegistry;
import ssg.pex.spi.GrammarProvider;
import ssg.pex.spi.HandlerProvider;
import ssg.pex.spi.PexPlugin;
import ssg.pex.spi.PluginContext;
import ssg.pex.spi.PluginRegistry;
import ssg.pex.sql.SqlPlugin;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CrossModulePluginLoadingTest {

    private List<PexPlugin> plugins;

    @BeforeEach
    void setUp() {
        var registry = PluginRegistry.getInstance();
        registry.reload();
        plugins = registry.loadPlugins();
    }

    @Test
    void arithmeticsPluginIsDiscovered() {
        assertThat(plugins)
                .anyMatch(p -> p instanceof ArithmeticsPlugin);
    }

    @Test
    void converterPluginIsDiscovered() {
        assertThat(plugins)
                .anyMatch(p -> p instanceof ConverterPlugin);
    }

    @Test
    void sqlPluginIsDiscovered() {
        assertThat(plugins)
                .anyMatch(p -> p instanceof SqlPlugin);
    }

    @Test
    void pluginsAreLoadedInCorrectOrder() {
        // ArithmeticsPlugin (100) < SqlPlugin (200) < ConverterPlugin (500)
        var names = plugins.stream().map(PexPlugin::name).toList();
        int arithmeticsIdx = names.indexOf("arithmetics");
        int sqlIdx = names.indexOf("sql");
        int converterIdx = names.indexOf("converter");

        assertThat(arithmeticsIdx).isGreaterThanOrEqualTo(0);
        assertThat(sqlIdx).isGreaterThanOrEqualTo(0);
        assertThat(converterIdx).isGreaterThanOrEqualTo(0);
        assertThat(arithmeticsIdx).isLessThan(sqlIdx);
        assertThat(sqlIdx).isLessThan(converterIdx);
    }

    @Test
    void pluginRegistryInitializesAllPlugins() {
        var handlers = new HandlerRegistry();
        var functions = new FunctionRegistry();
        var ctx = new PluginContext(handlers, functions, null);

        var registry = PluginRegistry.getInstance();
        registry.initializeAll(ctx);

        // After initialization, handlers and functions should be registered
        assertThat(handlers.registeredTypes()).isNotEmpty();
        assertThat(functions.functionNames()).isNotEmpty();
    }

    @Test
    void handlerProviderInterfaceIsSatisfied() {
        assertThat(plugins)
                .filteredOn(p -> p instanceof HandlerProvider)
                .isNotEmpty();

        var arithmeticsPlugin = plugins.stream()
                .filter(p -> p instanceof ArithmeticsPlugin)
                .map(p -> (HandlerProvider) p)
                .findFirst()
                .orElseThrow();

        assertThat(arithmeticsPlugin.provideHandlers()).isNotEmpty();
    }

    @Test
    void grammarProviderInterfaceIsSatisfied() {
        assertThat(plugins)
                .filteredOn(p -> p instanceof GrammarProvider)
                .isNotEmpty();

        var arithmeticsPlugin = plugins.stream()
                .filter(p -> p instanceof ArithmeticsPlugin)
                .map(p -> (GrammarProvider) p)
                .findFirst()
                .orElseThrow();

        assertThat(arithmeticsPlugin.provideGrammar()).isNotNull();
        assertThat(arithmeticsPlugin.dialectName()).isEqualTo("arithmetics");
    }

    @Test
    void executionEngineLoadPluginsIntegration() {
        try (var engine = ExecutionEngine.builder()
                .loadPlugins()
                .build()) {

            assertThat(engine.handlers()).isNotNull();
            assertThat(engine.handlers().registeredTypes()).isNotEmpty();
            assertThat(engine.functions()).isNotNull();
            assertThat(engine.functions().functionNames()).isNotEmpty();
            // Grammar should be set by ArithmeticsPlugin
            assertThat(engine.grammar()).isNotNull();
        }
    }
}
