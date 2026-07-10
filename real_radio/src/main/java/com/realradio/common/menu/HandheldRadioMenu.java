package com.realradio.common.menu;

import com.realradio.common.item.HandheldRadioItem;
import com.realradio.common.registry.ModMenus;
import com.realradio.common.util.RadioBand;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

public class HandheldRadioMenu extends AbstractContainerMenu {
    private final InteractionHand hand;
    private final ContainerData data;
    private final ItemStack stack;

    public static HandheldRadioMenu fromNetwork(int containerId, Inventory inventory, FriendlyByteBuf buf) {
        InteractionHand hand = buf.readEnum(InteractionHand.class);
        return new HandheldRadioMenu(containerId, inventory, hand);
    }

    public HandheldRadioMenu(int containerId, Inventory inventory, InteractionHand hand) {
        super(ModMenus.HANDHELD_RADIO.get(), containerId);
        this.hand = hand;
        this.stack = inventory.player.getItemInHand(hand);
        this.data = new SimpleContainerData(5);
        addDataSlots(data);
        syncFromStack();
    }

    private void syncFromStack() {
        if (stack.getItem() instanceof HandheldRadioItem) {
            data.set(0, Float.floatToIntBits(HandheldRadioItem.getFrequency(stack)));
            data.set(1, HandheldRadioItem.isAM(stack) ? 1 : 0);
            data.set(2, Float.floatToIntBits(HandheldRadioItem.getVolume(stack)));
            data.set(3, HandheldRadioItem.isActive(stack) ? 1 : 0);
            data.set(4, HandheldRadioItem.getChannelKey(stack));
        }
    }

    public InteractionHand getHand() {
        return hand;
    }

    public ItemStack getStack() {
        return stack;
    }

    public float getFrequency() {
        return Float.intBitsToFloat(data.get(0));
    }

    public boolean isAM() {
        return data.get(1) != 0;
    }

    public float getVolume() {
        return Float.intBitsToFloat(data.get(2));
    }

    public boolean isActive() {
        return data.get(3) != 0;
    }

    public int getChannelKey() {
        return data.get(4);
    }

    public RadioBand getBand() {
        return RadioBand.fromAm(isAM());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.getItemInHand(hand).getItem() instanceof HandheldRadioItem;
    }
}
