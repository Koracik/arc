package com.realradio.common.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * Three memory slots for frequency + band (AM/FM).
 */
public final class ChannelPresets {
    public static final int COUNT = 3;

    private final float[] frequency = new float[COUNT];
    private final boolean[] am = new boolean[COUNT];
    private final boolean[] set = new boolean[COUNT];

    public ChannelPresets() {
        for (int i = 0; i < COUNT; i++) {
            frequency[i] = RadioBand.FM.defaultFrequency();
            am[i] = false;
            set[i] = false;
        }
    }

    public boolean isSet(int slot) {
        return slot >= 0 && slot < COUNT && set[slot];
    }

    public float getFrequency(int slot) {
        return isSet(slot) ? frequency[slot] : RadioBand.FM.defaultFrequency();
    }

    public boolean isAM(int slot) {
        return isSet(slot) && am[slot];
    }

    public void save(int slot, float freq, boolean isAM) {
        if (slot < 0 || slot >= COUNT) {
            return;
        }
        RadioBand band = RadioBand.fromAm(isAM);
        this.am[slot] = isAM;
        this.frequency[slot] = band.snap(freq);
        this.set[slot] = true;
    }

    public void clear(int slot) {
        if (slot < 0 || slot >= COUNT) {
            return;
        }
        set[slot] = false;
    }

    public void saveToNbt(CompoundTag tag) {
        ListTag list = new ListTag();
        for (int i = 0; i < COUNT; i++) {
            CompoundTag e = new CompoundTag();
            e.putBoolean("set", set[i]);
            e.putFloat("frequency", frequency[i]);
            e.putBoolean("isAM", am[i]);
            list.add(e);
        }
        tag.put("presets", list);
    }

    public void loadFromNbt(CompoundTag tag) {
        if (!tag.contains("presets", Tag.TAG_LIST)) {
            return;
        }
        ListTag list = tag.getList("presets", Tag.TAG_COMPOUND);
        for (int i = 0; i < COUNT && i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);
            set[i] = e.getBoolean("set");
            am[i] = e.getBoolean("isAM");
            RadioBand band = RadioBand.fromAm(am[i]);
            frequency[i] = band.snap(e.contains("frequency") ? e.getFloat("frequency") : band.defaultFrequency());
        }
    }
}
