package com.realradio.client.coverage;

import com.realradio.common.util.RadioBand;
import com.realradio.common.util.RadioPropagation;
import com.realradio.common.util.SignalQuality;
import com.realradio.config.RealRadioConfig;
import com.realradio.network.CoverageStationsPayload;
import com.realradio.network.RequestCoveragePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

import java.util.ArrayList;
import java.util.List;

/**
 * Creative/debug coverage heat map around the player.
 */
public final class CoverageOverlay {
    private static boolean enabled;
    private static List<CoverageStationsPayload.Station> stations = List.of();
    private static int tickCounter;
    private static final int GRID = 16;
    private static final int RADIUS = 128;

    private CoverageOverlay() {
    }

    public static void toggle() {
        enabled = !enabled;
        if (enabled) {
            requestUpdate();
        }
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
        if (enabled) {
            requestUpdate();
        }
    }

    public static void updateStations(List<CoverageStationsPayload.Station> list) {
        stations = list != null ? List.copyOf(list) : List.of();
    }

    public static void onClientTick() {
        if (!enabled) {
            return;
        }
        if (!RealRadioConfig.enableCoverageOverlay()) {
            enabled = false;
            return;
        }
        if (RealRadioConfig.realismMode()) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null || (!player.isCreative() && !player.hasPermissions(2))) {
                enabled = false;
                return;
            }
        }
        tickCounter++;
        if (tickCounter % 40 == 0) {
            requestUpdate();
        }
    }

    private static void requestUpdate() {
        PacketDistributor.sendToServer(new RequestCoveragePayload());
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (!enabled || event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        Level level = mc.level;
        if (player == null || level == null || stations.isEmpty()) {
            return;
        }

        PoseStack pose = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer consumer = buffers.getBuffer(RenderType.debugFilledBox());

        BlockPos origin = player.blockPosition();
        int y = origin.getY();

        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);

        for (int dx = -RADIUS; dx <= RADIUS; dx += GRID) {
            for (int dz = -RADIUS; dz <= RADIUS; dz += GRID) {
                int x = origin.getX() + dx;
                int z = origin.getZ() + dz;
                BlockPos sample = new BlockPos(x, y, z);
                float best = bestQuality(level, sample);
                if (best <= 0.02f) {
                    continue;
                }
                int color = heatColor(best);
                float a = 0.25f + 0.45f * best;
                float r = ((color >> 16) & 0xFF) / 255f;
                float g = ((color >> 8) & 0xFF) / 255f;
                float b = (color & 0xFF) / 255f;
                float x0 = x + 0.1f;
                float y0 = y + 0.05f;
                float z0 = z + 0.1f;
                float x1 = x + GRID - 0.1f;
                float y1 = y + 0.15f;
                float z1 = z + GRID - 0.1f;
                LevelRenderer.addChainedFilledBoxVertices(
                        pose, consumer, x0, y0, z0, x1, y1, z1, r, g, b, a
                );
            }
        }

        pose.popPose();
        buffers.endBatch(RenderType.debugFilledBox());
    }

    private static float bestQuality(Level level, BlockPos sample) {
        float best = 0.0f;
        for (CoverageStationsPayload.Station st : stations) {
            RadioBand band = RadioBand.fromAm(st.isAM());
            double dist = Math.sqrt(st.pos().distSqr(sample));
            float distance = SignalQuality.distanceFactor(dist, st.range(), band);
            if (distance <= 0.0f) {
                continue;
            }
            float los = RadioPropagation.lineOfSightFactor(level, st.pos(), sample, st.isAM());
            float weather = RadioPropagation.weatherFactor(level, st.isAM());
            float q = SignalQuality.finalQuality(distance * los * weather, 1.0f);
            if (q > best) {
                best = q;
            }
        }
        return best;
    }

    private static int heatColor(float q) {
        if (q < 0.25f) {
            return 0xCC3333;
        }
        if (q < 0.5f) {
            return 0xCCAA22;
        }
        if (q < 0.75f) {
            return 0x66BB33;
        }
        return 0x22CC66;
    }
}
