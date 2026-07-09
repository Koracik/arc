package com.realradio.common.blockentity;

import com.realradio.common.menu.RadioTransmitterMenu;
import com.realradio.common.registry.ModBlockEntities;
import com.realradio.common.util.RadioBand;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Captures voice of players within {@link #CAPTURE_RADIUS} blocks and broadcasts
 * on the configured frequency / band.
 */
public class RadioTransmitterBlockEntity extends BlockEntity implements MenuProvider {
    public static final int CAPTURE_RADIUS = 4;
    public static final int MIN_RANGE = 50;
    public static final int MAX_RANGE = 500;
    public static final int DEFAULT_RANGE = 150;

    private static final int DATA_FREQUENCY_BITS = 0;
    private static final int DATA_IS_AM = 1;
    private static final int DATA_RANGE = 2;
    private static final int DATA_ACTIVE = 3;
    private static final int DATA_COUNT = 4;

    private float frequency = RadioBand.FM.defaultFrequency();
    private boolean isAM;
    private int range = DEFAULT_RANGE;
    private boolean active;

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_FREQUENCY_BITS -> Float.floatToIntBits(frequency);
                case DATA_IS_AM -> isAM ? 1 : 0;
                case DATA_RANGE -> range;
                case DATA_ACTIVE -> active ? 1 : 0;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case DATA_FREQUENCY_BITS -> frequency = Float.intBitsToFloat(value);
                case DATA_IS_AM -> isAM = value != 0;
                case DATA_RANGE -> range = value;
                case DATA_ACTIVE -> active = value != 0;
                default -> {
                }
            }
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public RadioTransmitterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RADIO_TRANSMITTER.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, RadioTransmitterBlockEntity be) {
        // Registration is kept in sync via setLevel / onLoad / onRemoved
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            RadioManager.registerTransmitter(this);
        }
    }

    @Override
    public void setRemoved() {
        if (level != null && !level.isClientSide) {
            RadioManager.unregisterTransmitter(this);
        }
        super.setRemoved();
    }

    public void onRemoved() {
        if (level != null && !level.isClientSide) {
            RadioManager.unregisterTransmitter(this);
        }
    }

    public boolean isPlayerInCaptureZone(Vec3 playerPos) {
        if (!active || level == null) {
            return false;
        }
        BlockPos pos = getBlockPos();
        double dx = playerPos.x - (pos.getX() + 0.5);
        double dy = playerPos.y - (pos.getY() + 0.5);
        double dz = playerPos.z - (pos.getZ() + 0.5);
        return dx * dx + dy * dy + dz * dz <= (double) CAPTURE_RADIUS * CAPTURE_RADIUS;
    }

    public AABB captureBox() {
        BlockPos pos = getBlockPos();
        return new AABB(pos).inflate(CAPTURE_RADIUS);
    }

    public float getFrequency() {
        return frequency;
    }

    public void setFrequency(float frequency) {
        RadioBand band = getBand();
        this.frequency = band.snap(frequency);
        setChangedAndSync();
    }

    public boolean isAM() {
        return isAM;
    }

    public void setAM(boolean am) {
        if (this.isAM == am) {
            return;
        }
        this.isAM = am;
        this.frequency = getBand().defaultFrequency();
        setChangedAndSync();
    }

    public RadioBand getBand() {
        return RadioBand.fromAm(isAM);
    }

    public int getRange() {
        return range;
    }

    public void setRange(int range) {
        this.range = Math.max(MIN_RANGE, Math.min(MAX_RANGE, range));
        setChangedAndSync();
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
        setChangedAndSync();
    }

    public ContainerData getDataAccess() {
        return dataAccess;
    }

    public void applySettings(float frequency, boolean isAM, int range, boolean active) {
        this.isAM = isAM;
        this.frequency = getBand().snap(frequency);
        this.range = Math.max(MIN_RANGE, Math.min(MAX_RANGE, range));
        this.active = active;
        setChangedAndSync();
    }

    private void setChangedAndSync() {
        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            BlockState state = getBlockState();
            serverLevel.sendBlockUpdated(worldPosition, state, state, 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putFloat("frequency", frequency);
        tag.putBoolean("isAM", isAM);
        tag.putInt("range", range);
        tag.putBoolean("active", active);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        isAM = tag.getBoolean("isAM");
        frequency = getBand().snap(tag.contains("frequency") ? tag.getFloat("frequency") : getBand().defaultFrequency());
        range = tag.contains("range") ? tag.getInt("range") : DEFAULT_RANGE;
        range = Math.max(MIN_RANGE, Math.min(MAX_RANGE, range));
        active = tag.getBoolean("active");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.real_radio.radio_transmitter");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new RadioTransmitterMenu(containerId, playerInventory, this, dataAccess,
                ContainerLevelAccess.create(level, worldPosition));
    }
}
