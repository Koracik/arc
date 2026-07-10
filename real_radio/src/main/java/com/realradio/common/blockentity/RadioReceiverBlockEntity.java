package com.realradio.common.blockentity;

import com.realradio.common.menu.RadioReceiverMenu;
import com.realradio.common.registry.ModBlockEntities;
import com.realradio.common.util.ChannelKeys;
import com.realradio.common.util.ChannelPresets;
import com.realradio.common.util.RadioBand;
import com.realradio.common.util.RadioPropagation;
import com.realradio.common.util.SignalQuality;
import com.realradio.config.RealRadioConfig;
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
 * <p>
 * Quality / spectrum / network sync are throttled — heavy LOS work is NOT done
 * on every voice frame (see {@link #acceptsFrom(BlockPos)}).
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
    /** Full quality recompute interval (ticks). */
    private static final int QUALITY_INTERVAL = 5;
    /** Client quality packet interval (ticks). */
    private static final int QUALITY_SYNC_INTERVAL = 5;
    /** Spectrum packet interval (ticks). */
    private static final int SPECTRUM_INTERVAL = 20;

    private float frequency = RadioBand.FM.defaultFrequency();
    private boolean isAM;
    private float volume = 0.8f;
    private boolean active;
    private float signalQuality;
    private int channelKey = ChannelKeys.OPEN;
    private boolean recording;
    private final ChannelPresets presets = new ChannelPresets();
    private int tickCounter;
    private boolean voiceSourceReady;
    private boolean cleanedUp;

    /** Best TX/relay position from last quality pass — used by voice path without re-raycasting. */
    @Nullable
    private BlockPos dominantSourcePos;
    private float lastSyncedQuality = Float.NaN;
    private float lastSyncedVoice;
    private float lastSyncedStatic;
    private boolean lastSyncedActive;

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
        be.tickCounter++;

        if (!be.active) {
            if (be.signalQuality != 0.0f || be.dominantSourcePos != null) {
                be.signalQuality = 0.0f;
                be.dominantSourcePos = null;
            }
            if (be.voiceSourceReady) {
                RadioVoiceService.removeSource(be);
                be.voiceSourceReady = false;
            }
            // Rare offline sync so clients clear static
            if (be.tickCounter % QUALITY_SYNC_INTERVAL == 0) {
                be.syncQualityToNearby(serverLevel, false);
            }
            return;
        }

        if (be.tickCounter % QUALITY_INTERVAL == 0) {
            be.recalculateQuality();
        }

        if (!be.voiceSourceReady) {
            be.voiceSourceReady = RadioVoiceService.createSource(be) != null;
        }

        if (be.tickCounter % QUALITY_SYNC_INTERVAL == 0) {
            be.syncQualityToNearby(serverLevel, true);
        }

        if (be.tickCounter % SPECTRUM_INTERVAL == 0) {
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
            cleanedUp = false;
            if (active) {
                voiceSourceReady = RadioVoiceService.createSource(this) != null;
            }
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
        if (recording) {
            com.realradio.common.util.AirRecording.finish(this);
        }
        RadioManager.unregisterReceiver(this);
        RadioVoiceService.removeSource(this);
        voiceSourceReady = false;
    }

    /**
     * Voice-path gate: true if this source won the last quality pass (no LOS recompute).
     */
    public boolean acceptsFrom(BlockPos sourcePos) {
        if (!active || sourcePos == null || dominantSourcePos == null) {
            return false;
        }
        if (SignalQuality.isSquelched(signalQuality)) {
            return false;
        }
        return dominantSourcePos.equals(sourcePos);
    }

    public boolean isVoiceOpen() {
        return active && signalQuality > 0.0f && !SignalQuality.isSquelched(signalQuality);
    }

    private void recalculateQuality() {
        if (!active || level == null) {
            signalQuality = 0.0f;
            dominantSourcePos = null;
            return;
        }

        float best = 0.0f;
        BlockPos bestPos = null;
        ArrayList<Float> qualities = new ArrayList<>(8);
        RadioBand band = getBand();
        BlockPos self = getBlockPos();
        // Antenna mast scanned once per quality pass
        float rxAntenna = RadioPropagation.antennaReceiveMultiplier(level, self);
        float weather = RadioPropagation.weatherFactor(level, isAM);

        for (RadioTransmitterBlockEntity tx : RadioManager.transmitters()) {
            float q = rawQualityFrom(tx, band, self, rxAntenna, weather);
            if (q > 0.0f) {
                qualities.add(q);
                if (q > best) {
                    best = q;
                    bestPos = tx.getBlockPos().immutable();
                }
            }
        }
        for (RadioRelayBlockEntity relay : RadioManager.relays()) {
            float q = rawQualityFromRelay(relay, band, self, rxAntenna, weather);
            if (q > 0.0f) {
                qualities.add(q);
                if (q > best) {
                    best = q;
                    bestPos = relay.getBlockPos().immutable();
                }
            }
        }

        signalQuality = SignalQuality.combineStationQualities(qualities, isAM);
        dominantSourcePos = bestPos;
    }

    private float rawQualityFromRelay(RadioRelayBlockEntity relay, RadioBand band, BlockPos self,
                                      float rxAntenna, float weather) {
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
        int range = relay.getOutRange();
        double distSq = relay.getBlockPos().distSqr(self);
        if (distSq >= (double) range * (double) range) {
            return 0.0f;
        }
        double dist = Math.sqrt(distSq);
        float distance = SignalQuality.distanceFactor(dist, range, band);
        if (distance <= 0.0f) {
            return 0.0f;
        }
        float los = RadioPropagation.lineOfSightFactor(level, relay.getBlockPos(), self, isAM);
        return SignalQuality.finalQuality(distance * los * weather * rxAntenna, tuning);
    }

    private float rawQualityFrom(RadioTransmitterBlockEntity tx, RadioBand band, BlockPos self,
                                 float rxAntenna, float weather) {
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
        int range = tx.getRange();
        double distSq = tx.getBlockPos().distSqr(self);
        if (distSq >= (double) range * (double) range) {
            return 0.0f;
        }
        double dist = Math.sqrt(distSq);
        float distance = SignalQuality.distanceFactor(dist, range, band);
        if (distance <= 0.0f) {
            return 0.0f;
        }
        float los = RadioPropagation.lineOfSightFactor(level, tx.getBlockPos(), self, isAM);
        return SignalQuality.finalQuality(distance * los * weather * rxAntenna, tuning);
    }

    public int getAntennaRodCount() {
        if (level == null) {
            return 0;
        }
        return RadioPropagation.countAntennaRods(level, getBlockPos());
    }

    /** @deprecated prefer {@link #acceptsFrom(BlockPos)} on the hot voice path */
    @Deprecated
    public boolean isDominantTransmitter(RadioTransmitterBlockEntity tx) {
        return tx != null && acceptsFrom(tx.getBlockPos());
    }

    public float rawQualityFrom(RadioTransmitterBlockEntity tx) {
        if (level == null || tx == null) {
            return 0.0f;
        }
        float rxAntenna = RadioPropagation.antennaReceiveMultiplier(level, getBlockPos());
        float weather = RadioPropagation.weatherFactor(level, isAM);
        return rawQualityFrom(tx, getBand(), getBlockPos(), rxAntenna, weather);
    }

    private void syncQualityToNearby(ServerLevel level, boolean ignore) {
        AABB box = new AABB(getBlockPos()).inflate(LISTENER_SYNC_RADIUS);
        List<ServerPlayer> players = level.getEntitiesOfClass(ServerPlayer.class, box);
        if (players.isEmpty()) {
            return;
        }

        float voiceVol = SignalQuality.voiceVolume(signalQuality, volume);
        float staticVol = active ? SignalQuality.staticVolume(signalQuality, volume) : 0.0f;

        // Skip only when nothing meaningful changed (still throttled by QUALITY_SYNC_INTERVAL)
        if (active == lastSyncedActive
                && Math.abs(signalQuality - lastSyncedQuality) < 0.02f
                && Math.abs(voiceVol - lastSyncedVoice) < 0.02f
                && Math.abs(staticVol - lastSyncedStatic) < 0.02f) {
            return;
        }

        ReceiverQualityPayload payload = new ReceiverQualityPayload(
                getBlockPos(), signalQuality, voiceVol, staticVol, active
        );
        for (ServerPlayer player : players) {
            PacketDistributor.sendToPlayer(player, payload);
        }
        lastSyncedQuality = signalQuality;
        lastSyncedVoice = voiceVol;
        lastSyncedStatic = staticVol;
        lastSyncedActive = active;
    }

    private void syncSpectrumToNearby(ServerLevel level) {
        // Realism: never send assist data (was spamming empty packets every few ticks)
        if (RealRadioConfig.realismMode()) {
            return;
        }
        if (!active) {
            return;
        }
        AABB box = new AABB(getBlockPos()).inflate(LISTENER_SYNC_RADIUS);
        List<ServerPlayer> players = level.getEntitiesOfClass(ServerPlayer.class, box);
        if (players.isEmpty()) {
            return;
        }
        List<NearbyStationsPayload.StationMarker> markers = buildSpectrumMarkers();
        NearbyStationsPayload payload = new NearbyStationsPayload(getBlockPos(), markers);
        for (ServerPlayer player : players) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    private List<NearbyStationsPayload.StationMarker> buildSpectrumMarkers() {
        List<NearbyStationsPayload.StationMarker> out = new ArrayList<>();
        if (level == null) {
            return out;
        }
        RadioBand band = getBand();
        BlockPos self = getBlockPos();
        float rxAntenna = RadioPropagation.antennaReceiveMultiplier(level, self);
        float weather = RadioPropagation.weatherFactor(level, isAM);
        for (RadioTransmitterBlockEntity tx : RadioManager.transmitters()) {
            if (!tx.isActive() || tx.getLevel() != level || tx.isAM() != isAM) {
                continue;
            }
            if (!ChannelKeys.matches(tx.getChannelKey(), channelKey)) {
                continue;
            }
            int range = tx.getRange();
            double distSq = tx.getBlockPos().distSqr(self);
            if (distSq >= (double) range * (double) range) {
                continue;
            }
            double dist = Math.sqrt(distSq);
            float distance = SignalQuality.distanceFactor(dist, range, band);
            if (distance <= 0.0f) {
                continue;
            }
            float los = RadioPropagation.lineOfSightFactor(level, tx.getBlockPos(), self, isAM);
            float strength = SignalQuality.finalQuality(distance * los * weather * rxAntenna, 1.0f);
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
        if (!active) {
            this.recording = false;
            this.dominantSourcePos = null;
            this.signalQuality = 0.0f;
            if (level != null && !level.isClientSide) {
                RadioVoiceService.removeSource(this);
                voiceSourceReady = false;
            }
        } else if (level != null && !level.isClientSide) {
            voiceSourceReady = RadioVoiceService.createSource(this) != null;
        }
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
        boolean wasActive = this.active;
        this.active = active;
        this.channelKey = ChannelKeys.clamp(channelKey);
        if (!active) {
            this.recording = false;
            this.dominantSourcePos = null;
            this.signalQuality = 0.0f;
        }
        if (level != null && !level.isClientSide) {
            if (active && !wasActive) {
                voiceSourceReady = RadioVoiceService.createSource(this) != null;
            } else if (!active && wasActive) {
                RadioVoiceService.removeSource(this);
                voiceSourceReady = false;
            }
        }
        setChangedAndSync();
    }

    public void applySettings(float frequency, boolean isAM, float volume, boolean active) {
        applySettings(frequency, isAM, volume, active, this.channelKey);
    }

    private void setChangedAndSync() {
        setChanged();
        // Invalidate quality packet cache so clients get fresh volumes after GUI change
        lastSyncedQuality = Float.NaN;
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
