package com.realradio.client.coverage;

import com.realradio.common.util.RadioBand;
import com.realradio.common.util.SignalQuality;
import com.realradio.config.RealRadioConfig;
import com.realradio.network.CoverageStationsPayload;
import com.realradio.network.RequestCoveragePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

import java.util.ArrayList;
import java.util.List;

/**
 * Creative/debug coverage heat map. Quality grid is recomputed on a tick budget,
 * not every render frame (LOS is skipped — distance-only for performance).
 */
public final class CoverageOverlay {
    private static boolean enabled;
    private static List<CoverageStationsPayload.Station> stations = List.of();
    private static int tickCounter;
    private static final int GRID = 16;
    private static final int RADIUS = 96; // was 128 — fewer cells

    private static final List<Cell> CACHED_CELLS = new ArrayList<>();
    private static BlockPos cacheOrigin = BlockPos.ZERO;
    private static boolean cacheDirty = true;

    private CoverageOverlay() {
    }

    public static void toggle() {
        enabled = !enabled;
        if (enabled) {
            cacheDirty = true;
            requestUpdate();
        } else {
            CACHED_CELLS.clear();
        }
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
        if (enabled) {
            cacheDirty = true;
            requestUpdate();
        } else {
            CACHED_CELLS.clear();
        }
    }

    public static void updateStations(List<CoverageStationsPayload.Station> list) {
        stations = list != null ? List.copyOf(list) : List.of();
        cacheDirty = true;
    }

    public static void onClientTick() {
        if (!enabled) {
            return;
        }
        if (!RealRadioConfig.enableCoverageOverlay()) {
            enabled = false;
            CACHED_CELLS.clear();
            return;
        }
        if (RealRadioConfig.realismMode()) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null || (!player.isCreative() && !player.hasPermissions(2))) {
                enabled = false;
                CACHED_CELLS.clear();
                return;
            }
        }
        tickCounter++;
        if (tickCounter % 40 == 0) {
            requestUpdate();
        }
        // Rebuild heat grid every 10 ticks or when player moves a grid cell
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        BlockPos origin = player.blockPosition();
        if (cacheDirty
                || tickCounter % 10 == 0
                || Math.abs(origin.getX() - cacheOrigin.getX()) >= GRID
                || Math.abs(origin.getZ() - cacheOrigin.getZ()) >= GRID) {
            rebuildCache(origin);
        }
    }

    private static void requestUpdate() {
        PacketDistributor.sendToServer(new RequestCoveragePayload());
    }

    private static void rebuildCache(BlockPos origin) {
        CACHED_CELLS.clear();
        cacheOrigin = origin.immutable();
        cacheDirty = false;
        if (stations.isEmpty()) {
            return;
        }
        int y = origin.getY();
        for (int dx = -RADIUS; dx <= RADIUS; dx += GRID) {
            for (int dz = -RADIUS; dz <= RADIUS; dz += GRID) {
                int x = origin.getX() + dx;
                int z = origin.getZ() + dz;
                BlockPos sample = new BlockPos(x, y, z);
                float best = bestQualityDistanceOnly(sample);
                if (best <= 0.02f) {
                    continue;
                }
                CACHED_CELLS.add(new Cell(x, y, z, best));
            }
        }
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (!enabled || event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        if (CACHED_CELLS.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        PoseStack pose = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer consumer = buffers.getBuffer(RenderType.debugFilledBox());

        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);

        for (Cell cell : CACHED_CELLS) {
            int color = heatColor(cell.quality);
            float a = 0.25f + 0.45f * cell.quality;
            float r = ((color >> 16) & 0xFF) / 255f;
            float g = ((color >> 8) & 0xFF) / 255f;
            float b = (color & 0xFF) / 255f;
            float x0 = cell.x + 0.1f;
            float y0 = cell.y + 0.05f;
            float z0 = cell.z + 0.1f;
            float x1 = cell.x + GRID - 0.1f;
            float y1 = cell.y + 0.15f;
            float z1 = cell.z + GRID - 0.1f;
            LevelRenderer.addChainedFilledBoxVertices(
                    pose, consumer, x0, y0, z0, x1, y1, z1, r, g, b, a
            );
        }

        pose.popPose();
        buffers.endBatch(RenderType.debugFilledBox());
    }

    /** Distance-only quality — no LOS raycast (overlay is debug, must stay cheap). */
    private static float bestQualityDistanceOnly(BlockPos sample) {
        float best = 0.0f;
        for (CoverageStationsPayload.Station st : stations) {
            RadioBand band = RadioBand.fromAm(st.isAM());
            double distSq = st.pos().distSqr(sample);
            double max = st.range();
            if (distSq >= max * max) {
                continue;
            }
            double dist = Math.sqrt(distSq);
            float distance = SignalQuality.distanceFactor(dist, st.range(), band);
            if (distance > best) {
                best = distance;
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

    private record Cell(int x, int y, int z, float quality) {
    }
}
