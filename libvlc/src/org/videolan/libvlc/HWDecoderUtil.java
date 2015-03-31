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
        public final String value;
        public final Decoder dec;
        public DecoderBySOC(String key, String value, Decoder dec) {
            this.key = key;
            this.value = value;
            this.dec = dec;
        }
    }

    private static final DecoderBySOC[] sDecoderBySOCList = new DecoderBySOC[] {
        /*
         *  Put first devices you want to blacklist
         *  because theses devices can match the next rules.
         */
        new DecoderBySOC("ro.product.brand", "SEMC", Decoder.NONE), // Xperia S
        new DecoderBySOC("ro.board.platform", "msm7627", Decoder.NONE), // QCOM S1

        /*
         * Devices working on OMX
         */
        new DecoderBySOC("ro.board.platform", "omap3", Decoder.OMX), // Omap 3
        new DecoderBySOC("ro.board.platform", "rockchip", Decoder.OMX), // Rockchip RK29
        new DecoderBySOC("ro.board.platform", "rk29", Decoder.OMX), // Rockchip RK29
        new DecoderBySOC("ro.board.platform", "msm7630", Decoder.OMX), // QCOM S2
        new DecoderBySOC("ro.board.platform", "s5pc", Decoder.OMX), // Exynos 3
        new DecoderBySOC("ro.board.platform",  "montblanc", Decoder.OMX), // Montblanc
        new DecoderBySOC("ro.board.platform", "exdroid", Decoder.OMX), // Allwinner A31
        new DecoderBySOC("ro.board.platform", "sun6i", Decoder.OMX), // Allwinner A31

        /*
         * Devices working on Mediacodec and OMX
         */
        new DecoderBySOC("ro.board.platform", "omap4", Decoder.ALL), // Omap 4
        new DecoderBySOC("ro.board.platform", "tegra", Decoder.ALL), // Tegra 2 & 3
        new DecoderBySOC("ro.board.platform", "tegra3", Decoder.ALL), // Tegra 3
        new DecoderBySOC("ro.board.platform", "msm8660", Decoder.ALL), // QCOM S3
        new DecoderBySOC("ro.board.platform", "exynos4", Decoder.ALL), // Exynos 4 (Samsung Galaxy S2/S3)
        new DecoderBySOC("ro.board.platform", "exynos5", Decoder.ALL), // Exynos 5 (Samsung Galaxy S4)
        new DecoderBySOC("ro.board.platform", "rk30", Decoder.ALL), // Rockchip RK30
        new DecoderBySOC("ro.board.platform", "rk31", Decoder.ALL), // Rockchip RK31
        new DecoderBySOC("ro.board.platform", "mv88de3100", Decoder.ALL), // Marvell ARMADA 1500

        new DecoderBySOC("ro.hardware", "mt65", Decoder.ALL), //MTK
        new DecoderBySOC("ro.hardware", "mt83", Decoder.ALL), //MTK
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
                    if (prop.contains(decBySOC.value))
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
