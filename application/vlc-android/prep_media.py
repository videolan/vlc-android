#!/usr/bin/python3.7

from pymediainfo import MediaInfo
import time
import datetime
import sys
from os import listdir
import os.path
import argparse
import collections
import json

class JsonManager:

    def __init__(self, path):
        if not os.path.exists(path):
            self.jsonfile = open(path, "w+")
        else:
            self.jsonfile = open(path, "r+")

    def getData(self):
        jsonstr = self.jsonfile.read()
        if jsonstr == "":
            return []
        return json.loads(jsonstr)

    def dump(self, data):
        datastr = json.dumps(data, indent=4, separators=(',', ':'))
        self.jsonfile.seek(0)
        self.jsonfile.write(datastr)
        self.jsonfile.truncate()
        self.jsonfile.close()


def fileLoop(dirpath, csvWriter):
    for filename in listdir(dirpath):
        csvWriter.writerow(getMediaCSV(dirpath + filename))
    return

def print_dict( d ):
    d = dict( collections.OrderedDict( sorted( d.items() ) ) )
    for key, value in d.items():
        print( "{0}: {1}".format( key, value ) )

def printMediaInfo( filepath ):
    media_info = MediaInfo.parse( filepath )
    for track in media_info.tracks:
        print(track.track_type)
        if (track.track_type == 'General'
                or track.track_type == 'Video'
                or track.track_type == 'Audio'):
            print( "type: {0}: ".format( track.track_type ) )
            print_dict( track.__dict__ )
            print()

def get(track, key, default):
    if hasattr(track, key) and getattr(track, key) != None:
        return getattr(track, key)
    return default

def getMediaInfo(filepath):
    data = dict()
    data["width"] = 0
    data["height"] = 0
    data["spu"] = -2
    data["type"] = -1
    media_info = MediaInfo.parse(filepath)
    for track in media_info.tracks:
        if track.track_type == 'General':
            data["title"] = getattr(track, "title")
            mrl = "/".join(getattr(track, "complete_name").split("/")[3:])
            print(mrl)
            data["mrl"] = "/storage/emulated/0/" + mrl
            data["filename"] = data["mrl"].split("/")[-1]
            if data["title"] == None or data["title"] == "":
                data["title"] = data["filename"]
            last_mod_str = getattr(track, "file_last_modification_date__local")
            timestamp = time.mktime(datetime.datetime.strptime(
                last_mod_str, "%Y-%m-%d %H:%M:%S").timetuple())
            data["last_modified"] = int(timestamp)
            data["length"] = int(get(track, "duration", "0"))
            data["artist"] = get(track, "performer", "")
            data["album"] = get(track, "album", "")
            data["album_artist"] = get(track, "album_performer", "")
            data["artwork_url"] = ""
            data["year"] = int(get(track, "recorded_date", "0"))
            data["genre"] = get(track, "genre", "")
            data["track_number"] = int(get(track, "track_name_position", "0"))
            data["track_total"] = int(get(track, "track_name_total", "0"))
            data["audio"] = int(get(track, "count_of_audio_streams", "1"))
        if track.track_type == 'Video':
            data["type"] = 0
            data["width"] = int(get(track, "width", "0"))
            data["height"] = int(get(track, "height", "0"))
            data["genre"] = ""
        if track.track_type == 'Audio':
            if data["type"] != 0:
                data["type"] = 1
            data["spu"] = -2
    if data["type"] == -1:
        return None
    print(data)
    return data

description = "Extracts mediainfo data from a file or directory to add them "
description += "to a media configuration file for the medialibrary stub."
parser = argparse.ArgumentParser( description=description )
parser.add_argument( '-i', '--input', dest='input_path',
        help="path to file or directory" )
parser.add_argument( '-o', '--output', dest='output',
        help="file to save output, mendatory and only for a directory" )
parser.add_argument( '-p', '--print', dest='vprint',
        help="Print media info of specified file")

args = parser.parse_args()
input_path = args.input_path
output = args.output
vprint = args.vprint

if vprint != None:
    printMediaInfo(vprint)
    exit( 0 )

if input_path != None:
    if os.path.exists( input_path ) == False:
        print( "Error: input: No such file or directory" )
        exit( 1 )
    if output == None:
        print( "Error: You didn't specify an output file" )
        exit( 1 )

    jsonManager = JsonManager(output)
    json_data = jsonManager.getData()
    if os.path.isdir( input_path ) == True:
        print( "Input directory: " + str( input_path ) )
        for filename in os.listdir( input_path ):
            media_data = getMediaInfo( input_path + filename )
            if (media_data != None):
                json_data.append( media_data )
    else:
        json_data.append( getMediaInfo( input_path ) )
    jsonManager.dump(json_data)
else:
    parser.print_help()

