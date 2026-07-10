package com.realradio.common.menu;

import com.realradio.common.blockentity.RadioReceiverBlockEntity;
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

public class RadioReceiverMenu extends AbstractContainerMenu {
    private final ContainerLevelAccess access;
    private final ContainerData data;
    private final BlockPos pos;

    public static RadioReceiverMenu fromNetwork(int containerId, Inventory inventory, FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        return new RadioReceiverMenu(containerId, inventory, pos, new SimpleContainerData(6),
                ContainerLevelAccess.create(inventory.player.level(), pos));
    }

    public RadioReceiverMenu(int containerId, Inventory inventory, RadioReceiverBlockEntity be,
                             ContainerData data, ContainerLevelAccess access) {
        this(containerId, inventory, be.getBlockPos(), data, access);
    }

    public RadioReceiverMenu(int containerId, Inventory inventory, BlockPos pos,
                             ContainerData data, ContainerLevelAccess access) {
        super(ModMenus.RADIO_RECEIVER.get(), containerId);
        this.access = access;
        this.data = data;
        this.pos = pos.immutable();
        addDataSlots(data);

        if (inventory.player.level().getBlockEntity(pos) instanceof RadioReceiverBlockEntity be) {
            data.set(0, Float.floatToIntBits(be.getFrequency()));
            data.set(1, be.isAM() ? 1 : 0);
            data.set(2, Float.floatToIntBits(be.getVolume()));
            data.set(3, be.isActive() ? 1 : 0);
            data.set(4, Float.floatToIntBits(be.getSignalQuality()));
            data.set(5, be.getChannelKey());
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

    public float getVolume() {
        return Float.intBitsToFloat(data.get(2));
    }

    public boolean isActive() {
        return data.get(3) != 0;
    }

    public float getSignalQuality() {
        return Float.intBitsToFloat(data.get(4));
    }

    public int getChannelKey() {
        return data.get(5);
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
        return stillValid(access, player, ModBlocks.RADIO_RECEIVER.get());
    }
}
