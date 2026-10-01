package com.mystic.rgbdeco.pattern;

/**
 * How an animated {@link RgbPattern} moves across a face. A rainbow pattern and its monochromatic
 * twin share one motion, which is what makes them step along together.
 */
public enum RgbMotion {
    /** The solid patterns do not move. */
    NONE,
    /** A flow across the face, scrolling over time. */
    FLOW,
    /** The same flow, with the scrolling taken out. */
    STILL,
    /** Wide bands that scroll along the sweep, like a color gradient wall. */
    BANDS,
    /** Waves running along the sweep, rising and falling as they scroll. */
    WAVE,
    /** A checkerboard of cells over the whole run, flipping as it scrolls. */
    CHECKER,
    /** A wheel of wedges turning round the middle of the run. */
    SPIN,
    /** Rings expanding out of the middle of the run. */
    RINGS,
    /** A bar on each of the face's two directions at once, which reads as a cross moving over the run. */
    CROSS,
    /** A single bar sweeping left to right across the run, over a color that keeps cycling. */
    SCAN_HORIZONTAL,
    /** A single bar sweeping top to bottom across the run. */
    SCAN_VERTICAL,
    /** Cells twinkling across the run, each on its own clock. */
    SPARKLE,
    /** One value for the whole run, cycling and pulsing in brightness. */
    PULSE;

    /**
     * True for the motions that are aimed along a direction, which are the ones that take an
     * {@link RgbDirection} to run along. Aimed along a direction means measured along one axis of the
     * run, so this is about which way a pattern is pointed and not about how large it is drawn: every
     * animated motion is measured over the whole run rather than over the one panel it is on, whether it
     * is aimed along a direction or fills the run in both of its axes.
     */
    public boolean isGradient() {
        return this == FLOW || this == STILL || this == BANDS || this == WAVE;
    }

    /**
     * The brightness this motion fades to as it runs, which every motion but the pulse leaves at
     * full. The scanning motions and the sparkle light up only part of a run, so the renderer gives
     * those a brightness of their own.
     */
    public double brightnessAt(double time) {
        return this == PULSE ? 0.55D + 0.45D * Math.sin(time * 1.5D) : 1.0D;
    }
}
