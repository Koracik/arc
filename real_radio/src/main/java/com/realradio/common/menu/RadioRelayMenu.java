package com.realradio.common.menu;

import com.realradio.common.blockentity.RadioRelayBlockEntity;
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

public class RadioRelayMenu extends AbstractContainerMenu {
    private final ContainerLevelAccess access;
    private final ContainerData data;
    private final BlockPos pos;

    public static RadioRelayMenu fromNetwork(int containerId, Inventory inventory, FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        return new RadioRelayMenu(containerId, inventory, pos, new SimpleContainerData(9),
                ContainerLevelAccess.create(inventory.player.level(), pos));
    }

    public RadioRelayMenu(int containerId, Inventory inventory, RadioRelayBlockEntity be,
                          ContainerData data, ContainerLevelAccess access) {
        this(containerId, inventory, be.getBlockPos(), data, access);
    }

    public RadioRelayMenu(int containerId, Inventory inventory, BlockPos pos,
                          ContainerData data, ContainerLevelAccess access) {
        super(ModMenus.RADIO_RELAY.get(), containerId);
        this.access = access;
        this.data = data;
        this.pos = pos.immutable();
        addDataSlots(data);

        if (inventory.player.level().getBlockEntity(pos) instanceof RadioRelayBlockEntity be) {
            data.set(0, Float.floatToIntBits(be.getInFrequency()));
            data.set(1, be.isInAM() ? 1 : 0);
            data.set(2, Float.floatToIntBits(be.getOutFrequency()));
            data.set(3, be.isOutAM() ? 1 : 0);
            data.set(4, be.isActive() ? 1 : 0);
            data.set(5, be.getInChannelKey());
            data.set(6, be.getOutChannelKey());
            data.set(7, be.getOutRange());
            data.set(8, be.isSpeakingNow() ? 1 : 0);
        }
    }

    public BlockPos getBlockPos() {
        return pos;
    }

    public float getInFrequency() {
        return Float.intBitsToFloat(data.get(0));
    }

    public boolean isInAM() {
        return data.get(1) != 0;
    }

    public float getOutFrequency() {
        return Float.intBitsToFloat(data.get(2));
    }

    public boolean isOutAM() {
        return data.get(3) != 0;
    }

    public boolean isActive() {
        return data.get(4) != 0;
    }

    public int getInChannelKey() {
        return data.get(5);
    }

    public int getOutChannelKey() {
        return data.get(6);
    }

    public int getOutRange() {
        return data.get(7);
    }

    public boolean isSpeaking() {
        return data.get(8) != 0;
    }

    public RadioBand getInBand() {
        return RadioBand.fromAm(isInAM());
    }

    public RadioBand getOutBand() {
        return RadioBand.fromAm(isOutAM());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.RADIO_RELAY.get());
    }
}
