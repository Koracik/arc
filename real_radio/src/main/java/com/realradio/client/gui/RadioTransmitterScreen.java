package com.realradio.client.gui;

import com.realradio.common.menu.RadioTransmitterMenu;
import com.realradio.common.util.ChannelKeys;
import com.realradio.common.util.ChannelPresets;
import com.realradio.common.util.RadioBand;
import com.realradio.config.RealRadioConfig;
import com.realradio.network.RadioPresetPayload;
import com.realradio.network.UpdateTransmitterPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public class RadioTransmitterScreen extends AbstractRadioScreen<RadioTransmitterMenu> {
    private final BlockPos blockPos;

    private float frequency;
    private boolean isAM;
    private boolean active;
    private int channelKey;

    private RadioWidgets.RadioSlider frequencySlider;
    private Button amFmButton;
    private Button powerButton;
    private Button freqMinus;
    private Button freqPlus;
    private Button keyMinus;
    private Button keyPlus;
    private final Button[] presetButtons = new Button[ChannelPresets.COUNT];

    public RadioTransmitterScreen(RadioTransmitterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.blockPos = menu.getBlockPos();
        this.frequency = menu.getFrequency();
        this.isAM = menu.isAM();
        this.active = menu.isActive();
        this.channelKey = menu.getChannelKey();
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

        keyMinus = RadioWidgets.stepButton(x + 16, y + 132, 16, "−", () -> stepKey(-1));
        keyPlus = RadioWidgets.stepButton(x + 16 + contentW - 16, y + 132, 16, "+", () -> stepKey(1));
        addRenderableWidget(keyMinus);
        addRenderableWidget(keyPlus);

        int presetY = y + 152;
        int pw = (contentW - 8) / 3;
        for (int i = 0; i < ChannelPresets.COUNT; i++) {
            final int slot = i;
            presetButtons[i] = Button.builder(
                    Component.translatable("gui.real_radio.preset", slot + 1),
                    b -> {
                        PacketDistributor.sendToServer(new RadioPresetPayload(blockPos, true, slot, false));
                        playClick();
                    }
            ).bounds(x + 16 + i * (pw + 4), presetY, pw, 16).build();
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
        }).bounds(x + 16, y + 172, btnW, 18).build();
        addRenderableWidget(amFmButton);

        powerButton = Button.builder(powerLabel(), b -> {
            active = !active;
            b.setMessage(powerLabel());
            playClick();
            sendUpdate();
        }).bounds(x + 16 + btnW + 8, y + 172, btnW, 18).build();
        addRenderableWidget(powerButton);
    }

    private void stepKey(int dir) {
        channelKey = ChannelKeys.clamp(channelKey + dir);
        playClick();
        sendUpdate();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 1) {
            for (int i = 0; i < presetButtons.length; i++) {
                Button b = presetButtons[i];
                if (b != null && b.isMouseOver(mouseX, mouseY)) {
                    PacketDistributor.sendToServer(new RadioPresetPayload(blockPos, true, i, true));
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
        return new RadioWidgets.RadioSlider(
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

    private int displayRange() {
        int local = band().rangeBlocks(frequency);
        int menuRange = menu.getRange();
        if (menuRange > local) {
            return menuRange;
        }
        return Math.max(local, menuRange);
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
        PacketDistributor.sendToServer(new UpdateTransmitterPayload(blockPos, frequency, isAM, active, channelKey));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
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
        active = menu.isActive();
        channelKey = menu.getChannelKey();
        if (powerButton != null) {
            powerButton.setMessage(powerLabel());
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        super.renderBg(graphics, partialTick, mouseX, mouseY);
        drawPowerLed(graphics, active);

        String main = band().format(frequency);
        String side = active
                ? Component.translatable("gui.real_radio.on_air").getString()
                : (isAM ? "AM" : "FM");
        RadioWidgets.drawLcd(graphics, font, leftPos + 16, topPos + 30, imageWidth - 32, 28, main, side, active);

        int range = displayRange();
        String rangeLabel = Component.translatable("gui.real_radio.range_auto", range).getString();
        int maxVisual = Math.round(RealRadioConfig.baseRangeBlocks() * 3.0f
                * RealRadioConfig.amNightMultiplier() * RealRadioConfig.antennaHeightBonus());
        RadioWidgets.drawRangeBar(graphics, font, leftPos + 16, topPos + 104, imageWidth - 32,
                range, maxVisual, rangeLabel);

        String keyLabel = Component.translatable("gui.real_radio.channel_key", ChannelKeys.format(channelKey)).getString();
        graphics.drawString(font, keyLabel, leftPos + 36, topPos + 136, RadioWidgets.COL_AMBER_LIT, false);

        RadioWidgets.drawMicMeter(graphics, font, leftPos + 16, topPos + 194, imageWidth - 32,
                menu.isSpeaking(), active);
    }
}
