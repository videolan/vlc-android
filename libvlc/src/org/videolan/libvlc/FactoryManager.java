package org.videolan.libvlc;

import android.util.Log;

import org.videolan.libvlc.interfaces.IComponentFactory;
import org.videolan.libvlc.interfaces.ILibVLCFactory;
import org.videolan.libvlc.interfaces.IMediaFactory;

import java.util.HashMap;
import java.util.Map;

public class FactoryManager {
    private static Map<String, IComponentFactory> factories = new HashMap<>();

    public static void registerFactory(String factoryId, IComponentFactory factory) {
        factories.put(factoryId, factory);
    }

    public static IComponentFactory getFactory(String factoryId) {
        IComponentFactory factory = factories.get(factoryId);
        // Fallback in case the factories have not been populated. It happens in some occasions when the custom Application class has not been instantiated (probably due to the app being in a backup routine)
        if (factory == null) {
            Log.e("FactoryManager", "Factory doesn't exist. Falling back to hard coded one");
            if (factoryId.equals(IMediaFactory.factoryId)) registerFactory(IMediaFactory.factoryId, new MediaFactory());
            if (factoryId.equals(ILibVLCFactory.factoryId)) registerFactory(ILibVLCFactory.factoryId, new LibVLCFactory());
            factory = factories.get(factoryId);
        }
        return factory;
    }
}
