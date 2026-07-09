package com.realradio.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Vintage radio-style widgets: dial slider, LCD, S-meter, compact toggles.
 */
public final class RadioWidgets {
    public static final int COL_WOOD_DARK = 0xFF2A1F12;
    public static final int COL_WOOD = 0xFF3A2A1A;
    public static final int COL_WOOD_MID = 0xFF6B4E2E;
    public static final int COL_AMBER = 0xFFC9A227;
    public static final int COL_AMBER_LIT = 0xFFE8D5A3;
    public static final int COL_AMBER_DIM = 0xFFA89870;
    public static final int COL_LCD_BG = 0xFF1A2A18;
    public static final int COL_LCD_BORDER = 0xFF3A5A30;
    public static final int COL_LCD_TEXT = 0xFF7CFF6A;
    public static final int COL_LED_ON = 0xFF33FF66;
    public static final int COL_LED_OFF = 0xFF662222;

    private RadioWidgets() {
    }

    public static Button compactButton(int x, int y, int w, int h, Component label, Button.OnPress onPress) {
        return Button.builder(label, onPress).bounds(x, y, w, h).build();
    }

    /**
     * Draws an amber LCD-style frequency readout.
     *
     * @param mainLine e.g. "98.0 MHz"
     * @param sideLine e.g. "FM" or "ON AIR"
     */
    public static void drawLcd(GuiGraphics graphics, Font font, int x, int y, int w, int h,
                               String mainLine, String sideLine, boolean powered) {
        graphics.fill(x, y, x + w, y + h, COL_WOOD_DARK);
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, powered ? COL_LCD_BG : 0xFF121210);
        graphics.fill(x + 2, y + 2, x + w - 2, y + 3, COL_LCD_BORDER);

        int textColor = powered ? COL_LCD_TEXT : 0xFF335533;
        int mainW = font.width(mainLine);
        graphics.drawString(font, mainLine, x + (w - mainW) / 2, y + (h - 8) / 2 - 1, textColor, false);

        if (sideLine != null && !sideLine.isEmpty()) {
            int sideW = font.width(sideLine);
            graphics.drawString(font, sideLine, x + w - sideW - 6, y + h - 12, powered ? COL_AMBER : COL_AMBER_DIM, false);
        }
    }

    /**
     * Classic multi-segment S-meter.
     */
    public static void drawSMeter(GuiGraphics graphics, Font font, int x, int y, int w,
                                  float quality, Component word, String percentText) {
        int segments = 10;
        int gap = 2;
        int segW = Math.max(4, (w - (segments - 1) * gap) / segments);
        int meterH = 10;
        int usedW = segments * segW + (segments - 1) * gap;
        int filled = Math.round(segments * Mth.clamp(quality, 0.0f, 1.0f));
        int fillColor = qualityColor(quality);

        for (int i = 0; i < segments; i++) {
            int sx = x + i * (segW + gap);
            int color = i < filled ? fillColor : 0xFF2A1F12;
            graphics.fill(sx, y, sx + segW, y + meterH, color);
            graphics.fill(sx, y, sx + segW, y + 1, 0x44000000);
        }

        String label = word.getString() + "  " + percentText;
        graphics.drawString(font, label, x + usedW + 6, y + 1, COL_AMBER_LIT, false);
    }

    public static int qualityColor(float quality) {
        if (quality <= 0.001f) {
            return 0xFF665544;
        }
        if (quality < 0.20f) {
            return 0xFFCC4433;
        }
        if (quality < 0.45f) {
            return 0xFFCCAA33;
        }
        if (quality < 0.75f) {
            return 0xFF66BB44;
        }
        return 0xFF33CC66;
    }

    /** Thin progress bar for auto-range visualization. */
    public static void drawRangeBar(GuiGraphics graphics, Font font, int x, int y, int w,
                                    int rangeBlocks, int maxVisualRange, String label) {
        graphics.drawString(font, label, x, y, COL_AMBER_LIT, false);
        int barY = y + 11;
        int barH = 6;
        graphics.fill(x, barY, x + w, barY + barH, COL_WOOD_DARK);
        float n = maxVisualRange <= 0 ? 0f : Mth.clamp(rangeBlocks / (float) maxVisualRange, 0f, 1f);
        int fill = Math.round(w * n);
        if (fill > 0) {
            graphics.fill(x, barY, x + fill, barY + barH, COL_AMBER);
        }
    }

    /** Pulsing mic / activity meter for the transmitter. */
    public static void drawMicMeter(GuiGraphics graphics, Font font, int x, int y, int w, boolean speaking, boolean powered) {
        graphics.drawString(font, Component.translatable("gui.real_radio.mic").getString(), x, y, COL_AMBER_DIM, false);
        int barY = y + 11;
        int barH = 8;
        graphics.fill(x, barY, x + w, barY + barH, COL_WOOD_DARK);
        if (!powered) {
            return;
        }
        float level = speaking ? 0.55f + 0.45f * (float) Math.abs(Math.sin(System.currentTimeMillis() / 90.0)) : 0.08f;
        int fill = Math.round(w * level);
        int color = speaking ? 0xFF33CC66 : 0xFF556644;
        if (fill > 0) {
            graphics.fill(x, barY, x + fill, barY + barH, color);
        }
    }

    /** Spectrum marker for dial (frequency + relative strength). */
    public record SpectrumMark(float frequency, float strength) {
    }

    public static final class RadioSlider extends AbstractWidget {
        private final double min;
        private final double max;
        private final double wheelStep;
        private final boolean showDialTicks;
        private final String minLabel;
        private final String maxLabel;
        private final Consumer<Double> onChange;
        private final Consumer<Double> onRelease;
        private Supplier<List<SpectrumMark>> spectrumSupplier = Collections::emptyList;
        private double value;
        private boolean dragging;
        private long lastDebounceMs;
        private static final long DEBOUNCE_MS = 50L;

        public RadioSlider(int x, int y, int width, int height, Component message,
                           double min, double max, double initial, double wheelStep,
                           boolean showDialTicks, String minLabel, String maxLabel,
                           Consumer<Double> onChange, Consumer<Double> onRelease) {
            super(x, y, width, height, message);
            this.min = min;
            this.max = max;
            this.wheelStep = wheelStep > 0 ? wheelStep : (max - min) / 100.0;
            this.showDialTicks = showDialTicks;
            this.minLabel = minLabel;
            this.maxLabel = maxLabel;
            this.value = Mth.clamp(initial, min, max);
            this.onChange = onChange;
            this.onRelease = onRelease;
        }

        /** Simpler ctor without dial chrome. */
        public RadioSlider(int x, int y, int width, int height, Component message,
                           double min, double max, double initial, double wheelStep,
                           Consumer<Double> onChange, Consumer<Double> onRelease) {
            this(x, y, width, height, message, min, max, initial, wheelStep, false, null, null, onChange, onRelease);
        }

        public void setSpectrumSupplier(Supplier<List<SpectrumMark>> supplier) {
            this.spectrumSupplier = supplier != null ? supplier : Collections::emptyList;
        }

        public double getValue() {
            return value;
        }

        public void setValue(double value) {
            this.value = Mth.clamp(value, min, max);
        }

        public void nudge(double delta) {
            setValue(value + delta);
            onChange.accept(value);
            onRelease.accept(value);
        }

        private double normalized() {
            if (max <= min) {
                return 0.0;
            }
            return (value - min) / (max - min);
        }

        private void setFromMouse(double mouseX) {
            double n = Mth.clamp((mouseX - (getX() + 4)) / (double) (width - 8), 0.0, 1.0);
            value = min + n * (max - min);
            onChange.accept(value);
            long now = System.currentTimeMillis();
            if (now - lastDebounceMs >= DEBOUNCE_MS) {
                lastDebounceMs = now;
                onRelease.accept(value);
            }
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            dragging = true;
            setFromMouse(mouseX);
        }

        @Override
        protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
            if (dragging) {
                setFromMouse(mouseX);
            }
        }

        @Override
        public void onRelease(double mouseX, double mouseY) {
            if (dragging) {
                dragging = false;
                lastDebounceMs = 0L;
                onRelease.accept(value);
            }
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
            if (!isHovered()) {
                return false;
            }
            double delta = scrollY > 0 ? wheelStep : -wheelStep;
            setValue(value + delta);
            onChange.accept(value);
            onRelease.accept(value);
            return true;
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            Font font = Minecraft.getInstance().font;

            // Label
            String text = getMessage().getString();
            int labelY = getY() - 12;
            int textW = font.width(text);
            graphics.fill(getX() - 1, labelY - 1, getX() + Math.min(width, textW + 4), labelY + 10, 0xAA1A1208);
            graphics.drawString(font, text, getX(), labelY, COL_AMBER_LIT, false);

            // Track
            int trackY = getY() + height / 2;
            graphics.fill(getX(), trackY - 2, getX() + width, trackY + 2, COL_WOOD);
            graphics.fill(getX() + 1, trackY - 1, getX() + width - 1, trackY + 1, COL_WOOD_MID);

            // Spectrum peaks (nearby stations) above the track
            List<SpectrumMark> marks = spectrumSupplier.get();
            if (marks != null && max > min) {
                for (SpectrumMark mark : marks) {
                    if (mark.frequency() < min || mark.frequency() > max) {
                        continue;
                    }
                    float n = (float) ((mark.frequency() - min) / (max - min));
                    int mx = getX() + 4 + Math.round((width - 8) * n);
                    int h = 3 + Math.round(7 * Mth.clamp(mark.strength(), 0f, 1f));
                    int col = mark.strength() > 0.5f ? 0xFF44DD66 : mark.strength() > 0.2f ? 0xFFCCAA33 : 0xFF886633;
                    graphics.fill(mx, trackY - 2 - h, mx + 2, trackY - 2, col);
                }
            }

            if (showDialTicks) {
                int ticks = 9;
                for (int i = 0; i <= ticks; i++) {
                    int tx = getX() + 4 + (int) ((width - 8) * (i / (double) ticks));
                    int th = (i == 0 || i == ticks || i == ticks / 2) ? 5 : 3;
                    graphics.fill(tx, trackY + 3, tx + 1, trackY + 3 + th, COL_AMBER_DIM);
                }
                if (minLabel != null) {
                    graphics.drawString(font, minLabel, getX(), getY() + height - 1, COL_AMBER_DIM, false);
                }
                if (maxLabel != null) {
                    int mw = font.width(maxLabel);
                    graphics.drawString(font, maxLabel, getX() + width - mw, getY() + height - 1, COL_AMBER_DIM, false);
                }
            }

            // Filled portion
            int fill = (int) ((width - 8) * normalized());
            graphics.fill(getX() + 4, trackY - 1, getX() + 4 + fill, trackY + 1, COL_AMBER);

            // Knob
            int knobX = getX() + 4 + fill - 3;
            graphics.fill(knobX, getY() + 2, knobX + 6, getY() + height - 2, COL_AMBER_LIT);
            graphics.fill(knobX + 1, getY() + 3, knobX + 5, getY() + height - 3, 0xFF8B6914);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    /** Tiny ± step buttons next to a frequency dial. */
    public static Button stepButton(int x, int y, int size, String symbol, Runnable action) {
        return Button.builder(Component.literal(symbol), b -> action.run())
                .bounds(x, y, size, size)
                .build();
    }
}
