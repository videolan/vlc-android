package org.videolan.tools

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap


@Suppress("UNCHECKED_CAST")
open class DependencyProvider<A> {
    val objectMap: ConcurrentMap<String, Any> = ConcurrentHashMap()
    val creatorMap: ConcurrentMap<String, (A) -> Any> = ConcurrentHashMap()

    var overrideCreator = true

    inline fun <T> getKey(clazz: Class<T>): String = clazz.name

    inline fun <X : Any, reified T : X> registerCreator(clazz: Class<X>? = null, noinline creator: (A) -> T) {
        val key = getKey(clazz ?: T::class.java)
        if (overrideCreator || !creatorMap.containsKey(key))
            creatorMap[key] = creator
        if (objectMap.containsKey(key) && overrideCreator) {
            objectMap.remove(key)
        }
    }

    inline fun <X : Any, reified T : X> get(arg: A, clazz: Class<X>? = null): T {
        val key = getKey(clazz ?: T::class.java)
        if (!objectMap.containsKey(key))
            objectMap[key] = creatorMap[key]?.invoke(arg)
        return objectMap[key] as T
    }
}