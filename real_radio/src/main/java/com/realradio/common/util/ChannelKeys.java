package com.realradio.common.util;

import com.realradio.config.RealRadioConfig;

/**
 * Shared channel key for closed links. Key {@code 0} is the open (unencrypted) channel.
 */
public final class ChannelKeys {
    public static final int MIN = 0;
    public static final int MAX = 9999;
    public static final int OPEN = 0;

    private ChannelKeys() {
    }

    public static int clamp(int key) {
        return Math.max(MIN, Math.min(MAX, key));
    }

    /**
     * @return true if voice path is allowed between these keys
     */
    public static boolean matches(int txKey, int rxKey) {
        if (!RealRadioConfig.requireMatchingKey()) {
            return true;
        }
        return clamp(txKey) == clamp(rxKey);
    }

    public static String format(int key) {
        int k = clamp(key);
        if (k == OPEN) {
            return "OPEN";
        }
        return String.format("%04d", k);
    }
}
