package com.realradio.common.item;

import com.realradio.common.menu.HandheldRadioMenu;
import com.realradio.common.util.ChannelKeys;
import com.realradio.common.util.RadioBand;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class HandheldRadioItem extends Item {
    private static final String TAG_FREQ = "Frequency";
    private static final String TAG_AM = "IsAM";
    private static final String TAG_VOLUME = "Volume";
    private static final String TAG_ACTIVE = "Active";
    private static final String TAG_KEY = "ChannelKey";

    public HandheldRadioItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static float getFrequency(ItemStack stack) {
        CompoundTag tag = ItemNbt.get(stack);
        boolean am = tag.getBoolean(TAG_AM);
        RadioBand band = RadioBand.fromAm(am);
        if (!tag.contains(TAG_FREQ)) {
            return band.defaultFrequency();
        }
        return band.snap(tag.getFloat(TAG_FREQ));
    }

    public static boolean isAM(ItemStack stack) {
        return ItemNbt.get(stack).getBoolean(TAG_AM);
    }

    public static float getVolume(ItemStack stack) {
        CompoundTag tag = ItemNbt.get(stack);
        if (!tag.contains(TAG_VOLUME)) {
            return 0.8f;
        }
        return Math.max(0.0f, Math.min(1.0f, tag.getFloat(TAG_VOLUME)));
    }

    public static boolean isActive(ItemStack stack) {
        return ItemNbt.get(stack).getBoolean(TAG_ACTIVE);
    }

    public static int getChannelKey(ItemStack stack) {
        CompoundTag tag = ItemNbt.get(stack);
        if (!tag.contains(TAG_KEY)) {
            return ChannelKeys.OPEN;
        }
        return ChannelKeys.clamp(tag.getInt(TAG_KEY));
    }

    public static void applySettings(ItemStack stack, float frequency, boolean isAM, float volume,
                                     boolean active, int channelKey) {
        ItemNbt.put(stack, tag -> {
            tag.putBoolean(TAG_AM, isAM);
            tag.putFloat(TAG_FREQ, RadioBand.fromAm(isAM).snap(frequency));
            tag.putFloat(TAG_VOLUME, Math.max(0.0f, Math.min(1.0f, volume)));
            tag.putBoolean(TAG_ACTIVE, active);
            tag.putInt(TAG_KEY, ChannelKeys.clamp(channelKey));
        });
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            final InteractionHand usedHand = hand;
            serverPlayer.openMenu(new MenuProvider() {
                @Override
                public Component getDisplayName() {
                    return Component.translatable("item.real_radio.handheld_radio");
                }

                @Nullable
                @Override
                public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                    return new HandheldRadioMenu(id, inv, usedHand);
                }
            }, buf -> buf.writeEnum(usedHand));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        RadioBand band = RadioBand.fromAm(isAM(stack));
        tooltip.add(Component.literal(band.format(getFrequency(stack)) + (isActive(stack) ? " ON" : " OFF")));
        tooltip.add(Component.translatable("gui.real_radio.channel_key", ChannelKeys.format(getChannelKey(stack))));
    }
}
