package com.realradio.client.gui;

import com.realradio.client.sound.StationSpectrumCache;
import com.realradio.common.menu.RadioReceiverMenu;
import com.realradio.common.util.ChannelPresets;
import com.realradio.common.util.RadioBand;
import com.realradio.common.util.SignalQuality;
import com.realradio.config.RealRadioConfig;
import com.realradio.network.NearbyStationsPayload;
import com.realradio.network.RadioPresetPayload;
import com.realradio.network.UpdateReceiverPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public class RadioReceiverScreen extends AbstractRadioScreen<RadioReceiverMenu> {
    private final BlockPos blockPos;

    private float frequency;
    private boolean isAM;
    private float volume;
    private boolean active;

    private RadioWidgets.RadioSlider frequencySlider;
    private RadioWidgets.RadioSlider volumeSlider;
    private Button amFmButton;
    private Button powerButton;
    private Button freqMinus;
    private Button freqPlus;
    private final Button[] presetButtons = new Button[ChannelPresets.COUNT];

    public RadioReceiverScreen(RadioReceiverMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.blockPos = menu.getBlockPos();
        this.frequency = menu.getFrequency();
        this.isAM = menu.isAM();
        this.volume = menu.getVolume();
        this.active = menu.isActive();
    }

    @Override
    protected void init() {
        super.init();

        int x = leftPos;
        int y = topPos;
        int contentW = imageWidth - 32;
        int sliderX = x + 16 + 18;
        int sliderW = contentW - 36;

        freqMinus = RadioWidgets.stepButton(x + 16, y + 78, 16, "−", () -> stepFrequency(-1));
        addRenderableWidget(freqMinus);

        frequencySlider = createFrequencySlider(sliderX, y + 78);
        addRenderableWidget(frequencySlider);

        freqPlus = RadioWidgets.stepButton(sliderX + sliderW + 2, y + 78, 16, "+", () -> stepFrequency(1));
        addRenderableWidget(freqPlus);

        volumeSlider = new RadioWidgets.RadioSlider(
                x + 16, y + 112, contentW, 16,
                volumeLabel(),
                0.0, 1.0, volume, 0.05,
                v -> {
                    volume = v.floatValue();
                    volumeSlider.setMessage(volumeLabel());
                },
                v -> {
                    volume = v.floatValue();
                    volumeSlider.setMessage(volumeLabel());
                    sendUpdate();
                }
        );
        addRenderableWidget(volumeSlider);

        // Presets M1–M3 (left-click load, right-click save)
        int presetY = y + 140;
        int pw = (contentW - 8) / 3;
        for (int i = 0; i < ChannelPresets.COUNT; i++) {
            final int slot = i;
            presetButtons[i] = Button.builder(
                    Component.translatable("gui.real_radio.preset", slot + 1),
                    b -> {
                        // left click: load
                        PacketDistributor.sendToServer(new RadioPresetPayload(blockPos, false, slot, false));
                        // optimistic local apply after short delay is hard; pull from next container sync
                        playClick();
                    }
            ).bounds(x + 16 + i * (pw + 4), presetY, pw, 18).build();
            presetButtons[i].setTooltip(Tooltip.create(Component.translatable("gui.real_radio.preset_hint")));
            addRenderableWidget(presetButtons[i]);
        }

        int btnW = (contentW - 8) / 2;
        amFmButton = Button.builder(amFmLabel(), b -> {
            isAM = !isAM;
            frequency = band().defaultFrequency();
            rebuildFrequencyWidgets();
            b.setMessage(amFmLabel());
            playClick();
            sendUpdate();
        }).bounds(x + 16, y + 164, btnW, 20).build();
        addRenderableWidget(amFmButton);

        powerButton = Button.builder(powerLabel(), b -> {
            active = !active;
            b.setMessage(powerLabel());
            playClick();
            sendUpdate();
        }).bounds(x + 16 + btnW + 8, y + 164, btnW, 20).build();
        addRenderableWidget(powerButton);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Right-click preset buttons = save
        if (button == 1) {
            for (int i = 0; i < presetButtons.length; i++) {
                Button b = presetButtons[i];
                if (b != null && b.isMouseOver(mouseX, mouseY)) {
                    PacketDistributor.sendToServer(new RadioPresetPayload(blockPos, false, i, true));
                    playClick();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void rebuildFrequencyWidgets() {
        removeWidget(frequencySlider);
        removeWidget(freqMinus);
        removeWidget(freqPlus);
        int x = leftPos;
        int y = topPos;
        int contentW = imageWidth - 32;
        int sliderX = x + 16 + 18;
        int sliderW = contentW - 36;
        freqMinus = RadioWidgets.stepButton(x + 16, y + 78, 16, "−", () -> stepFrequency(-1));
        frequencySlider = createFrequencySlider(sliderX, y + 78);
        freqPlus = RadioWidgets.stepButton(sliderX + sliderW + 2, y + 78, 16, "+", () -> stepFrequency(1));
        addRenderableWidget(freqMinus);
        addRenderableWidget(frequencySlider);
        addRenderableWidget(freqPlus);
    }

    private RadioWidgets.RadioSlider createFrequencySlider(int x, int y) {
        int contentW = imageWidth - 32;
        int sliderW = contentW - 36;
        RadioBand b = band();
        RadioWidgets.RadioSlider slider = new RadioWidgets.RadioSlider(
                x, y, sliderW, 16,
                Component.translatable("gui.real_radio.frequency"),
                b.minFrequency(), b.maxFrequency(), frequency, b.step(),
                true,
                formatEdge(b.minFrequency()),
                formatEdge(b.maxFrequency()),
                v -> frequency = v.floatValue(),
                v -> {
                    frequency = b.snap(v.floatValue());
                    frequencySlider.setValue(frequency);
                    sendUpdate();
                }
        );
        slider.setSpectrumSupplier(this::spectrumMarks);
        return slider;
    }

    private List<RadioWidgets.SpectrumMark> spectrumMarks() {
        if (RealRadioConfig.realismMode()) {
            return List.of();
        }
        List<NearbyStationsPayload.StationMarker> raw = StationSpectrumCache.get(blockPos);
        List<RadioWidgets.SpectrumMark> out = new ArrayList<>(raw.size());
        for (NearbyStationsPayload.StationMarker m : raw) {
            out.add(new RadioWidgets.SpectrumMark(m.frequency(), m.strength()));
        }
        return out;
    }

    private void stepFrequency(int dir) {
        RadioBand b = band();
        frequency = b.snap(frequency + dir * b.step());
        if (frequencySlider != null) {
            frequencySlider.setValue(frequency);
        }
        playClick();
        sendUpdate();
    }

    private String formatEdge(float f) {
        RadioBand b = band();
        return b.isAM() ? String.format("%.0f", f) : String.format("%.1f", f);
    }

    private RadioBand band() {
        return RadioBand.fromAm(isAM);
    }

    private Component volumeLabel() {
        return Component.translatable("gui.real_radio.volume", Math.round(volume * 100));
    }

    private Component amFmLabel() {
        return Component.translatable(isAM ? "gui.real_radio.band_am" : "gui.real_radio.band_fm");
    }

    private Component powerLabel() {
        return Component.translatable(active ? "gui.real_radio.power_on" : "gui.real_radio.power_off");
    }

    private void playClick() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
        }
    }

    private void sendUpdate() {
        PacketDistributor.sendToServer(new UpdateReceiverPayload(blockPos, frequency, isAM, volume, active));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        // Sync local fields from menu when server applies a preset load
        float menuFreq = menu.getFrequency();
        boolean menuAm = menu.isAM();
        if (menuAm != isAM || Math.abs(menuFreq - frequency) > 0.001f) {
            if (frequencySlider == null || !frequencySlider.isHovered()) {
                boolean bandChanged = menuAm != isAM;
                isAM = menuAm;
                frequency = menuFreq;
                if (bandChanged) {
                    rebuildFrequencyWidgets();
                } else if (frequencySlider != null) {
                    frequencySlider.setValue(frequency);
                }
                if (amFmButton != null) {
                    amFmButton.setMessage(amFmLabel());
                }
            }
        }
        volume = menu.getVolume();
        active = menu.isActive();
        if (powerButton != null) {
            powerButton.setMessage(powerLabel());
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        super.renderBg(graphics, partialTick, mouseX, mouseY);
        drawPowerLed(graphics, active);

        String main = band().format(frequency);
        String side = isAM ? "AM" : "FM";
        RadioWidgets.drawLcd(graphics, font, leftPos + 16, topPos + 30, imageWidth - 32, 28, main, side, active);

        boolean realism = RealRadioConfig.realismMode();
        if (!realism) {
            float quality = active ? menu.getSignalQuality() : 0.0f;
            Component word = SignalQuality.qualityWord(quality);
            String pct = Math.round(quality * 100) + "%";
            graphics.drawString(font,
                    Component.translatable("gui.real_radio.s_meter").getString(),
                    leftPos + 16, topPos + 190, RadioWidgets.COL_AMBER_DIM, false);
            RadioWidgets.drawSMeter(graphics, font, leftPos + 16, topPos + 200, 110, quality, word, pct);

            graphics.drawString(font,
                    Component.translatable("gui.real_radio.spectrum_hint").getString(),
                    leftPos + 16, topPos + 64, RadioWidgets.COL_AMBER_DIM, false);
        } else {
            // Realism: no station assist — only frequency and ear
            graphics.drawString(font,
                    Component.translatable("gui.real_radio.realism_hint").getString(),
                    leftPos + 16, topPos + 190, RadioWidgets.COL_AMBER_DIM, false);
        }
    }
}
