#!/bin/sh

for i in stagefright media utils cutils binder ui ui-hc hardware; do
	nm -D --defined-only lib$i.so | awk '{print $3}' | grep -v ^__aeabi | grep '\(IInterface\|OMXObserver\|MemoryDealer\|Binder\|RefBase\|String\|OMXClient\|IMemory\|GraphicBuffer\|android_atomic\|^str\|hw_get_module\)' > lib$i.symbols
done

cat libui.symbols libui-hc.symbols | sort | uniq > tmp
mv tmp libui.symbols
rm -f libui-hc.symbols

