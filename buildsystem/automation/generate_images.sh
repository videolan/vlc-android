#!/bin/bash
#
# *************************************************************************
#  generate_images.sh
# **************************************************************************
# Copyright © 2024 VLC authors and VideoLAN
# Author: Nicolas POMEPUY
# This program is free software; you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation; either version 2 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program; if not, write to the Free Software
# Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
# ***************************************************************************
#
#
#

export LANGUAGE=fr_FR
export TEXTDOMAINDIR="$(pwd)/framing/locale"
export TEXTDOMAIN=framing
echo "TEXTDOMAINDIR is $TEXTDOMAINDIR"

function blue() {
  echo -e "\033[1;34m===================\n$1\n===================\033[0m"
}

function purple() {
  echo -e "\033[1;35m$1\033[0m"
}

function yellow() {
  echo -e "\033[1;33m$1\033[0m"
}

function log() {
  if $verbose ;
  then
    echo "$1"
  fi
}

reset=false
genPhone=false
genSeven=false
genTen=false
verbose=false
declare -i nbOfFile=0

#Arguments
while [ $# -gt 0 ]; do
    case $1 in
        help|--help|-h)
            echo "Use -r/--reset to reset"
            echo "Use -p/--phone to generate phone screenshots"
            echo "Use -s/--seven to seven inches tablet phone screenshots"
            echo "Use -t/--ten to ten inches tablet phone screenshots"
            echo "Use -v for verbose logs"
            exit 0
            ;;
        -r|--reset)
            reset=true
            ;;
        -p|--phone)
            genPhone=true
            ;;
        -s|--seven)
            genSeven=true
            ;;
        -t|--ten)
            genTen=true
            ;;
        -v)
            verbose=true
            ;;
        *)
            diagnostic "$0: Invalid option '$1'."
            diagnostic "$0: Try --help for more information."
            exit 1
            ;;
    esac
    shift
done

if [[ $genPhone == false ]] && [[ $genSeven == false ]] && [[ $genTen == false ]] ;
then
  genPhone=true
  genSeven=true
  genTen=true
fi

phone_strings() {
    case $1 in
        '01_video_l') echo "Play all video formats";;
        '02_audio_l') echo "Play all audio formats";;
        '03_audio_p') echo "Intuitive audio player";;
        '04_audio_p') echo "Audio equalizer for a sound to your taste";;
        '05_browser') echo "Easy to use file browser";;
        '06_video_p') echo "Video player with gesture controls and other advanced options";;
        '07_pip_vid') echo "Keep watching with the popup player while using other apps";;
        '08_all_dev') echo "Play everywhere on all of your Android devices";;
        *) echo 'Unknown';;
    esac
}

get_font() {
  case $1 in
        'ja-JP') echo "NotoSansJP";;
        'ko-KR') echo "NotoSansKR";;
        'hi-IN') echo "NotoSans";;
        'el-GR') echo "NotoSans";;
        'ka-GE') echo "NotoSansGeorgian";;
        'he-IL') echo "NotoSansHebrew";;
        'zh-TW') echo "NotoSansSC";;
        'zh-CN') echo "NotoSansSC";;
        'ar') echo "NotoAR";;
        *) echo 'Raleway';;
    esac
}


# Generates the screenshots
generate_screenshot () {
  lang=$1
  imageType=$2
  filename=$3
  filePath=$4
  eqFilename=$5
  log "Attempt creating $filePath // $eqFilename // $imageType"
  if [ ! -f ./framed/$filePath ]; then
    log "File not found!!!"
    nbOfFile+=1
    if [[ $imageType == "phoneScreenshots" ]] || [[ $imageType == "sevenInchScreenshots" ]] || [[ $imageType == "tenInchScreenshots" ]] ;
    then

      blue "Generating screen for lang $lang->$imageType: $filename at $filePath"

      isTablet=false
      if [[ $imageType == "sevenInchScreenshots" ]] ;
      then
        isTablet=true
      fi
      isAllDevices=false
      if [[ $filename == "08_all_devices.png" ]] ;
      then
        isAllDevices=true
      fi

      dir=framed/$filePath
      mkdir -p "$(dirname "$dir")"

      firstChars="${filename:0:10}"
      translation=$(gettext -s "$(phone_strings "$firstChars")")
      inLandscape=false
      forEq=false
      forPip=false
      charSplit=22
      initialTextYOffset=375

      if ! $isAllDevices; then
        if $isTablet; then
          initialTextYOffset=590
        fi
        if [[ $firstChars == "06_video_p" ]]; then
          inLandscape=true
          initialTextYOffset=280
          if $isTablet; then
            initialTextYOffset=240
          fi
          charSplit=50
        fi
        if [[ $imageType == "tenInchScreenshots" ]] ;
        then
          initialTextYOffset=252
          charSplit=50
        fi
      fi

      if [[ $firstChars == "04_audio_p" ]]; then
        forEq=true
      fi
      if [[ $firstChars == "07_pip_vid" ]]; then
        forPip=true
      fi

      imageOffsetX=360
      imageOffsetY=780
      imageOffsetLandscapeX=480
      imageOffsetLandscapeY=592
      templatePrefix="background_pixel_2_XL"
      eqWidth=1440
      eqHeight=1815
      eqX=0
      eqY=975
      eqFrameX=550
      eqFrameY=1360
      pipFrameX=1050
      pipFrameY=2460
      borderWidth=30
      portraitSize="2160x3840"
      imageWidth=2160
      gestureLeftX=850
      gestureRightX=2734
      declare -i fontSize=150
      if $inLandscape; then
        fontSize=112
        imageWidth=3840
      fi
      if $isAllDevices; then
        fontSize=150
        imageWidth=2160

      elif [[ $imageType == "sevenInchScreenshots" ]] ;
        then
        templatePrefix="background_seven_inches"
        imageOffsetX=330
        imageOffsetY=1220
        imageOffsetLandscapeX=720
        imageOffsetLandscapeY=520
        eqWidth=1024
        eqHeight=1038
        eqX=238
        eqY=1298
        eqFrameX=900
        eqFrameY=2050
        pipFrameY=2460
        borderWidth=40
      elif [[ $imageType == "tenInchScreenshots" ]] ;
      then
        templatePrefix="background_ten_inches"
        portraitSize="3840x2160"
        imageWidth=3840
        imageOffsetX=920
        imageOffsetY=544
        imageOffsetLandscapeX=920
        imageOffsetLandscapeY=544
        eqWidth=768
        eqHeight=782
        eqX=616
        eqY=670
        eqFrameX=2600
        eqFrameY=1050
        pipFrameX=2450
        pipFrameY=1350
        borderWidth=40
        gestureLeftX=970
        gestureRightX=2614
        fontSize=112
      fi

      eqFrameWidth=$(($eqWidth+$((2*$borderWidth))))
      eqFrameHeight=$(($eqHeight+$((2*$borderWidth))))

      textHeight=$(bc <<< "$initialTextYOffset * 2 - 40")
      textWidth=$(bc <<< "$imageWidth - 40")

      font=$(get_font "$lang")
      purple  "Generating image with alldevices: $isAllDevices, land: $inLandscape, eq: $forEq, firstChars: $firstChars, eqFrame $eqFrameWidth x $eqFrameHeight, textOffset: $textOffsetY, textHeight: $textHeight font $font, nb of chars is $charSplit text $string"

      if $isAllDevices; then
        ./magick \
          \( -background transparent -fill white -gravity Center -pointsize "$fontSize" -font ./framing/fonts/"$font".ttf -size "$textWidth"x caption:"$translation" \) \
          -write mpr:text +delete \
          -size $portraitSize canvas:none -gravity NorthWest \
          ./framing/templates/all_devices.png -composite \
          \( mpr:text -resize "$textWidth"x"$textHeight" -background transparent -gravity Center -extent "$textWidth"x"$textHeight" \) -gravity North -geometry +0+20 -composite \
          ./framed/$filePath
      elif $forPip; then
        ./magick \
            \( -background none -fill white -gravity Center -pointsize "$fontSize" -font ./framing/fonts/"$font".ttf -size "$textWidth"x caption:"$translation" \) \
            -write mpr:text +delete \
            -size $portraitSize canvas:none -gravity NorthWest \
            \( $filePath -geometry +"$imageOffsetX"+"$imageOffsetY" \) -composite \
            ./framing/templates/"$templatePrefix"_portrait.png -composite \
            \( ./framing/templates/"$templatePrefix"_pip.png -geometry +"$pipFrameX"+"$pipFrameY" \) -composite \
            \( mpr:text -resize "$textWidth"x"$textHeight" -background transparent -gravity Center -extent "$textWidth"x"$textHeight" \) -gravity North -geometry +0+20 -composite \
            ./framed/$filePath
      elif $forEq; then
        purple "prefix is $eqFilename"
        ./magick \
            \( -background transparent -fill white -gravity Center -pointsize "$fontSize" -font ./framing/fonts/"$font".ttf -size "$textWidth"x caption:"$translation" \) \
            -write mpr:text +delete \
            -size "$eqFrameWidth"x"$eqFrameHeight" canvas:none -gravity NorthWest \
            \( $eqFilename -crop "$eqWidth"x"$eqHeight"+"$eqX"+"$eqY" -geometry +"$borderWidth"+"$borderWidth"  -alpha Set ./framing/templates/"$templatePrefix"_eq_mask.png \) -composite \
            \( ./framing/templates/"$templatePrefix"_eq.png \) -composite \
            -write mpr:eq +delete \
            -size $portraitSize canvas:none \
            \( $filePath -geometry +"$imageOffsetX"+"$imageOffsetY" \) -composite \
            ./framing/templates/"$templatePrefix"_portrait.png -composite \
            \( mpr:eq -geometry +"$eqFrameX"+"$eqFrameY" \) -composite \
            \( mpr:text -resize "$textWidth"x"$textHeight" -background transparent -gravity Center -extent "$textWidth"x"$textHeight" \) -gravity North -geometry +0+20 -composite \
            ./framed/$filePath
      elif $inLandscape; then
        purple "Text is $string, font is $font, lang is $lang"
        ./magick \
            \( -background transparent -fill white -gravity Center -pointsize "$fontSize" -font ./framing/fonts/"$font".ttf -size "$textWidth"x caption:"$translation" \) \
            -write mpr:text +delete \
            -size 3840x2160 canvas:none -gravity NorthWest \
            \( $filePath -geometry +"$imageOffsetLandscapeX"+"$imageOffsetLandscapeY" \) -composite \
            ./framing/templates/"$templatePrefix"_landscape.png -composite \
            \( ./framing/templates/swipe_down.png -geometry +"$gestureLeftX"+1142 \) -composite \
            \( ./framing/templates/swipe.png -geometry +1792+1142 \) -composite \
            \( ./framing/templates/swipe_up.png -geometry +"$gestureRightX"+1142 \) -composite \
            \( mpr:text -resize "$textWidth"x"$textHeight" -background transparent -gravity Center -extent "$textWidth"x"$textHeight" \) -gravity North -geometry +0+20 -composite \
            ./framed/$filePath

        else
          ./magick \
              \( -background transparent -fill white -gravity Center -pointsize "$fontSize" -font ./framing/fonts/"$font".ttf -size "$textWidth"x caption:"$translation" \) \
              -write mpr:text +delete \
              -size $portraitSize canvas:none -gravity NorthWest \
              \( $filePath -geometry +"$imageOffsetX"+"$imageOffsetY" \) -composite \
              ./framing/templates/"$templatePrefix"_portrait.png -composite \
              \( mpr:text -resize "$textWidth"x"$textHeight" -background transparent -gravity Center -extent "$textWidth"x"$textHeight" \) -gravity North -geometry +0+20 -composite \
              ./framed/$filePath

      fi



      echo "Writing in framed/$filePath"

    else
      #10 inch tablet
      ./magick -size 2160x3840 canvas:none \
              \( $filePath -geometry +"$imageOffsetX"+"$imageOffsetY" \) -composite \
              ./framing/templates/"$templatePrefix"_portrait.png -composite \
              -gravity North -pointsize 150 -fill "#FFFFFF" -font ./framing/fonts/"$font".ttf -annotate +0+"$textOffsetY" "$string" \
              ./framed/$filePath
    fi
  fi

}

FILE=./magick
if [[ -f "$FILE" ]]; then
    purple "$FILE exists."
else
  purple "Magick not found. It will be downloaded"
  read -sp "Continue: y/N" -n 1 -r
  echo

  if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    [[ "$0" == "$BASH_SOURCE" ]] && exit 1 || return 1 # handle exits from shell or function but don't exit interactive shell
  fi

  wget https://imagemagick.org/archive/binaries/magick
  wget https://imagemagick.org/archive/binaries/magick.asc

  # verify the signature
  export GNUPGHOME="$(mktemp -d)"
  gpg --keyserver keyserver.ubuntu.com --recv-keys 89AB63D48277377A
  gpg --batch --verify ./magick.asc ./magick
  rm -rf "$GNUPGHOME" ./magick.asc


  chmod +x ./magick

fi

purple "Compile the translations"
translationFileList=(`find framing/locale/ -name \*.po`)

for path in "${translationFileList[@]}"; {
  filename=$(basename -- "$path")
  filename="${filename%.*}"
  mkdir -p ./framing/locale/"$filename"/LC_MESSAGES
  msgfmt -o  framing/locale/"$filename"/LC_MESSAGES/framing.mo "$path"
}


if $reset; then
  purple "Are you sur you want to delete all files?"
  read -sp "Continue: y/N" -n 1 -r
  echo

  if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    [[ "$0" == "$BASH_SOURCE" ]] && exit 1 || return 1 # handle exits from shell or function but don't exit interactive shell
  fi
  rm -rf "framed"
  mkdir "framed"
fi

dirlist=(`find fastlane/metadata/android/ -mindepth 1 -maxdepth 1 -type d`)

# parse all dirs
for path in "${dirlist[@]}"; {

  lang=${path##*/}
  #change lang for i18n
  export LANGUAGE=$(echo "$lang".utf8 | tr - _)
  log "Language is now $LANGUAGE"

  screendirlist=(`find "$path/images" -mindepth 1 -maxdepth 1 -type d`)

  # parse form factor dirs
  for imagePath in "${screendirlist[@]}"; {
    imageType=${imagePath##*/}
    playlistFilePath=""
    playlistFileName=""
    allDeviceGenerated=false

    #parse files
    for filePath in "$imagePath"/*
    do
      filename=${filePath##*/}
      firstChars="${filename:0:10}"

      if [[ $firstChars == "04_audio_p" ]] ;
      then
        playlistFilePath=$filePath
        playlistFileName=$filename
      fi

      if [[ $filename != "*" ]] && [[ $imageType == "sevenInchScreenshots" ]] && $genSeven ;
      then
         if [[ $firstChars == "09_equaliz" ]] ;
          then
              generate_screenshot "$lang" "$imageType" "$playlistFileName" "$playlistFilePath" "$filePath"
          else
              generate_screenshot "$lang" "$imageType" "$filename" "$filePath"
          fi
      fi
      if [[ $filename != "*" ]] && [[ $imageType == "phoneScreenshots" ]] && $genPhone ;
      then
        if [[ $firstChars == "09_equaliz" ]] ;
        then
            generate_screenshot "$lang" "$imageType" "$playlistFileName" "$playlistFilePath" "$filePath"
        else
          generate_screenshot "$lang" "$imageType" "$filename" "$filePath"
        fi
      fi
      if [[ $filename != "*" ]] && [[ $imageType == "tenInchScreenshots" ]] && $genTen ;
      then
        if [[ $firstChars == "09_equaliz" ]] ;
        then
            generate_screenshot "$lang" "$imageType" "$playlistFileName" "$playlistFilePath" "$filePath"
        else
          generate_screenshot "$lang" "$imageType" "$filename" "$filePath"
        fi
      fi

      if [[ $filename != "*" ]] && ! $allDeviceGenerated ;
      then
        allDeviceGenerated=true
        generate_screenshot "$lang" "$imageType" 08_all_devices.png "$imagePath"/08_all_devices.png
      fi


    done
  }

}
purple "$nbOfFile files generated"
