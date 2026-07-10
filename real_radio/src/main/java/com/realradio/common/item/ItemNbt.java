package com.realradio.common.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** 1.21.1-safe helpers for stack custom NBT. */
public final class ItemNbt {
    private ItemNbt() {
    }

    public static CompoundTag get(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null ? data.copyTag() : new CompoundTag();
    }

    public static void set(ItemStack stack, CompoundTag tag) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static CompoundTag getOrCreate(ItemStack stack) {
        CompoundTag tag = get(stack);
        if (tag.isEmpty() && !stack.has(DataComponents.CUSTOM_DATA)) {
            // return mutable empty that caller will write then set()
            return new CompoundTag();
        }
        return tag;
    }

    public static void put(ItemStack stack, java.util.function.Consumer<CompoundTag> editor) {
        CompoundTag tag = get(stack);
        editor.accept(tag);
        set(stack, tag);
    }
}
