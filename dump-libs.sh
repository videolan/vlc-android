#!/bin/sh

for i in stagefright media utils cutils binder ui; do
	nm -D --defined-only lib$i.so | awk '{print $3}' | grep -v ^__aeabi | grep '\(IInterface\|OMXObserver\|MemoryDealer\|Binder\|RefBase\|String\|OMXClient\|IMemory\|GraphicBuffer\|android_atomic\|^str\)' > lib$i.symbols
done

