package com.realradio.common.util;

import com.realradio.common.blockentity.RadioReceiverBlockEntity;
import com.realradio.common.item.RadioTapeItem;
import com.realradio.config.RealRadioConfig;
import com.realradio.common.registry.ModItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side ring buffer of Opus frames while RX is in REC mode.
 * Stopping REC (or power off) drops a {@link RadioTapeItem} near the receiver.
 */
public final class AirRecording {
    private static final Map<Long, Session> SESSIONS = new ConcurrentHashMap<>();
    /** Rough Opus frame rate used for duration cap (~50 fps). */
    private static final int FRAMES_PER_SECOND = 50;

    private AirRecording() {
    }

    public static void captureFrame(RadioReceiverBlockEntity rx, byte[] data, long sequence,
                                    boolean stereo, float frequency, boolean isAM, int channelKey) {
        if (rx == null || !rx.isRecording() || data == null || data.length == 0) {
            return;
        }
        Level level = rx.getLevel();
        if (!(level instanceof ServerLevel)) {
            return;
        }
        long key = rx.getBlockPos().asLong();
        Session session = SESSIONS.computeIfAbsent(key, k -> new Session(frequency, isAM, channelKey));
        int maxFrames = Math.max(FRAMES_PER_SECOND, RealRadioConfig.maxRecordingSeconds() * FRAMES_PER_SECOND);
        if (session.frames.size() >= maxFrames) {
            // Cap reached — auto-stop and eject tape
            finish(rx);
            return;
        }
        session.frames.add(new Frame(data.clone(), sequence, stereo));
        session.lastFrequency = frequency;
        session.lastIsAM = isAM;
        session.lastKey = channelKey;
    }

    public static void onRecordingStarted(RadioReceiverBlockEntity rx) {
        if (rx == null) {
            return;
        }
        SESSIONS.put(rx.getBlockPos().asLong(), new Session(rx.getFrequency(), rx.isAM(), rx.getChannelKey()));
    }

    public static void finish(RadioReceiverBlockEntity rx) {
        if (rx == null) {
            return;
        }
        Session session = SESSIONS.remove(rx.getBlockPos().asLong());
        rx.setRecording(false);
        if (session == null || session.frames.isEmpty()) {
            return;
        }
        Level level = rx.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        ItemStack tape = new ItemStack(ModItems.RADIO_TAPE.get());
        RadioTapeItem.writeFrames(tape, session.frames, session.lastFrequency, session.lastIsAM, session.lastKey);
        var pos = rx.getBlockPos();
        serverLevel.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(
                serverLevel,
                pos.getX() + 0.5,
                pos.getY() + 1.0,
                pos.getZ() + 0.5,
                tape
        ));
    }

    public static void clear(RadioReceiverBlockEntity rx) {
        if (rx != null) {
            SESSIONS.remove(rx.getBlockPos().asLong());
        }
    }

    public record Frame(byte[] data, long sequence, boolean stereo) {
    }

    private static final class Session {
        final List<Frame> frames = new ArrayList<>();
        float lastFrequency;
        boolean lastIsAM;
        int lastKey;

        Session(float frequency, boolean isAM, int key) {
            this.lastFrequency = frequency;
            this.lastIsAM = isAM;
            this.lastKey = key;
        }
    }
}
