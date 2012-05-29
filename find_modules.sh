#!/bin/sh

if [ $# != 1 ]
then
    echo "Usage: $0 [vlc build dir]"
    exit 1
fi

blacklist="
stats
access_(bd|shm|imem)
oldrc
real
hotkeys
gestures
sap
dynamicoverlay
rss
ball
audiobargraph_[av]
clone
mosaic
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
motiondetect
motionblur
export
smf
podcast
bluescreen
erase
stream_filter_record
speex_resampler
remoteosd
magnify
gradient
.*tospdif
dtstofloat32
logger
visual
fb
aout_file
yuv
.dummy
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

find $1/modules -name 'lib*plugin.a' | grep -vE "lib(${regexp})_plugin.a" | tr '\n' ' '
