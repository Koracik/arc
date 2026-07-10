package com.realradio.common.blockentity;

import com.realradio.common.menu.RadioReceiverMenu;
import com.realradio.common.registry.ModBlockEntities;
import com.realradio.common.util.ChannelKeys;
import com.realradio.common.util.ChannelPresets;
import com.realradio.common.util.RadioBand;
import com.realradio.common.util.RadioPropagation;
import com.realradio.common.util.SignalQuality;
import com.realradio.integration.plasmovoice.RadioVoiceService;
import com.realradio.network.NearbyStationsPayload;
import com.realradio.network.ReceiverQualityPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Plays received radio audio as a Plasmo Voice 3D static source at this block.
 */
public class RadioReceiverBlockEntity extends BlockEntity implements MenuProvider {
    private static final int DATA_FREQUENCY_BITS = 0;
    private static final int DATA_IS_AM = 1;
    private static final int DATA_VOLUME_BITS = 2;
    private static final int DATA_ACTIVE = 3;
    private static final int DATA_QUALITY_BITS = 4;
    private static final int DATA_CHANNEL_KEY = 5;
    private static final int DATA_COUNT = 6;

    public static final double LISTENER_SYNC_RADIUS = 32.0;

    private float frequency = RadioBand.FM.defaultFrequency();
    private boolean isAM;
    private float volume = 0.8f;
    private boolean active;
    private float signalQuality;
    private int channelKey = ChannelKeys.OPEN;
    private boolean recording;
    private final ChannelPresets presets = new ChannelPresets();
    private int spectrumTickCounter;

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_FREQUENCY_BITS -> Float.floatToIntBits(frequency);
                case DATA_IS_AM -> isAM ? 1 : 0;
                case DATA_VOLUME_BITS -> Float.floatToIntBits(volume);
                case DATA_ACTIVE -> active ? 1 : 0;
                case DATA_QUALITY_BITS -> Float.floatToIntBits(signalQuality);
                case DATA_CHANNEL_KEY -> channelKey;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case DATA_FREQUENCY_BITS -> frequency = Float.intBitsToFloat(value);
                case DATA_IS_AM -> isAM = value != 0;
                case DATA_VOLUME_BITS -> volume = Float.intBitsToFloat(value);
                case DATA_ACTIVE -> active = value != 0;
                case DATA_QUALITY_BITS -> signalQuality = Float.intBitsToFloat(value);
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

    public RadioReceiverBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RADIO_RECEIVER.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, RadioReceiverBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        be.recalculateQuality();
        be.ensureVoiceSource();
        be.syncQualityToNearby(serverLevel);
        be.spectrumTickCounter++;
        if (be.spectrumTickCounter % 10 == 0) {
            be.syncSpectrumToNearby(serverLevel);
        }
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, RadioReceiverBlockEntity be) {
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            RadioManager.registerReceiver(this);
            if (active) {
                RadioVoiceService.createSource(this);
            }
        }
    }

    @Override
    public void setRemoved() {
        if (level != null && !level.isClientSide) {
            RadioManager.unregisterReceiver(this);
            RadioVoiceService.removeSource(this);
        }
        super.setRemoved();
    }

    public void onRemoved() {
        if (level != null && !level.isClientSide) {
            if (recording) {
                com.realradio.common.util.AirRecording.finish(this);
            }
            RadioManager.unregisterReceiver(this);
            RadioVoiceService.removeSource(this);
        }
    }

    private void ensureVoiceSource() {
        if (active) {
            RadioVoiceService.createSource(this);
        } else {
            RadioVoiceService.removeSource(this);
        }
    }

    private void recalculateQuality() {
        if (!active || level == null) {
            signalQuality = 0.0f;
            return;
        }
        signalQuality = SignalQuality.combineStationQualities(collectRawQualities(), isAM);
    }

    private List<Float> collectRawQualities() {
        ArrayList<Float> qualities = new ArrayList<>();
        if (!active || level == null) {
            return qualities;
        }
        RadioBand band = getBand();
        BlockPos self = getBlockPos();
        for (RadioTransmitterBlockEntity tx : RadioManager.transmitters()) {
            float q = rawQualityFrom(tx, band, self);
            if (q > 0.0f) {
                qualities.add(q);
            }
        }
        for (RadioRelayBlockEntity relay : RadioManager.relays()) {
            float q = rawQualityFromRelay(relay, band, self);
            if (q > 0.0f) {
                qualities.add(q);
            }
        }
        return qualities;
    }

    private float rawQualityFromRelay(RadioRelayBlockEntity relay, RadioBand band, BlockPos self) {
        if (!relay.isActive() || relay.getLevel() != level || relay.isOutAM() != isAM) {
            return 0.0f;
        }
        if (!ChannelKeys.matches(relay.getOutChannelKey(), channelKey)) {
            return 0.0f;
        }
        float tuning = SignalQuality.tuningFactor(relay.getOutFrequency(), frequency, band);
        if (tuning <= 0.0f) {
            return 0.0f;
        }
        double dist = Math.sqrt(relay.getBlockPos().distSqr(self));
        float distance = SignalQuality.distanceFactor(dist, relay.getOutRange(), band);
        if (distance <= 0.0f) {
            return 0.0f;
        }
        float los = RadioPropagation.lineOfSightFactor(level, relay.getBlockPos(), self, isAM);
        float weather = RadioPropagation.weatherFactor(level, isAM);
        float rxAntenna = RadioPropagation.antennaReceiveMultiplier(level, self);
        return SignalQuality.finalQuality(distance * los * weather * rxAntenna, tuning);
    }

    private float rawQualityFrom(RadioTransmitterBlockEntity tx, RadioBand band, BlockPos self) {
        if (!tx.isActive() || tx.getLevel() != level || tx.isAM() != isAM) {
            return 0.0f;
        }
        if (!ChannelKeys.matches(tx.getChannelKey(), channelKey)) {
            return 0.0f;
        }
        float tuning = SignalQuality.tuningFactor(tx.getFrequency(), frequency, band);
        if (tuning <= 0.0f) {
            return 0.0f;
        }
        double dist = Math.sqrt(tx.getBlockPos().distSqr(self));
        float distance = SignalQuality.distanceFactor(dist, tx.getRange(), band);
        if (distance <= 0.0f) {
            return 0.0f;
        }

        float los = RadioPropagation.lineOfSightFactor(level, tx.getBlockPos(), self, isAM);
        float weather = RadioPropagation.weatherFactor(level, isAM);
        float rxAntenna = RadioPropagation.antennaReceiveMultiplier(level, self);
        float env = los * weather * rxAntenna;
        return SignalQuality.finalQuality(distance * env, tuning);
    }

    public int getAntennaRodCount() {
        if (level == null) {
            return 0;
        }
        return RadioPropagation.countAntennaRods(level, getBlockPos());
    }

    public boolean isDominantTransmitter(RadioTransmitterBlockEntity tx) {
        if (!active || level == null || tx == null) {
            return false;
        }
        RadioBand band = getBand();
        BlockPos self = getBlockPos();
        float candidate = rawQualityFrom(tx, band, self);
        if (candidate <= 0.0f) {
            return false;
        }

        float best = 0.0f;
        RadioTransmitterBlockEntity bestTx = null;
        for (RadioTransmitterBlockEntity other : RadioManager.transmitters()) {
            float q = rawQualityFrom(other, band, self);
            if (q > best) {
                best = q;
                bestTx = other;
            }
        }
        return bestTx == tx;
    }

    public float rawQualityFrom(RadioTransmitterBlockEntity tx) {
        if (level == null) {
            return 0.0f;
        }
        return rawQualityFrom(tx, getBand(), getBlockPos());
    }

    private void syncQualityToNearby(ServerLevel level) {
        AABB box = new AABB(getBlockPos()).inflate(LISTENER_SYNC_RADIUS);
        float voiceVol = SignalQuality.voiceVolume(signalQuality, volume);
        float staticVol = active ? SignalQuality.staticVolume(signalQuality, volume) : 0.0f;

        ReceiverQualityPayload payload = new ReceiverQualityPayload(
                getBlockPos(), signalQuality, voiceVol, staticVol, active
        );

        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, box)) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    private void syncSpectrumToNearby(ServerLevel level) {
        // Realism mode: never reveal nearby stations to clients
        if (com.realradio.config.RealRadioConfig.realismMode()) {
            NearbyStationsPayload empty = new NearbyStationsPayload(getBlockPos(), List.of());
            AABB box = new AABB(getBlockPos()).inflate(LISTENER_SYNC_RADIUS);
            for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, box)) {
                PacketDistributor.sendToPlayer(player, empty);
            }
            return;
        }
        if (!active) {
            return;
        }
        List<NearbyStationsPayload.StationMarker> markers = buildSpectrumMarkers();
        NearbyStationsPayload payload = new NearbyStationsPayload(getBlockPos(), markers);
        AABB box = new AABB(getBlockPos()).inflate(LISTENER_SYNC_RADIUS);
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, box)) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    /**
     * All active TX on the same band within theoretical max range, for dial ticks.
     * Strength is normalized raw quality as if RX were tuned exactly to that TX.
     */
    private List<NearbyStationsPayload.StationMarker> buildSpectrumMarkers() {
        List<NearbyStationsPayload.StationMarker> out = new ArrayList<>();
        if (level == null) {
            return out;
        }
        RadioBand band = getBand();
        BlockPos self = getBlockPos();
        for (RadioTransmitterBlockEntity tx : RadioManager.transmitters()) {
            if (!tx.isActive() || tx.getLevel() != level || tx.isAM() != isAM) {
                continue;
            }
            if (!ChannelKeys.matches(tx.getChannelKey(), channelKey)) {
                continue;
            }
            // Perfect tuning hypothetical for spectrum peak height
            float perfectTuning = 1.0f;
            double dist = Math.sqrt(tx.getBlockPos().distSqr(self));
            float distance = SignalQuality.distanceFactor(dist, tx.getRange(), band);
            if (distance <= 0.0f) {
                continue;
            }
            float los = RadioPropagation.lineOfSightFactor(level, tx.getBlockPos(), self, isAM);
            float weather = RadioPropagation.weatherFactor(level, isAM);
            float strength = SignalQuality.finalQuality(distance * los * weather, perfectTuning);
            if (strength > 0.02f) {
                out.add(new NearbyStationsPayload.StationMarker(tx.getFrequency(), strength));
            }
        }
        out.sort(Comparator.comparingDouble(NearbyStationsPayload.StationMarker::strength).reversed());
        if (out.size() > NearbyStationsPayload.MAX_STATIONS) {
            return new ArrayList<>(out.subList(0, NearbyStationsPayload.MAX_STATIONS));
        }
        return out;
    }

    public float getFrequency() {
        return frequency;
    }

    public void setFrequency(float frequency) {
        this.frequency = getBand().snap(frequency);
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

    public float getVolume() {
        return volume;
    }

    public void setVolume(float volume) {
        this.volume = Math.max(0.0f, Math.min(1.0f, volume));
        setChangedAndSync();
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
        ensureVoiceSource();
        setChangedAndSync();
    }

    public float getSignalQuality() {
        return signalQuality;
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

    public boolean isRecording() {
        return recording;
    }

    public void setRecording(boolean recording) {
        this.recording = recording && active;
        setChangedAndSync();
    }

    public void applySettings(float frequency, boolean isAM, float volume, boolean active, int channelKey) {
        this.isAM = isAM;
        this.frequency = getBand().snap(frequency);
        this.volume = Math.max(0.0f, Math.min(1.0f, volume));
        this.active = active;
        this.channelKey = ChannelKeys.clamp(channelKey);
        if (!active) {
            this.recording = false;
        }
        ensureVoiceSource();
        setChangedAndSync();
    }

    public void applySettings(float frequency, boolean isAM, float volume, boolean active) {
        applySettings(frequency, isAM, volume, active, this.channelKey);
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
        tag.putFloat("volume", volume);
        tag.putBoolean("active", active);
        tag.putInt("channelKey", channelKey);
        presets.saveToNbt(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        isAM = tag.getBoolean("isAM");
        frequency = getBand().snap(tag.contains("frequency") ? tag.getFloat("frequency") : getBand().defaultFrequency());
        volume = tag.contains("volume") ? tag.getFloat("volume") : 0.8f;
        volume = Math.max(0.0f, Math.min(1.0f, volume));
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
        return Component.translatable("block.real_radio.radio_receiver");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new RadioReceiverMenu(containerId, playerInventory, this, dataAccess,
                ContainerLevelAccess.create(level, worldPosition));
    }
}
