package com.realradio.client.gui;

import com.realradio.common.menu.RadioReceiverMenu;
import com.realradio.common.util.RadioBand;
import com.realradio.common.util.SignalQuality;
import com.realradio.network.UpdateReceiverPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

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

        // Frequency dial with ± fine tune
        freqMinus = RadioWidgets.stepButton(x + 16, y + 78, 16, "−", () -> stepFrequency(-1));
        addRenderableWidget(freqMinus);

        frequencySlider = createFrequencySlider(sliderX, y + 78);
        addRenderableWidget(frequencySlider);

        freqPlus = RadioWidgets.stepButton(sliderX + sliderW + 2, y + 78, 16, "+", () -> stepFrequency(1));
        addRenderableWidget(freqPlus);

        // Volume
        volumeSlider = new RadioWidgets.RadioSlider(
                x + 16, y + 118, contentW, 16,
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

        int btnW = (contentW - 8) / 2;
        amFmButton = Button.builder(amFmLabel(), b -> {
            isAM = !isAM;
            frequency = band().defaultFrequency();
            rebuildFrequencyWidgets();
            b.setMessage(amFmLabel());
            playClick();
            sendUpdate();
        }).bounds(x + 16, y + 148, btnW, 20).build();
        addRenderableWidget(amFmButton);

        powerButton = Button.builder(powerLabel(), b -> {
            active = !active;
            b.setMessage(powerLabel());
            playClick();
            sendUpdate();
        }).bounds(x + 16 + btnW + 8, y + 148, btnW, 20).build();
        addRenderableWidget(powerButton);
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
        return new RadioWidgets.RadioSlider(
                x, y, sliderW, 16,
                Component.translatable("gui.real_radio.frequency"),
                b.minFrequency(), b.maxFrequency(), frequency, b.step(),
                true,
                formatEdge(b.minFrequency()),
                formatEdge(b.maxFrequency()),
                v -> {
                    frequency = v.floatValue();
                },
                v -> {
                    frequency = b.snap(v.floatValue());
                    frequencySlider.setValue(frequency);
                    sendUpdate();
                }
        );
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
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        super.renderBg(graphics, partialTick, mouseX, mouseY);
        drawPowerLed(graphics, active);

        // LCD frequency display
        String main = band().format(frequency);
        String side = isAM ? "AM" : "FM";
        RadioWidgets.drawLcd(graphics, font, leftPos + 16, topPos + 30, imageWidth - 32, 28, main, side, active);

        // S-meter
        float quality = active ? menu.getSignalQuality() : 0.0f;
        Component word = SignalQuality.qualityWord(quality);
        String pct = Math.round(quality * 100) + "%";
        graphics.drawString(font,
                Component.translatable("gui.real_radio.s_meter").getString(),
                leftPos + 16, topPos + 174, RadioWidgets.COL_AMBER_DIM, false);
        RadioWidgets.drawSMeter(graphics, font, leftPos + 16, topPos + 184, 110, quality, word, pct);
    }
}
