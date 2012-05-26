#!/bin/sh

if [ $# != 1 ]
then
    echo "Usage: $0 [vlc build dir]"
    exit 1
fi

blacklist="
stats
access_bd
oldrc
real
hotkeys
gestures
sap
dynamicoverlay
rss
libball
bargraph
clone
access_shm
mosaic
imem
osdmenu
puzzle
mediadirs
t140
ripple
motion
sharpen
grain
posterize
mirror
wall
scene
blendbench
psychedelic
alphamask
netsync
audioscrobbler
imem
motiondetect
export
smf
podcast
bluescreen
erase
record
speex_resampler
remoteosd
magnify
gradient
spdif
dtstofloat32
logger
"

regexp=
for i in ${blacklist}
do
    if [ -z "${regexp}" ]
    then
        regexp="${i}"
    else
        regexp="${regexp}|${i}"
    fi
done

find $1/modules -name 'lib*plugin.a' | grep -vE "${regexp}" | tr '\n' ' '
