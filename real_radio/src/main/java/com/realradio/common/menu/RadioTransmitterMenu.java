package com.realradio.common.menu;

import com.realradio.common.blockentity.RadioTransmitterBlockEntity;
import com.realradio.common.registry.ModBlocks;
import com.realradio.common.registry.ModMenus;
import com.realradio.common.util.RadioBand;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

public class RadioTransmitterMenu extends AbstractContainerMenu {
    private final ContainerLevelAccess access;
    private final ContainerData data;
    private final BlockPos pos;

    public static RadioTransmitterMenu fromNetwork(int containerId, Inventory inventory, FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        return new RadioTransmitterMenu(containerId, inventory, pos, new SimpleContainerData(4),
                ContainerLevelAccess.create(inventory.player.level(), pos));
    }

    public RadioTransmitterMenu(int containerId, Inventory inventory, RadioTransmitterBlockEntity be,
                                ContainerData data, ContainerLevelAccess access) {
        this(containerId, inventory, be.getBlockPos(), data, access);
    }

    public RadioTransmitterMenu(int containerId, Inventory inventory, BlockPos pos,
                                ContainerData data, ContainerLevelAccess access) {
        super(ModMenus.RADIO_TRANSMITTER.get(), containerId);
        this.access = access;
        this.data = data;
        this.pos = pos.immutable();
        addDataSlots(data);

        // If opening on client with a loaded BE, seed data from it once
        if (inventory.player.level().getBlockEntity(pos) instanceof RadioTransmitterBlockEntity be) {
            data.set(0, Float.floatToIntBits(be.getFrequency()));
            data.set(1, be.isAM() ? 1 : 0);
            data.set(2, be.getRange());
            data.set(3, be.isActive() ? 1 : 0);
        }
    }

    public BlockPos getBlockPos() {
        return pos;
    }

    public float getFrequency() {
        return Float.intBitsToFloat(data.get(0));
    }

    public boolean isAM() {
        return data.get(1) != 0;
    }

    public int getRange() {
        return data.get(2);
    }

    public boolean isActive() {
        return data.get(3) != 0;
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
        return stillValid(access, player, ModBlocks.RADIO_TRANSMITTER.get());
    }
}
