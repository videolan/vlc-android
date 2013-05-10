/*****************************************************************************
 * LibVlcUtil.java
 *****************************************************************************
 * Copyright © 2011-2012 VLC authors and VideoLAN
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *****************************************************************************/

package org.videolan.libvlc;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;

import org.videolan.vlc.Util;
import org.videolan.vlc.VLCApplication;

import android.util.Log;

public class LibVlcUtil {
    public final static String TAG = "LibVlc/Util";

    public static boolean isFroyoOrLater()
    {
        return android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.FROYO;
    }

    public static boolean isGingerbreadOrLater()
    {
        return android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.GINGERBREAD;
    }

    public static boolean isHoneycombOrLater()
    {
        return android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB;
    }

    public static boolean isICSOrLater()
    {
        return android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH;
    }

    public static boolean isJellyBeanOrLater()
    {
        return android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN;
    }

    private static String errorMsg = null;
    private static boolean isCompatible = false;
    public static String getErrorMsg() {
        return errorMsg;
    }

    public static boolean hasCompatibleCPU()
    {
        // If already checked return cached result
        if(errorMsg != null) return isCompatible;

        if(VLCApplication.getAppResources() == null) {
            Log.e(TAG, "WARNING: Unable to get app resources; cannot check device ABI!");
            Log.e(TAG, "WARNING: Cannot guarantee correct ABI for this build (may crash)!");
            return true;
        }

        Properties properties = new Properties();
        try {
            properties.load(new ByteArrayInputStream(Util.readAsset("env.txt", "").getBytes("UTF-8")));
        } catch (IOException e) {
            // Shouldn't happen if done correctly
            e.printStackTrace();
            errorMsg = "IOException whilst reading compile flags";
            isCompatible = false;
            return false;
        }

        String CPU_ABI = android.os.Build.CPU_ABI;
        String CPU_ABI2 = "none";
        if(android.os.Build.VERSION.SDK_INT >= 8) { // CPU_ABI2 since 2.2
            try {
                CPU_ABI2 = (String)android.os.Build.class.getDeclaredField("CPU_ABI2").get(null);
            } catch (Exception e) { }
        }

        String ANDROID_ABI = properties.getProperty("ANDROID_ABI");
        boolean NO_FPU = properties.getProperty("NO_FPU","0").equals("1");
        boolean NO_ARMV6 = properties.getProperty("NO_ARMV6","0").equals("1");
        boolean hasNeon = false, hasFpu = false, hasArmV6 = false,
                hasArmV7 = false, hasMips = false;
        boolean hasX86 = false;

        if(CPU_ABI.equals("x86")) {
            hasX86 = true;
        } else if(CPU_ABI.equals("armeabi-v7a") ||
                  CPU_ABI2.equals("armeabi-v7a")) {
            hasArmV7 = true;
            hasArmV6 = true; /* Armv7 is backwards compatible to < v6 */
        } else if(CPU_ABI.equals("armeabi") ||
                  CPU_ABI2.equals("armeabi")) {
            hasArmV6 = true;
        }

        try {
            FileReader fileReader = new FileReader("/proc/cpuinfo");
            BufferedReader br = new BufferedReader(fileReader);
            String line;
            while((line = br.readLine()) != null) {
                if(!hasArmV7 && line.contains("ARMv7")) {
                    hasArmV7 = true;
                    hasArmV6 = true; /* Armv7 is backwards compatible to < v6 */
                }
                if(!hasArmV7 && !hasArmV6 && line.contains("ARMv6"))
                    hasArmV6 = true;
                // "clflush size" is a x86-specific cpuinfo tag.
                // (see kernel sources arch/x86/kernel/cpu/proc.c)
                if(line.contains("clflush size"))
                    hasX86 = true;
                // "microsecond timers" is specific to MIPS.
                // see arch/mips/kernel/proc.c
                if(line.contains("microsecond timers"))
                    hasMips = true;
                if(!hasNeon && line.contains("neon"))
                    hasNeon = true;
                if(!hasFpu && line.contains("vfp"))
                    hasFpu = true;
            }
            fileReader.close();
        } catch(IOException ex){
            ex.printStackTrace();
            errorMsg = "IOException whilst reading cpuinfo flags";
            isCompatible = false;
            return false;
        }

        // Enforce proper architecture to prevent problems
        if(ANDROID_ABI.equals("x86") && !hasX86) {
            errorMsg = "x86 build on non-x86 device";
            isCompatible = false;
            return false;
        } else if(hasX86 && ANDROID_ABI.contains("armeabi")) {
            errorMsg = "ARM build on x86 device";
            isCompatible = false;
            return false;
        }

        if(ANDROID_ABI.equals("mips") && !hasMips) {
            errorMsg = "MIPS build on non-MIPS device";
            isCompatible = false;
            return false;
        } else if(hasMips && ANDROID_ABI.contains("armeabi")) {
            errorMsg = "ARM build on MIPS device";
            isCompatible = false;
            return false;
        }

        if(ANDROID_ABI.equals("armeabi-v7a") && !hasArmV7) {
            errorMsg = "ARMv7 build on non-ARMv7 device";
            isCompatible = false;
            return false;
        }
        if(ANDROID_ABI.equals("armeabi")) {
            if(!NO_ARMV6 && !hasArmV6) {
                errorMsg = "ARMv6 build on non-ARMv6 device";
                isCompatible = false;
                return false;
            } else if(!NO_FPU && !hasFpu) {
                errorMsg = "FPU-enabled build on non-FPU device";
                isCompatible = false;
                return false;
            }
        }
        if(ANDROID_ABI.equals("armeabi") || ANDROID_ABI.equals("armeabi-v7a")) {
            if(!hasNeon) {
                errorMsg = "NEON build on non-NEON device";
            }
        }
        errorMsg = null;
        isCompatible = true;
        return true;
    }
}
