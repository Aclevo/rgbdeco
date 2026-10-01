package com.mystic.rgbdeco.pattern;

/**
 * The four ways a directional {@link RgbPattern} can run across a face: left, right, up and down.
 *
 * <p>A direction here is a direction on the face the pattern is painted on rather than a compass point in
 * the world, which is what makes a pattern look the same whichever side of a panel it is being looked at.
 * A face carries its own {@code [0, 1]} texture space, {@code u} running to the right of somebody
 * standing outside it and {@code v} running downwards, and a direction is simply which way along that
 * space the pattern travels. There is no third dimension to be measured in, so nothing about a pattern
 * changes with the orientation of the block it is on: a run going right reads as movement to the right on
 * the front, the back, both sides and the top, instead of only on the faces that happen to be square to
 * the world axis it was pointed along.
 *
 * <p>That is also what makes a wall of panels behave. The direction is fixed in the pattern rather than
 * in the world, so every panel of a run is showing the same travel, and the run only decides how much of
 * the sweep each panel gets, which is what {@link com.mystic.rgbdeco.system.RgbRun} is for.
 */
public enum RgbDirection {
    /** Travels towards the left hand edge of the face, so it starts at the right of it. */
    LEFT(true, true),
    /** Travels towards the right hand edge of the face, starting at the left of it. */
    RIGHT(true, false),
    /** Travels towards the top of the face, so it starts at the bottom of it. */
    UP(false, true),
    /** Travels towards the bottom of the face, starting at the top of it. */
    DOWN(false, false);

    /** True for the two directions that run along the {@code u} of a face rather than down its {@code v}. */
    private final boolean horizontal;

    /** True for the two directions that count backwards along their axis, since {@code v} runs downwards. */
    private final boolean reversed;

    RgbDirection(boolean horizontal, boolean reversed) {
        this.horizontal = horizontal;
        this.reversed = reversed;
    }

    /** True for the two directions that run along the {@code u} of a face rather than down its {@code v}. */
    public boolean horizontal() {
        return this.horizontal;
    }

    /**
     * True for the two directions that count backwards along their axis, which is what makes left run
     * leftwards and up run upwards when {@code v} is counted from the top of the face downwards.
     */
    public boolean reversed() {
        return this.reversed;
    }

    /**
     * How far along this direction a point of a face sits, where {@code 0} is where the pattern starts
     * and {@code 1} is where it comes back round to.
     *
     * @param u texture coordinate across the face, {@code 0} at its left hand edge as seen from outside
     * @param v texture coordinate down the face, {@code 0} at its top edge as seen from outside
     */
    public double position(double u, double v) {
        double along = this.horizontal ? u : v;
        return this.reversed ? 1.0D - along : along;
    }
}
