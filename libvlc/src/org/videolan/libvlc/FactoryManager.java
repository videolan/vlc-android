package org.videolan.libvlc;

import org.videolan.libvlc.interfaces.IComponentFactory;

import java.util.HashMap;
import java.util.Map;

public class FactoryManager {
    private static Map<String, IComponentFactory> factories = new HashMap<>();

    public static void registerFactory(String factoryId, IComponentFactory factory) {
        factories.put(factoryId, factory);
    }

    public static IComponentFactory getFactory(String factoryId) {
        return factories.get(factoryId);
    }
}
