/*****************************************************************************
 * HWDecUtil.java
 *****************************************************************************
 * Copyright © 2010-2013 VLC authors and VideoLAN
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *****************************************************************************/

package org.videolan.libvlc;

import java.lang.reflect.Method;
import java.util.HashMap;

/**
 * Utility class that return the preferred hardware decoder from a list of known devices.
 */
public class HWDecoderUtil {

    public enum Decoder {
        UNKNOWN, NONE, OMX, MEDIACODEC, ALL
    }

    private static class DecoderBySOC {
        public final String key;
        public final Decoder dec;
        public final String[] list;
        public DecoderBySOC(String key, Decoder dec, String[] list) {
            this.key = key;
            this.dec = dec;
            this.list = list;
        }
    }

    private static final DecoderBySOC[] sDecoderBySOCList = new DecoderBySOC[] {
        /*
         *  Put first devices you want to blacklist
         *  because theses devices can match the next rules.
         */
        new DecoderBySOC ("ro.product.brand", Decoder.NONE, new  String[] {
                "SEMC",             // Xperia S
        }),
        new DecoderBySOC ("ro.board.platform", Decoder.NONE, new  String[] {
                "msm7627",          // QCOM S1
        }),
        /*
         * Devices working on OMX
         */
        new DecoderBySOC ("ro.board.platform", Decoder.OMX, new  String[] {
                "omap3",            // Omap 3
                "rockchip", "rk29", // Rockchip RK29
                "msm7630",          // QCOM S2
                "s5pc",             // Exynos 3
                "montblanc",        // Montblanc
                "exdroid",          // Allwinner A31
        }),
        new DecoderBySOC ("ro.hardware", Decoder.OMX, new  String[] {
                "sun6i",            // Allwinner A31
        }),
        /*
         * Devices working on Mediacodec and OMX
         */
        new DecoderBySOC ("ro.board.platform", Decoder.ALL, new  String[] {
                "omap4",            // Omap 4
                "tegra",            // Tegra 2 & 3
                "tegra3",           // Tegra 3
                "msm8660",          // QCOM S3
                "exynos4",          // Exynos 4 (Samsung Galaxy S2/S3)
                "exynos5",          // Exynos 5 (Samsung Galaxy S4)
                "rk30", "rk31",     // Rockchip RK3*
                "mv88de3100",       // Marvell ARMADA 1500
        }),
        new DecoderBySOC ("ro.hardware", Decoder.ALL, new  String[] {
                "mt65", "mt83",     // MTK
        }),
    };

    private static final HashMap<String, String> sSystemPropertyMap = new HashMap<String, String>();

    /**
     * @return the hardware decoder known to work for the running device
     * (Always return Dec.ALL after Android 4.3)
     */
    public static Decoder getDecoderFromDevice() {
        /*
         * Always try MediaCodec after JellyBean MR2,
         * Try OMX or MediaCodec after HoneyComb depending on device properties.
         * Otherwise, use software decoder by default.
         */
        if (LibVlcUtil.isJellyBeanMR2OrLater())
            return Decoder.ALL;
        else if (LibVlcUtil.isHoneycombOrLater()) {
            for (DecoderBySOC decBySOC : sDecoderBySOCList) {
                String prop = sSystemPropertyMap.get(decBySOC.key);
                if (prop == null) {
                    prop = getSystemProperty(decBySOC.key, "none");
                    sSystemPropertyMap.put(decBySOC.key, prop);
                }
                if (prop != null) {
                    for (String decProp: decBySOC.list)
                        if (prop.contains(decProp))
                            return decBySOC.dec;
                }
            }
        }
        return Decoder.UNKNOWN;
    }

    private static String getSystemProperty(String key, String def) {
        try {
            final ClassLoader cl = ClassLoader.getSystemClassLoader();
            final Class<?> SystemProperties = cl.loadClass("android.os.SystemProperties");
            final Class<?>[] paramTypes = new Class[] { String.class, String.class };
            final Method get = SystemProperties.getMethod("get", paramTypes);
            final Object[] params = new Object[] { key, def };
            return (String) get.invoke(SystemProperties, params);
        } catch (Exception e){
            return def;
        }
    }
}
