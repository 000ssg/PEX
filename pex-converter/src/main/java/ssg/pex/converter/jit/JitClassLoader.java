package ssg.pex.converter.jit;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class JitClassLoader extends ClassLoader {

    private final Map<String, byte[]> classBytecodes = new ConcurrentHashMap<>();

    public JitClassLoader(ClassLoader parent) {
        super(parent);
    }

    public void addClass(String name, byte[] bytecode) {
        classBytecodes.put(name, bytecode);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        byte[] bytecode = classBytecodes.get(name);
        if (bytecode != null) {
            return defineClass(name, bytecode, 0, bytecode.length);
        }
        throw new ClassNotFoundException(name);
    }
}
