package com.realradio.common.blockentity;

import com.realradio.common.menu.RadioRelayMenu;
import com.realradio.common.registry.ModBlockEntities;
import com.realradio.common.util.ChannelKeys;
import com.realradio.common.util.RadioBand;
import com.realradio.common.util.RadioPropagation;
import com.realradio.config.RealRadioConfig;
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
import org.jetbrains.annotations.Nullable;

/**
 * Receives on in-frequency/band/key and retransmits on out-frequency/band/key.
 */
public class RadioRelayBlockEntity extends BlockEntity implements MenuProvider {
    private static final long SPEAK_HOLD_MS = 350L;

    private static final int DATA_IN_FREQ = 0;
    private static final int DATA_IN_AM = 1;
    private static final int DATA_OUT_FREQ = 2;
    private static final int DATA_OUT_AM = 3;
    private static final int DATA_ACTIVE = 4;
    private static final int DATA_IN_KEY = 5;
    private static final int DATA_OUT_KEY = 6;
    private static final int DATA_OUT_RANGE = 7;
    private static final int DATA_SPEAKING = 8;
    private static final int DATA_COUNT = 9;

    private float inFrequency = RadioBand.FM.defaultFrequency();
    private boolean inAM;
    private float outFrequency = RadioBand.FM.defaultFrequency();
    private boolean outAM;
    private boolean active;
    private int inChannelKey = ChannelKeys.OPEN;
    private int outChannelKey = ChannelKeys.OPEN;
    private long lastSpeakMs;

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_IN_FREQ -> Float.floatToIntBits(inFrequency);
                case DATA_IN_AM -> inAM ? 1 : 0;
                case DATA_OUT_FREQ -> Float.floatToIntBits(outFrequency);
                case DATA_OUT_AM -> outAM ? 1 : 0;
                case DATA_ACTIVE -> active ? 1 : 0;
                case DATA_IN_KEY -> inChannelKey;
                case DATA_OUT_KEY -> outChannelKey;
                case DATA_OUT_RANGE -> getOutRange();
                case DATA_SPEAKING -> isSpeakingNow() ? 1 : 0;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case DATA_IN_FREQ -> inFrequency = Float.intBitsToFloat(value);
                case DATA_IN_AM -> inAM = value != 0;
                case DATA_OUT_FREQ -> outFrequency = Float.intBitsToFloat(value);
                case DATA_OUT_AM -> outAM = value != 0;
                case DATA_ACTIVE -> active = value != 0;
                case DATA_IN_KEY -> inChannelKey = ChannelKeys.clamp(value);
                case DATA_OUT_KEY -> outChannelKey = ChannelKeys.clamp(value);
                default -> {
                }
            }
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public RadioRelayBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RADIO_RELAY.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, RadioRelayBlockEntity be) {
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            RadioManager.registerRelay(this);
        }
    }

    @Override
    public void setRemoved() {
        if (level != null && !level.isClientSide) {
            RadioManager.unregisterRelay(this);
        }
        super.setRemoved();
    }

    public void onRemoved() {
        if (level != null && !level.isClientSide) {
            RadioManager.unregisterRelay(this);
        }
    }

    public void markSpeaking() {
        lastSpeakMs = System.currentTimeMillis();
    }

    public boolean isSpeakingNow() {
        return active && lastSpeakMs > 0 && System.currentTimeMillis() - lastSpeakMs < SPEAK_HOLD_MS;
    }

    public float getInFrequency() {
        return inFrequency;
    }

    public boolean isInAM() {
        return inAM;
    }

    public RadioBand getInBand() {
        return RadioBand.fromAm(inAM);
    }

    public float getOutFrequency() {
        return outFrequency;
    }

    public boolean isOutAM() {
        return outAM;
    }

    public RadioBand getOutBand() {
        return RadioBand.fromAm(outAM);
    }

    public int getInChannelKey() {
        return inChannelKey;
    }

    public int getOutChannelKey() {
        return outChannelKey;
    }

    public boolean isActive() {
        return active;
    }

    public int getOutRange() {
        int base = getOutBand().rangeBlocks(outFrequency);
        float mult = RadioPropagation.antennaRangeMultiplier(getBlockPos().getY());
        if (outAM && level != null && level.isNight()) {
            mult *= RealRadioConfig.amNightMultiplier();
        }
        return Math.max(1, Math.round(base * mult));
    }

    public ContainerData getDataAccess() {
        return dataAccess;
    }

    public void applySettings(float inFrequency, boolean inAM, float outFrequency, boolean outAM,
                              boolean active, int inKey, int outKey) {
        this.inAM = inAM;
        this.inFrequency = getInBand().snap(inFrequency);
        this.outAM = outAM;
        this.outFrequency = getOutBand().snap(outFrequency);
        this.active = active;
        this.inChannelKey = ChannelKeys.clamp(inKey);
        this.outChannelKey = ChannelKeys.clamp(outKey);
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
        tag.putFloat("inFrequency", inFrequency);
        tag.putBoolean("inAM", inAM);
        tag.putFloat("outFrequency", outFrequency);
        tag.putBoolean("outAM", outAM);
        tag.putBoolean("active", active);
        tag.putInt("inChannelKey", inChannelKey);
        tag.putInt("outChannelKey", outChannelKey);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        inAM = tag.getBoolean("inAM");
        inFrequency = getInBand().snap(tag.contains("inFrequency") ? tag.getFloat("inFrequency") : getInBand().defaultFrequency());
        outAM = tag.getBoolean("outAM");
        outFrequency = getOutBand().snap(tag.contains("outFrequency") ? tag.getFloat("outFrequency") : getOutBand().defaultFrequency());
        active = tag.getBoolean("active");
        inChannelKey = ChannelKeys.clamp(tag.contains("inChannelKey") ? tag.getInt("inChannelKey") : ChannelKeys.OPEN);
        outChannelKey = ChannelKeys.clamp(tag.contains("outChannelKey") ? tag.getInt("outChannelKey") : ChannelKeys.OPEN);
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
        return Component.translatable("block.real_radio.radio_relay");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new RadioRelayMenu(containerId, playerInventory, this, dataAccess,
                ContainerLevelAccess.create(level, worldPosition));
    }
}
