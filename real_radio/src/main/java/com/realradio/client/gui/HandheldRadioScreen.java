package com.realradio.client.gui;

import com.realradio.common.menu.HandheldRadioMenu;
import com.realradio.common.util.ChannelKeys;
import com.realradio.common.util.RadioBand;
import com.realradio.network.UpdateHandheldPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public class HandheldRadioScreen extends AbstractRadioScreen<HandheldRadioMenu> {
    private float frequency;
    private boolean isAM;
    private float volume;
    private boolean active;
    private int channelKey;
    private final boolean mainHand;

    private RadioWidgets.RadioSlider frequencySlider;
    private RadioWidgets.RadioSlider volumeSlider;
    private Button amFmButton;
    private Button powerButton;
    private Button freqMinus;
    private Button freqPlus;
    private Button keyMinus;
    private Button keyPlus;

    public HandheldRadioScreen(HandheldRadioMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.mainHand = menu.getHand() == InteractionHand.MAIN_HAND;
        this.frequency = menu.getFrequency();
        this.isAM = menu.isAM();
        this.volume = menu.getVolume();
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

        volumeSlider = new RadioWidgets.RadioSlider(
                x + 16, y + 108, contentW, 16,
                Component.translatable("gui.real_radio.volume", Math.round(volume * 100)),
                0.0, 1.0, volume, 0.05,
                v -> volume = v.floatValue(),
                v -> {
                    volume = v.floatValue();
                    volumeSlider.setMessage(Component.translatable("gui.real_radio.volume", Math.round(volume * 100)));
                    sendUpdate();
                }
        );
        addRenderableWidget(volumeSlider);

        keyMinus = RadioWidgets.stepButton(x + 16, y + 132, 16, "−", () -> stepKey(-1));
        keyPlus = RadioWidgets.stepButton(x + 16 + contentW - 16, y + 132, 16, "+", () -> stepKey(1));
        addRenderableWidget(keyMinus);
        addRenderableWidget(keyPlus);

        int btnW = (contentW - 8) / 2;
        amFmButton = Button.builder(amFmLabel(), b -> {
            isAM = !isAM;
            frequency = band().defaultFrequency();
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
                b.isAM() ? String.format("%.0f", b.minFrequency()) : String.format("%.1f", b.minFrequency()),
                b.isAM() ? String.format("%.0f", b.maxFrequency()) : String.format("%.1f", b.maxFrequency()),
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

    private void stepKey(int dir) {
        channelKey = ChannelKeys.clamp(channelKey + dir);
        playClick();
        sendUpdate();
    }

    private RadioBand band() {
        return RadioBand.fromAm(isAM);
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
        PacketDistributor.sendToServer(new UpdateHandheldPayload(
                mainHand, frequency, isAM, volume, active, channelKey
        ));
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        super.renderBg(graphics, partialTick, mouseX, mouseY);
        drawPowerLed(graphics, active);
        RadioWidgets.drawLcd(graphics, font, leftPos + 16, topPos + 30, imageWidth - 32, 28,
                band().format(frequency), isAM ? "AM" : "FM", active);
        String keyLabel = Component.translatable("gui.real_radio.channel_key", ChannelKeys.format(channelKey)).getString();
        graphics.drawString(font, keyLabel, leftPos + 36, topPos + 136, RadioWidgets.COL_AMBER_LIT, false);
        graphics.drawString(font, Component.translatable("gui.real_radio.ptt").getString() + " key",
                leftPos + 16, topPos + 190, RadioWidgets.COL_AMBER_DIM, false);
    }
}
