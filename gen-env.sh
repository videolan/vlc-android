#!/bin/bash
# Simple script to generate env.txt
ENVTXT=$1/assets/env.txt

function optional_var {
	echo -n "$1=" >> $ENVTXT
	ONE=\$$1
	T=`eval echo $ONE`
	if [ -z "$T" ]; then
		echo -n "0" >> $ENVTXT
	else
		echo -n "$T" >> $ENVTXT
	fi
	echo -n -e "\n" >> $ENVTXT
}

rm -f $ENVTXT
echo -e "ANDROID_ABI=$ANDROID_ABI" >> $ENVTXT
optional_var "NO_FPU"
optional_var "NO_ARMV6"
