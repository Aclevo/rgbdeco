package com.mystic.rgbdeco.system;

import net.minecraft.core.BlockPos;

import java.util.Collections;
import java.util.List;

/**
 * What one controller reaches: the panels it can drive and how many controllers share them.
 *
 * <p>A system with more than one controller is not driven at all, because both of them would be
 * writing the same block states and neither would be in charge. A system with no panels has nothing
 * to drive, which is also what a controller with nothing attached to it shows.
 */
public final class RgbSystem {
    /** A controller that reaches nothing, which is what a scan that could not start returns. */
    public static final RgbSystem EMPTY = new RgbSystem(Collections.emptyList(), 0);

    private final List<BlockPos> panels;
    private final int controllers;

    public RgbSystem(List<BlockPos> panels, int controllers) {
        this.panels = panels;
        this.controllers = controllers;
    }

    /** How many panels the system holds. */
    public int panels() {
        return this.panels.size();
    }

    /** How many controllers share the system, the one that scanned it included. */
    public int controllers() {
        return this.controllers;
    }

    /** The panels themselves, in the order the walk reached them. */
    public List<BlockPos> panelPositions() {
        return this.panels;
    }

    /** True when the system has panels to drive and exactly one controller driving them. */
    public boolean drivable() {
        return !this.panels.isEmpty() && this.controllers == 1;
    }
}
