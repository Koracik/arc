package com.realradio.common.item;

import com.realradio.common.util.AirRecording;
import com.realradio.common.util.ChannelKeys;
import com.realradio.common.util.RadioBand;
import com.realradio.common.util.RadioBroadcast;
import com.realradio.common.util.RadioPropagation;
import com.realradio.config.RealRadioConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Stores recorded Opus frames and plays them back as a virtual radio transmission.
 */
public class RadioTapeItem extends Item {
    private static final String TAG_FRAMES = "Frames";
    private static final String TAG_FREQ = "Frequency";
    private static final String TAG_AM = "IsAM";
    private static final String TAG_KEY = "ChannelKey";
    private static final String TAG_COUNT = "FrameCount";

    private static final ExecutorService PLAYBACK = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "real-radio-tape-playback");
        t.setDaemon(true);
        return t;
    });

    public RadioTapeItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static void writeFrames(ItemStack stack, List<AirRecording.Frame> frames,
                                   float frequency, boolean isAM, int channelKey) {
        ItemNbt.put(stack, tag -> {
            tag.putFloat(TAG_FREQ, frequency);
            tag.putBoolean(TAG_AM, isAM);
            tag.putInt(TAG_KEY, ChannelKeys.clamp(channelKey));
            tag.putInt(TAG_COUNT, frames.size());
            ListTag list = new ListTag();
            // Cap stored frames to avoid huge NBT (keep last N)
            int maxStore = Math.min(frames.size(), RealRadioConfig.maxRecordingSeconds() * 50);
            int start = Math.max(0, frames.size() - maxStore);
            for (int i = start; i < frames.size(); i++) {
                AirRecording.Frame frame = frames.get(i);
                CompoundTag f = new CompoundTag();
                f.putByteArray("d", frame.data());
                f.putLong("s", frame.sequence());
                f.putBoolean("st", frame.stereo());
                list.add(f);
            }
            tag.putInt(TAG_COUNT, list.size());
            tag.put(TAG_FRAMES, list);
        });
    }

    public static boolean hasRecording(ItemStack stack) {
        CompoundTag tag = ItemNbt.get(stack);
        return tag.contains(TAG_FRAMES) && tag.getInt(TAG_COUNT) > 0;
    }

    public static List<AirRecording.Frame> readFrames(ItemStack stack) {
        List<AirRecording.Frame> out = new ArrayList<>();
        CompoundTag tag = ItemNbt.get(stack);
        if (!tag.contains(TAG_FRAMES, Tag.TAG_LIST)) {
            return out;
        }
        ListTag list = tag.getList(TAG_FRAMES, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag f = list.getCompound(i);
            out.add(new AirRecording.Frame(f.getByteArray("d"), f.getLong("s"), f.getBoolean("st")));
        }
        return out;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!hasRecording(stack)) {
            return InteractionResultHolder.pass(stack);
        }
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            playTape(serverPlayer, stack);
            player.getCooldowns().addCooldown(this, 40);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    private static void playTape(ServerPlayer player, ItemStack stack) {
        CompoundTag tag = ItemNbt.get(stack);
        float frequency = tag.getFloat(TAG_FREQ);
        boolean isAM = tag.getBoolean(TAG_AM);
        int key = ChannelKeys.clamp(tag.getInt(TAG_KEY));
        List<AirRecording.Frame> frames = readFrames(stack);
        if (frames.isEmpty()) {
            return;
        }
        RadioBand band = RadioBand.fromAm(isAM);
        int baseRange = band.rangeBlocks(frequency);
        float mult = RadioPropagation.antennaRangeMultiplier((int) player.getY())
                * RealRadioConfig.handheldRangeFactor();
        if (isAM && player.level().isNight()) {
            mult *= RealRadioConfig.amNightMultiplier();
        }
        int range = Math.max(1, Math.round(baseRange * mult));
        ServerLevel serverLevel = player.serverLevel();

        PLAYBACK.execute(() -> {
            long seqBase = System.nanoTime();
            for (int i = 0; i < frames.size(); i++) {
                if (!player.isAlive()) {
                    break;
                }
                AirRecording.Frame frame = frames.get(i);
                final int idx = i;
                serverLevel.getServer().execute(() -> {
                    if (!player.isAlive()) {
                        return;
                    }
                    RadioBroadcast.broadcast(
                            player.level(),
                            player.blockPosition(),
                            frequency,
                            isAM,
                            key,
                            range,
                            frame.data(),
                            seqBase + idx,
                            (short) 16,
                            frame.stereo(),
                            0,
                            new HashSet<>()
                    );
                });
                try {
                    Thread.sleep(20L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (!hasRecording(stack)) {
            tooltip.add(Component.literal("Empty tape"));
            return;
        }
        CompoundTag tag = ItemNbt.get(stack);
        boolean am = tag.getBoolean(TAG_AM);
        RadioBand band = RadioBand.fromAm(am);
        tooltip.add(Component.literal(band.format(tag.getFloat(TAG_FREQ))));
        tooltip.add(Component.translatable("gui.real_radio.channel_key", ChannelKeys.format(tag.getInt(TAG_KEY))));
        tooltip.add(Component.literal(tag.getInt(TAG_COUNT) + " frames"));
    }
}
