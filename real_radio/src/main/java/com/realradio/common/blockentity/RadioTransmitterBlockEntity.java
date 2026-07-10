package com.realradio.common.blockentity;

import com.realradio.common.menu.RadioTransmitterMenu;
import com.realradio.common.registry.ModBlockEntities;
import com.realradio.common.util.ChannelKeys;
import com.realradio.common.util.ChannelPresets;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Captures voice of players within {@link #CAPTURE_RADIUS} blocks and broadcasts
 * on the configured frequency / band.
 */
public class RadioTransmitterBlockEntity extends BlockEntity implements MenuProvider {
    public static final int CAPTURE_RADIUS = 4;
    private static final long SPEAK_HOLD_MS = 350L;

    private static final int DATA_FREQUENCY_BITS = 0;
    private static final int DATA_IS_AM = 1;
    private static final int DATA_RANGE = 2;
    private static final int DATA_ACTIVE = 3;
    private static final int DATA_SPEAKING = 4;
    private static final int DATA_CHANNEL_KEY = 5;
    private static final int DATA_COUNT = 6;

    private float frequency = RadioBand.FM.defaultFrequency();
    private boolean isAM;
    private boolean active;
    private int channelKey = ChannelKeys.OPEN;
    private long lastSpeakMs;
    private final ChannelPresets presets = new ChannelPresets();
    /** Cached range (antenna scan is not free). */
    private int cachedRange = -1;
    private long cachedRangeGameTime = Long.MIN_VALUE;
    private static final int RANGE_CACHE_TICKS = 20;
    private boolean cleanedUp;

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_FREQUENCY_BITS -> Float.floatToIntBits(frequency);
                case DATA_IS_AM -> isAM ? 1 : 0;
                case DATA_RANGE -> getRange();
                case DATA_ACTIVE -> active ? 1 : 0;
                case DATA_SPEAKING -> isSpeakingNow() ? 1 : 0;
                case DATA_CHANNEL_KEY -> channelKey;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case DATA_FREQUENCY_BITS -> frequency = Float.intBitsToFloat(value);
                case DATA_IS_AM -> isAM = value != 0;
                case DATA_RANGE -> {
                }
                case DATA_ACTIVE -> active = value != 0;
                case DATA_SPEAKING -> {
                }
                case DATA_CHANNEL_KEY -> channelKey = ChannelKeys.clamp(value);
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
        // Registration is kept in sync via onLoad / onRemoved
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
        cleanupServer();
        super.setRemoved();
    }

    public void onRemoved() {
        cleanupServer();
    }

    private void cleanupServer() {
        if (cleanedUp || level == null || level.isClientSide) {
            return;
        }
        cleanedUp = true;
        RadioManager.unregisterTransmitter(this);
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
        return new AABB(getBlockPos()).inflate(CAPTURE_RADIUS);
    }

    /** Called when a player voice frame is relayed through this TX. */
    public void markSpeaking() {
        lastSpeakMs = System.currentTimeMillis();
    }

    public boolean isSpeakingNow() {
        return active && lastSpeakMs > 0 && System.currentTimeMillis() - lastSpeakMs < SPEAK_HOLD_MS;
    }

    public float getFrequency() {
        return frequency;
    }

    public void setFrequency(float frequency) {
        this.frequency = getBand().snap(frequency);
        invalidateRangeCache();
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
        invalidateRangeCache();
        setChangedAndSync();
    }

    public RadioBand getBand() {
        return RadioBand.fromAm(isAM);
    }

    /**
     * Effective broadcast range: band × frequency × night (AM) × height × copper lightning-rod mast.
     * Result is cached for {@link #RANGE_CACHE_TICKS} to avoid re-scanning the mast every call.
     */
    public int getRange() {
        long gameTime = level != null ? level.getGameTime() : 0L;
        if (cachedRange > 0 && gameTime - cachedRangeGameTime < RANGE_CACHE_TICKS) {
            return cachedRange;
        }
        int base = getBand().rangeBlocks(frequency);
        float mult = level != null
                ? RadioPropagation.fullAntennaMultiplier(level, getBlockPos())
                : RadioPropagation.antennaRangeMultiplier(getBlockPos().getY());
        if (isAM && level != null && level.isNight()) {
            mult *= RealRadioConfig.amNightMultiplier();
        }
        cachedRange = Math.max(1, Math.round(base * mult));
        cachedRangeGameTime = gameTime;
        return cachedRange;
    }

    private void invalidateRangeCache() {
        cachedRange = -1;
        cachedRangeGameTime = Long.MIN_VALUE;
    }

    /** Copper lightning rods counted as the antenna mast above this transmitter. */
    public int getAntennaRodCount() {
        if (level == null) {
            return 0;
        }
        return RadioPropagation.countAntennaRods(level, getBlockPos());
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
        setChangedAndSync();
    }

    public ChannelPresets getPresets() {
        return presets;
    }

    public void savePreset(int slot) {
        presets.save(slot, frequency, isAM);
        setChangedAndSync();
    }

    public void loadPreset(int slot) {
        if (!presets.isSet(slot)) {
            return;
        }
        this.isAM = presets.isAM(slot);
        this.frequency = getBand().snap(presets.getFrequency(slot));
        setChangedAndSync();
    }

    public ContainerData getDataAccess() {
        return dataAccess;
    }

    public int getChannelKey() {
        return channelKey;
    }

    public void setChannelKey(int channelKey) {
        this.channelKey = ChannelKeys.clamp(channelKey);
        setChangedAndSync();
    }

    public void applySettings(float frequency, boolean isAM, boolean active, int channelKey) {
        this.isAM = isAM;
        this.frequency = getBand().snap(frequency);
        this.active = active;
        this.channelKey = ChannelKeys.clamp(channelKey);
        invalidateRangeCache();
        setChangedAndSync();
    }

    /** @deprecated use {@link #applySettings(float, boolean, boolean, int)} */
    @Deprecated
    public void applySettings(float frequency, boolean isAM, boolean active) {
        applySettings(frequency, isAM, active, this.channelKey);
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
        tag.putBoolean("active", active);
        tag.putInt("channelKey", channelKey);
        presets.saveToNbt(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        isAM = tag.getBoolean("isAM");
        frequency = getBand().snap(tag.contains("frequency") ? tag.getFloat("frequency") : getBand().defaultFrequency());
        active = tag.getBoolean("active");
        channelKey = ChannelKeys.clamp(tag.contains("channelKey") ? tag.getInt("channelKey") : ChannelKeys.OPEN);
        presets.loadFromNbt(tag);
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
