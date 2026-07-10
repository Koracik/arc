package com.realradio.client.gui;

import com.realradio.common.menu.RadioRelayMenu;
import com.realradio.common.util.ChannelKeys;
import com.realradio.common.util.RadioBand;
import com.realradio.network.UpdateRelayPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public class RadioRelayScreen extends AbstractRadioScreen<RadioRelayMenu> {
    private final BlockPos blockPos;

    private float inFrequency;
    private boolean inAM;
    private float outFrequency;
    private boolean outAM;
    private boolean active;
    private int inKey;
    private int outKey;
    private boolean editOut;

    private RadioWidgets.RadioSlider frequencySlider;
    private Button amFmButton;
    private Button powerButton;
    private Button inOutToggle;
    private Button freqMinus;
    private Button freqPlus;
    private Button keyMinus;
    private Button keyPlus;

    public RadioRelayScreen(RadioRelayMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.blockPos = menu.getBlockPos();
        this.inFrequency = menu.getInFrequency();
        this.inAM = menu.isInAM();
        this.outFrequency = menu.getOutFrequency();
        this.outAM = menu.isOutAM();
        this.active = menu.isActive();
        this.inKey = menu.getInChannelKey();
        this.outKey = menu.getOutChannelKey();
    }

    @Override
    protected void init() {
        super.init();
        int x = leftPos;
        int y = topPos;
        int contentW = imageWidth - 32;
        int sliderX = x + 16 + 18;
        int sliderW = contentW - 36;

        inOutToggle = Button.builder(inOutLabel(), b -> {
            editOut = !editOut;
            b.setMessage(inOutLabel());
            rebuildFrequencyWidgets();
            playClick();
        }).bounds(x + 16, y + 62, contentW, 16).build();
        addRenderableWidget(inOutToggle);

        freqMinus = RadioWidgets.stepButton(x + 16, y + 84, 16, "−", () -> stepFrequency(-1));
        addRenderableWidget(freqMinus);
        frequencySlider = createFrequencySlider(sliderX, y + 84);
        addRenderableWidget(frequencySlider);
        freqPlus = RadioWidgets.stepButton(sliderX + sliderW + 2, y + 84, 16, "+", () -> stepFrequency(1));
        addRenderableWidget(freqPlus);

        keyMinus = RadioWidgets.stepButton(x + 16, y + 120, 16, "−", () -> stepKey(-1));
        keyPlus = RadioWidgets.stepButton(x + 16 + contentW - 16, y + 120, 16, "+", () -> stepKey(1));
        addRenderableWidget(keyMinus);
        addRenderableWidget(keyPlus);

        int btnW = (contentW - 8) / 2;
        amFmButton = Button.builder(amFmLabel(), b -> {
            if (editOut) {
                outAM = !outAM;
                outFrequency = outBand().defaultFrequency();
            } else {
                inAM = !inAM;
                inFrequency = inBand().defaultFrequency();
            }
            rebuildFrequencyWidgets();
            b.setMessage(amFmLabel());
            playClick();
            sendUpdate();
        }).bounds(x + 16, y + 160, btnW, 18).build();
        addRenderableWidget(amFmButton);

        powerButton = Button.builder(powerLabel(), b -> {
            active = !active;
            b.setMessage(powerLabel());
            playClick();
            sendUpdate();
        }).bounds(x + 16 + btnW + 8, y + 160, btnW, 18).build();
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
        freqMinus = RadioWidgets.stepButton(x + 16, y + 84, 16, "−", () -> stepFrequency(-1));
        frequencySlider = createFrequencySlider(sliderX, y + 84);
        freqPlus = RadioWidgets.stepButton(sliderX + sliderW + 2, y + 84, 16, "+", () -> stepFrequency(1));
        addRenderableWidget(freqMinus);
        addRenderableWidget(frequencySlider);
        addRenderableWidget(freqPlus);
        if (amFmButton != null) {
            amFmButton.setMessage(amFmLabel());
        }
    }

    private RadioWidgets.RadioSlider createFrequencySlider(int x, int y) {
        int contentW = imageWidth - 32;
        int sliderW = contentW - 36;
        RadioBand b = editOut ? outBand() : inBand();
        float freq = editOut ? outFrequency : inFrequency;
        return new RadioWidgets.RadioSlider(
                x, y, sliderW, 16,
                Component.translatable("gui.real_radio.frequency"),
                b.minFrequency(), b.maxFrequency(), freq, b.step(),
                true,
                formatEdge(b, b.minFrequency()),
                formatEdge(b, b.maxFrequency()),
                v -> {
                    if (editOut) {
                        outFrequency = v.floatValue();
                    } else {
                        inFrequency = v.floatValue();
                    }
                },
                v -> {
                    float snapped = b.snap(v.floatValue());
                    if (editOut) {
                        outFrequency = snapped;
                    } else {
                        inFrequency = snapped;
                    }
                    frequencySlider.setValue(snapped);
                    sendUpdate();
                }
        );
    }

    private void stepFrequency(int dir) {
        RadioBand b = editOut ? outBand() : inBand();
        if (editOut) {
            outFrequency = b.snap(outFrequency + dir * b.step());
            if (frequencySlider != null) {
                frequencySlider.setValue(outFrequency);
            }
        } else {
            inFrequency = b.snap(inFrequency + dir * b.step());
            if (frequencySlider != null) {
                frequencySlider.setValue(inFrequency);
            }
        }
        playClick();
        sendUpdate();
    }

    private void stepKey(int dir) {
        if (editOut) {
            outKey = ChannelKeys.clamp(outKey + dir);
        } else {
            inKey = ChannelKeys.clamp(inKey + dir);
        }
        playClick();
        sendUpdate();
    }

    private String formatEdge(RadioBand b, float f) {
        return b.isAM() ? String.format("%.0f", f) : String.format("%.1f", f);
    }

    private RadioBand inBand() {
        return RadioBand.fromAm(inAM);
    }

    private RadioBand outBand() {
        return RadioBand.fromAm(outAM);
    }

    private Component inOutLabel() {
        return Component.translatable(editOut ? "gui.real_radio.out_freq" : "gui.real_radio.in_freq");
    }

    private Component amFmLabel() {
        boolean am = editOut ? outAM : inAM;
        return Component.translatable(am ? "gui.real_radio.band_am" : "gui.real_radio.band_fm");
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
        PacketDistributor.sendToServer(new UpdateRelayPayload(
                blockPos, inFrequency, inAM, outFrequency, outAM, active, inKey, outKey
        ));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        active = menu.isActive();
        if (powerButton != null) {
            powerButton.setMessage(powerLabel());
        }
        if (frequencySlider == null || !frequencySlider.isHovered()) {
            inFrequency = menu.getInFrequency();
            inAM = menu.isInAM();
            outFrequency = menu.getOutFrequency();
            outAM = menu.isOutAM();
            inKey = menu.getInChannelKey();
            outKey = menu.getOutChannelKey();
            if (frequencySlider != null) {
                frequencySlider.setValue(editOut ? outFrequency : inFrequency);
            }
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        super.renderBg(graphics, partialTick, mouseX, mouseY);
        drawPowerLed(graphics, active);

        RadioBand shownBand = editOut ? outBand() : inBand();
        float shownFreq = editOut ? outFrequency : inFrequency;
        String main = shownBand.format(shownFreq);
        String side = (editOut ? "OUT " : "IN ") + (editOut ? (outAM ? "AM" : "FM") : (inAM ? "AM" : "FM"));
        RadioWidgets.drawLcd(graphics, font, leftPos + 16, topPos + 30, imageWidth - 32, 28, main, side, active);

        int key = editOut ? outKey : inKey;
        String keyLabel = Component.translatable("gui.real_radio.channel_key", ChannelKeys.format(key)).getString();
        graphics.drawString(font, keyLabel, leftPos + 36, topPos + 124, RadioWidgets.COL_AMBER_LIT, false);

        String other = Component.translatable(editOut ? "gui.real_radio.in_freq" : "gui.real_radio.out_freq").getString()
                + ": " + (editOut ? inBand().format(inFrequency) : outBand().format(outFrequency));
        graphics.drawString(font, other, leftPos + 16, topPos + 144, RadioWidgets.COL_AMBER_DIM, false);

        RadioWidgets.drawMicMeter(graphics, font, leftPos + 16, topPos + 190, imageWidth - 32,
                menu.isSpeaking(), active);
    }
}
