# Automating screenshots

## Installation

### fastlane

Install [fastlane](https://docs.fastlane.tools/)

Even if Linux is not officially supported, it can be installed easily.

`sudo apt install ruby ruby-dev`

`export LC_ALL=en_US.UTF-8`

`export LANG=en_US.UTF-8`

`sudo gem install fastlane`

### Android SDK

The Android SDK has to be installed and you have to set an env variable to its path

`export ANDROID_SDK=/path/to/sdk/`

## Usage

To launch a fastlane command, run `bundle exec fastlane [command] [option_key1]:[option_value_1] ...`

Available lanes:

### Deployment

Once the build is done by the CI and signed, copy the apks into this directory or you can use the `FASTLANE_APK_PATH` env var to tell fastlane the path to use (relative path only) 

#### Play Store:

**Prerequisites**:

 Get the Play Store certificate and copy the file at `./certificates/vlc.json`
 
 Export an env variable named `K8S_SECRET_VLC_PLAYSTORE_PUBLIC_API_KEY` with the Play Stire API key
 
 Export an env variable named `SLACK_URL` with the slack webhook URL
 

- `deploy_release` sends a release to the Play Store in draft
- `deploy_beta` sends a beta to the Play Store in draft

Options: `version` is the version string in the apk name.

For example: `bundle exec fastlane deploy_beta version:"3.3.1-Beta-1"` creates a draft beta version on the Play Store for apks: 

```
VLC-Android-3.3.1-Beta-1-arm64-v8a.apk
VLC-Android-3.3.1-Beta-1-armeabi-v7a.apk
VLC-Android-3.3.1-Beta-1-x86.apk
VLC-Android-3.3.1-Beta-1-x86_64.apk
```

#### Huawei

**Prerequisites**:

You need to export 3 env variables: HUAWEI_CLIENT_ID, HUAWEI_CLIENT_SECRET and HUAWEI_APP_ID

- `deploy_huawei` sends a release to the Huawei AppGallery in draft

Options: `version` is the version string in the apk name.

#### FTP

**Prerequisites**:

You need to export 1 env variable: VIDEOLAN_FTP_HOST

- `deploy_ftp`

Options: `version` is the version string in the apk name.

The 4 apks and the 4 `.sha256` files will be uploaded in the `/incoming/[version]` folder of the FTP with anonymous credentials

### Screenshots

#### Take screenshots

**Prerequisites**:

In order to make the script work, you have to **create 3 AVDs**, **install VLC** and **add the samples**

You should also skip the welcome screens and let the scanning occur.

- `screenshots`, `screenshots_seven`, `screenshots_ten`, `screenshots_tv` lanes launch respectively the screenshot UI tests for mobile, 7" tablets, 10" tablets and TV  

Options: `version` is the version string in the apk name.

Before running, the app and the test apks will be generated automatically

#### Upload the screenshots

- `deploy_screenshots`