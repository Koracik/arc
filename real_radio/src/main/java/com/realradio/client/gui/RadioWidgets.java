package com.realradio.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Compact radio-style widgets: horizontal slider + toggle button helpers.
 */
public final class RadioWidgets {
    private RadioWidgets() {
    }

    public static Button toggle(int x, int y, int w, int h, Supplier<Boolean> state,
                                Supplier<Component> label, Runnable onPress) {
        return Button.builder(label.get(), b -> {
                    onPress.run();
                    b.setMessage(label.get());
                })
                .bounds(x, y, w, h)
                .build();
    }

    public static final class RadioSlider extends AbstractWidget {
        private final double min;
        private final double max;
        private final Consumer<Double> onChange;
        private final Consumer<Double> onRelease;
        private double value;
        private boolean dragging;
        private long lastDebounceMs;
        private static final long DEBOUNCE_MS = 100L;

        public RadioSlider(int x, int y, int width, int height, Component message,
                           double min, double max, double initial,
                           Consumer<Double> onChange, Consumer<Double> onRelease) {
            super(x, y, width, height, message);
            this.min = min;
            this.max = max;
            this.value = Mth.clamp(initial, min, max);
            this.onChange = onChange;
            this.onRelease = onRelease;
        }

        public double getValue() {
            return value;
        }

        public void setValue(double value) {
            this.value = Mth.clamp(value, min, max);
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
            // Debounced network update while dragging
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
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            // Track
            graphics.fill(getX(), getY() + height / 2 - 2, getX() + width, getY() + height / 2 + 2, 0xFF3A2A1A);
            graphics.fill(getX() + 1, getY() + height / 2 - 1, getX() + width - 1, getY() + height / 2 + 1, 0xFF6B4E2E);

            // Filled portion
            int fill = (int) ((width - 8) * normalized());
            graphics.fill(getX() + 4, getY() + height / 2 - 1, getX() + 4 + fill, getY() + height / 2 + 1, 0xFFC9A227);

            // Knob
            int knobX = getX() + 4 + fill - 3;
            graphics.fill(knobX, getY() + 2, knobX + 6, getY() + height - 2, 0xFFE8D5A3);
            graphics.fill(knobX + 1, getY() + 3, knobX + 5, getY() + height - 3, 0xFF8B6914);

            // Label
            String text = getMessage().getString();
            graphics.drawString(Minecraft.getInstance().font, text, getX(), getY() - 10, 0xFFE8D5A3, false);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }
}
