package ssg.pex.spi;

import ssg.pex.exec.NodeHandler;

import java.util.Map;

public interface HandlerProvider {

    Map<Class<?>, NodeHandler> provideHandlers();
}
