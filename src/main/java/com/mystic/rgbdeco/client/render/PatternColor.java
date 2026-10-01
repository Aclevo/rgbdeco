package com.mystic.rgbdeco.client.render;

import com.mystic.rgbdeco.pattern.RgbDirection;
import com.mystic.rgbdeco.pattern.RgbMotion;
import com.mystic.rgbdeco.pattern.RgbPattern;
import com.mystic.rgbdeco.system.RgbRun;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;

/**
 * The single answer to "what color is this pattern at this point of this face".
 *
 * <p>The world renderer and the swatches on a controller's screen both go through here, which is what
 * stops a swatch from showing a flat block of a color the panel never actually paints. Anything that
 * changes what a pattern looks like belongs in this class rather than in either caller.
 *
 * <p>Everything is measured in the texture space of the face it is being drawn on, so a pattern looks
 * the same on all six sides of a block. A face knows where its own left and right are, and a pattern
 * being pointed left, right, up or down is answered from that, rather than from a direction in the world
 * that only lines up with the faces a wall is painted on.
 *
 * <p>Every animated motion is measured against the whole run of connected panels rather than against
 * the one face it is being drawn on, which is what stops a wall from looking like the same block
 * repeated along it. A checkerboard covers the run as one checkerboard, a wheel of wedges turns round
 * the middle of the run, rings open out of it, and a scan bar on each of the face's two directions
 * crosses the run and comes back. The gradient motions do the same thing along
 * a single direction they carry with them, which is the one axis those patterns are aimed along.
 *
 * <p>A sparkle tiles rather than spreading. Its cells are counted over the whole run, so the grid carries
 * on from one panel into the next and there is no join down a wall where the twinkling stops and starts
 * again, but the color and the clock of a cell are mixed out of where that cell sits rather than read
 * off its number, so a panel shows the same twinkle whether it stands alone or in the middle of a wall.
 * Both halves of that are needed: counting from the face alone tiles nothing and never changes with the
 * wall, and counting from the run alone tiles but repaints every panel when a neighbor is placed.
 *
 * <p>The two motions that draw a shape rather than a sweep are centered on the middle of the run, and
 * each of the face's directions is taken out of the measurement in turn, so that a shape is the same
 * shape on a wall as it is on a panel. The wheel is divided against the run's own size along each of the
 * two directions separately, which is what keeps it whole instead of being flattened by the one axis the
 * run happens to be long in; the rings are counted in blocks from the same middle, because a ring that
 * is a fixed fraction of the run is a ring per block on a wall six blocks long, which is a gradient along
 * the wall rather than rings.
 *
 * <p>Both are measured in the plane of the face they are on, so a face the run runs away from, such as
 * the end of a wall, carries the middle of the pattern in the middle of itself: the middle of the run
 * is outside that face, and there is nowhere in it for the middle of a wheel to go.
 *
 * <p>A pattern running along a direction needs one number: the block it stands in counted from the end
 * the sweep starts at, plus the point's own place on its face, over the length of the run. A pattern
 * that fills its face in both directions, like a checkerboard, needs both of the face's axes, and is
 * measured in blocks from the low corner of the run so the cells of a run are one set of cells rather
 * than one set per block.
 *
 * <p>A face is also cut into the mesh its pattern asks for, and a shape only reads on a mesh that has
 * the cells to hold it. A wheel of eight wedges or a set of rings crossing the whole spectrum are not
 * things eight quads a side can carry, so those patterns ask for more of the face than a gradient does.
 * The surface grid, which dims alternate cells to make a flat panel look lit, is left off a pattern that
 * has cut the face up itself: laid over a wheel it cuts every wedge in half, and laid over a set of
 * rings it cuts every ring in half.
 *
 * <p>A swatch has no world to sit in and passes a null position and a null run, which leaves a panel on
 * its own: still the right shape, just measured over a single block rather than over a wall.
 */
@OnlyIn(Dist.CLIENT)
public final class PatternColor {
    /** Cells a face is split into by the surface grid. */
    public static final int GRID_CELLS = 4;
    /** How much the darker cells of the surface grid are dimmed. */
    public static final double GRID_DARKEN = 0.86D;

    /** How many bands a set of bands puts across a run, so wide bands stay wide on a short wall. */
    private static final double BAND_COUNT = 3.0D;
    /** How many waves a wave puts across a run. */
    private static final double WAVE_COUNT = 1.5D;
    /**
     * How many wedges the spinning wheel is cut into, counted before the turn is added to it.
     *
     * <p>A wheel painted as one continuous sweep of the spectrum has nothing to hold on to: every
     * point of it is blended into its neighbors, so it turns without ever looking as though it
     * does. Cutting it into a handful of wedges puts an edge between neighboring colors, and an
     * edge traveling round the middle of a run is exactly what a wheel turning looks like.
     */
    private static final double WHEEL_WEDGES = 8.0D;
    /**
     * How much of the spectrum the rings cross between the middle of a run and one block out from it.
     *
     * <p>One is a whole rainbow over a block, which is about eight rings from the middle of a panel
     * out to its far corner. Any more than that packs the rings closer together than the mesh they
     * are drawn on can carry, and they stop being rings and become a blur.
     */
    private static final double RING_SWEEPS = 1.0D;
    /** How fast the scan bars travel, in blocks a second, and so how long a run takes to cross. */
    private static final double SCAN_SPEED = 0.25D;
    /** How quickly the light falls off either side of a scan bar, over blocks. */
    private static final double SCAN_BAR = 25.0D;

    private static final double TAU = Math.PI * 2.0D;

    private PatternColor() {
    }

    /**
     * Color of a face at a point in its texture space, where {@code [0, 0]} is the top left corner of
     * the face as it is seen from outside and {@code [1, 1]} the bottom right.
     *
     * @param pos  position of the block, {@code null} for a preview that has no world
     * @param time seconds the animation has been running
     * @param run  box of the run the pattern is spread over, {@code null} for a single block
     */
    public static int at(RgbPattern pattern, Direction face, double u, double v, @Nullable BlockPos pos, float time, @Nullable RgbRun run) {
        double shade = paintsItsOwnCells(pattern) ? 1.0D : gridShade(u, v);
        if (!pattern.isAnimated()) {
            return scale(pattern.flatColor() | 0xFF000000, shade);
        }
        // Where this point sits on the sweep, over the whole run rather than over this one face, and how
        // bright it comes out. Every motion writes both, the ones that light up only part of a face
        // overriding the default envelope.
        double sweep = pattern.isGradient() ? sweep(pattern.runAlong(), face, u, v, pos, run) : 0.0D;
        // The same run measured in both of the face's axes rather than along the one the pattern runs,
        // which is what the motions that fill their face are drawn from.
        RunSpace space = runSpace(face, u, v, pos, run);
        double position = 0.0D;
        RgbMotion motion = pattern.motion();
        double brightness = motion.brightnessAt(time);
        switch (motion) {
            case FLOW -> position = sweep - time * 0.15D;
            case STILL -> position = sweep;
            case BANDS -> position = sweep * BAND_COUNT - time * 0.5D;
            case WAVE -> position = (Math.sin(sweep * TAU * WAVE_COUNT - time * 1.2D) + 1.0D) * 0.5D;
            // The cells are the run's, not the block's, so a checkerboard runs on over a wall as one
            // board with the grid it would have had on a single panel.
            case CHECKER -> position = ((space.cellU() + space.cellV() + (int) (time * 1.0D)) % 3) / 3.0D;
// Cut into wedges and measured against the run's own box, so the wheel turns round the
            // middle of the run and is whole in both of the face's directions rather than being a
            // slice of one spread along it. A wheel has no size of its own, so every face it is on
            // shows one turned about where the middle of the run falls in that face, and no face needs
            // to know how far the run is from it to say so.
            case SPIN -> position = wedge(space.wheelAngle()) + time * 0.12D;
            // Rings opening out of the middle of the run and counted in blocks across the face, so that
            // they reach out to the corners of it whichever shape the run is, and stay rings rather
            // than becoming a gradient along a wall. A ring is measured in the plane of the face it is
            // on and nothing else: the middle of the run falls inside a face where the run runs across
            // that face, and on a face the run runs away from, such as the end of a wall, it falls
            // outside it, and that face then carries the middle of the pattern in the middle of itself.
            case RINGS -> position = radius(space.centerU(), space.centerV()) * RING_SWEEPS - time * 0.3D;
case CROSS, SCAN_HORIZONTAL, SCAN_VERTICAL -> {
                // A bar on each of the face's two directions, and each bar turns at the two edges of the
                // run in the direction it is traveling along, so every face of a run has a bar
                // bouncing at all four of its edges rather than one bar bouncing at two of them: a
                // square run used to pick a direction to sweep in and the other two edges of it were
                // never anything but the ends of the wall.
                //
                // Both bars travel the same distance in blocks rather than the same fraction of the
                // run, so they cross the face on the diagonal instead of the short one dashing about
                // while the long one crawls, and both are drawn where they are brightest.
                //
                // The two single bar motions are that same bar on one direction only. Which direction
                // each of them takes is the face's own, so a bar that sweeps sideways on the wall stays
                // sideways whichever way the wall is turned, and the vertical one runs down the face
                // rather than down the world.
                double travelled = time * SCAN_SPEED;
                double across = space.u - bounce(travelled, space.spanU);
                double down = space.v - bounce(travelled, space.spanV);
                boolean barOnU = motion != RgbMotion.SCAN_VERTICAL;
                boolean barOnV = motion != RgbMotion.SCAN_HORIZONTAL;
                position = time * 0.05D;
                brightness = Math.max(barOnU ? Math.exp(-across * across * SCAN_BAR) : 0.0D,
                        barOnV ? Math.exp(-down * down * SCAN_BAR) : 0.0D);
            }
            case SPARKLE -> {
                // Which cell of the run this point is in, so a cell carries on into the block next door
                // and the grid tiles across a join instead of stopping at every block edge. Taking the
                // cell from the face instead put a hard join down every block, because the last cell of
                // one panel and the first cell of the next are different cells and a twinkle stops dead
                // between them.
                //
                // The color and the clock are then mixed out of that cell rather than being the cell
                // number itself, which is what keeps a sparkle looking the same wherever it stands. The
                // cell number depends on how wide the run is, so a wall four blocks across numbers its
                // cells differently from a single panel and a run that was showing one twinkle would show
                // another the moment a neighbor was put next to it. Mixing spreads those numbers over
                // the sixteen colors instead of naming them, so the grid still lines up with itself and
                // the panel is the panel whether it stands alone or in the middle of a wall.
                int mixed = mix(space.cellU(), space.cellV());
                double blink = (time * 0.9D + (mixed >>> 4 & 31) * 0.37D) % 1.0D;
                position = (mixed & 15) / 16.0D;
                brightness = 0.2D + 0.8D * Math.max(0.0D, Math.sin(blink * TAU));
            }
            case PULSE -> position = time * 0.05D;
            case NONE -> {
            }
        }
        return pattern.animatedColor(position, brightness * shade);
    }

    /** Scales the color channels of a vertex color, leaving the alpha alone. */
    public static int scale(int color, double factor) {
        int r = Mth.clamp((int) ((color >> 16 & 0xFF) * factor + 0.5D), 0, 255);
        int g = Mth.clamp((int) ((color >> 8 & 0xFF) * factor + 0.5D), 0, 255);
        int b = Mth.clamp((int) ((color & 0xFF) * factor + 0.5D), 0, 255);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /**
     * Which of the wheel's wedges a point is in, as a fraction of a turn.
     *
     * <p>It is counted before the turn is added to the position rather than after, which is the
     * whole of the difference between a wheel that spins and a wheel that jumps: counting it first
     * leaves the edges between wedges where they are and lets the turn carry them round the middle
     * of the face smoothly, where counting afterward would move every wedge at once and show only
     * the eight colors taking turns with each other.
     */
    private static double wedge(double angle) {
        double wedges = angle * WHEEL_WEDGES;
        return ((wedges % WHEEL_WEDGES) + WHEEL_WEDGES) % WHEEL_WEDGES / WHEEL_WEDGES;
    }

    /**
     * Where the bar on a run {@code reach} blocks long is, having travelled {@code travelled} blocks,
     * counted from the run's near edge.
     *
     * <p>It bounces rather than wrapping, so a bar goes to the far end of the run and comes back
     * instead of stepping off one end and appearing at the other, which would leave the run dark for
     * as long as the crossing took.
     *
     * <p>It turns at the middle of the two end blocks rather than at the edges of them. The run's
     * edges are the outside of the first and last panel, and a bar sitting on one of them has half of
     * its light off the structure entirely, which reads as the scan spilling over the ends of the run
     * every time it turned there. Half a block in is as far as it can go and still span every block
     * of the run, since a bar at the middle of the first block is the closest it can get to that
     * block's near edge without being outside it.
     *
     * <p>A run one block long is both its own first and its own last block, so the two ends it turns at
     * are the same half block in and the bar has nowhere left to travel: it sits across the middle of
     * the panel. Sending it back out to the edges of the face for that one case would put back exactly
     * what the inset is there to stop, and a panel standing on its own is the run a swatch is drawn as,
     * so it would show the spill on every controller screen.
     */
    private static double bounce(double travelled, double span) {
        double near = 0.5D;
        double leg = span - 1.0D;
        return leg > 0.0D ? near + leg - Math.abs(travelled % (leg * 2.0D) - leg) : near;
    }

    /**
     * True for the motions that break a face into pieces of their own, where the surface grid would
     * only dim half of them.
     *
     * <p>A checker and a sparkle are cut into cells, so the grid would be dimming a checkerboard
     * with another checkerboard on top of it. A wheel and a set of rings are cut up just as
     * thoroughly, and a grid laid over them does not read as a grid at all: it cuts a ring in two
     * down its middle and leaves half of every ring lit, which is what turned a run of rings into a
     * run of stained-glass.
     */
    private static boolean paintsItsOwnCells(RgbPattern pattern) {
        return switch (pattern.motion()) {
            case CHECKER, SPARKLE, SPIN, RINGS -> true;
            default -> false;
        };
    }

    /**
     * Dims every other cell of a checkerboard, so a panel reads as a lit grid of cells instead of a
     * flat color. The cells are pinned to the face itself rather than to the texture, so every panel of
     * a run shows the very same grid along the whole of it, whatever color it happens to be showing.
     */
    private static double gridShade(double u, double v) {
        return ((cell(u) + cell(v)) & 1) == 0 ? 1.0D : GRID_DARKEN;
    }

    /**
     * Which cell of the four by four grid a texture coordinate falls in, counted from one corner. The
     * last edge of a face belongs to the last cell rather than to a fifth one that is not there, which
     * is what stops a seam running down the right hand and bottom of every face.
     */
    private static int cell(double coordinate) {
        return (int) Math.min(Math.floor(coordinate * GRID_CELLS), GRID_CELLS - 1.0D);
    }

    /**
     * A point of a face measured against the whole run it belongs to instead of against its own block.
     *
     * <p>Both coordinates are in blocks rather than in fractions, counting from the low corner of the
     * run, so they run from {@code 0} at that corner to {@code span} at the far one and are continuous
     * across a join between two panels: the far edge of one is the near edge of the next. The two spans
     * are the run's own size along the face's axes, which is what a pattern that is measured in both
     * directions needs to know how big the thing it is filling actually is.
 *
 * <p>The two motions that draw a shape rather than a sweep are the exception: a wheel has no size of
 * its own and a ring opens out of the middle of the panel it is on, so both are measured on the face
 * alone and take nothing from the run. Spreading either of them along a run puts the middle of the
 * pattern in the middle of the run, which on anything but a single panel is a place most of the panels
 * of that run are nowhere near.
     *
     * <p>Counting from the low corner rather than from whichever end the face happens to be turned to
     * is deliberate. A wall is one picture, and a checkerboard that came out the other way round on the
     * other side of it would not be the same picture seen from another angle.
     *
     * <p>A panel on its own is the case of a run one block across, which is also what a swatch is, so
     * the motions that are drawn from this come out of a swatch exactly as they do out of a block
     * standing on its own.
     */
    private static final class RunSpace {
        /**
         * The one instance every measurement is read through.
         *
         * <p>It used to be an immutable record built fresh for each vertex, which is a few thousand
         * allocations a frame for a wall of panels and buys nothing: every field is read once, within
         * the call that filled it in, and nothing holds on to a measurement after the vertex it was
         * taken for has been colored. The renderer is single threaded, so one reused set of fields is
         * all it takes.
         */
        private static final RunSpace SHARED = new RunSpace();

        private double u;
        private double v;
        private double spanU = 1D;
        private double spanV = 1D;
        private double middleU = 0.5D;
        private double middleV = 0.5D;

        private RunSpace() {
        }

        /** A run one block across, which is a panel standing on its own. */
        private static RunSpace of(double u, double v) {
            return SHARED.at(u, v, 1D, 1D, 0.5D, 0.5D);
        }

        private RunSpace at(double u, double v, double spanU, double spanV, double middleU, double middleV) {
            this.u = u;
            this.v = v;
            this.spanU = spanU;
            this.spanV = spanV;
            this.middleU = middleU;
            this.middleV = middleV;
            return this;
        }

        /**
         * Which cell of the run's grid this point is in, counting across it.
         *
         * <p>Not pinned to an edge the way a single face is, because the cells of a run carry on into
         * the next block: the far edge of one panel is the near edge of the next, and both have to be
         * counted as the same place for the two to agree about it. What is pinned is the far edge of the
         * run, where there is no next block and a cell beyond the last one would smear into it.
         */
private int cellU() {
            return (int) Math.min(Math.floor(this.u * GRID_CELLS), GRID_CELLS * this.spanU - 1.0D);
        }

        /** Which cell of the run's grid this point is in, counting down it. */
        private int cellV() {
            return (int) Math.min(Math.floor(this.v * GRID_CELLS), GRID_CELLS * this.spanV - 1.0D);
        }

        /**
         * How many blocks from the middle of the run this point is, counted across it.
         *
         * <p>From the middle of the panels in the run, which the run is asked for in the same direction
         * the blocks are counted in, and not from the middle of the box they fit in. The two are the same
         * on a run that fills its box and somewhere else on any run that does not, and on an L or a
         * cross the middle of the box is a corner of the run that has no panels on it at all, so a
         * wheel or a set of rings centered on it is centered on nothing.
         */
        private double centerU() {
            return this.u - this.middleU;
        }

        /** How many blocks from the middle of the run this point is, counted down it. */
        private double centerV() {
            return this.v - this.middleV;
        }

        /**
         * How far round the middle of the run this point sits, as a fraction of a turn, with each of
         * the face's directions taken out of it in turn against its own share of the run.
         *
         * <p>Measuring each direction against the run's own size along it is what keeps the wheel the
         * same shape on a wall as it is on a panel, and what fits it to the run: divided out this way,
         * a wheel is as wide as the run and as tall as one block of it whichever way the run is
         * turned, so the whole of it is on show, and it reaches the two ends of the run exactly rather
         * than being flattened into bands by the one axis the run is long in.
         *
         * <p>The middle it turns round is the middle of the run itself, counted in each direction on
         * its own. On a run of an odd number of panels that is the middle of the panel in the middle of
         * it, and on a run of an even number it falls on the join between two of them, which is the
         * middle of the construction and the only place it can be: nothing is drawn along a join
         * between two panels of one run, so a wheel centered there is not cut in two by it.
         */
        private double wheelAngle() {
            return Math.atan2(this.centerV() / this.spanV, this.centerU() / this.spanU) / TAU;
        }
    }

    /**
     * The point being drawn measured against the run around it, in both of the face's axes.
     *
     * <p>Which world axis each of them is comes from the face rather than guessed, because a run that
     * goes along {@code x} on one face of a panel is a run going along {@code z} on another, and a
     * pattern that measured itself against a fixed pair of axes would run along the wall on some faces
     * and across it on the rest.
     */
private static RunSpace runSpace(Direction face, double u, double v,
                                      @Nullable BlockPos pos, @Nullable RgbRun run) {
        if (run == null || pos == null) {
            return RunSpace.of(u, v);
        }
        Base base = Base.of(face, pos, run);
        return RunSpace.SHARED.at(base.acrossU + u, base.acrossV + v, base.spanU, base.spanV, base.middleU, base.middleV);
    }

    /**
     * Everything about the run that is the same for every vertex of one face.
     *
     * <p>Which way a face's two directions run through the world, where the block stands in the run on
     * each of them, and how big the run is along each, are all fixed for as long as the renderer is on
     * one block and one face. They used to be worked out afresh for each vertex, which is four times
     * per quad and a thousand quads a face, and each of those answers cost a switch on the direction, a
     * switch on the axis, a subtraction and a couple of divisions. The four vertices of a quad land on
     * the same block of the same run, so the answer is held until it is asked for differently.
     */
    private static final class Base {
        private static final Base SHARED = new Base();

        private Direction face;
        private BlockPos pos;
        private RgbRun run;
        private double acrossU;
        private double acrossV;
        private double spanU = 1D;
        private double spanV = 1D;
        private double middleU = 0.5D;
        private double middleV = 0.5D;

        private Base() {
        }

        private static Base of(Direction face, BlockPos pos, RgbRun run) {
            Base base = SHARED;
            if (base.face == face && base.pos == pos && base.run == run) {
                return base;
            }
            Direction uDir = ColoredBoxRenderer.uAxis(face);
            Direction vDir = ColoredBoxRenderer.vAxis(face);
            base.face = face;
            base.pos = pos;
            base.run = run;
            base.acrossU = run.across(uDir, coordinate(pos, uDir.getAxis()));
            base.acrossV = run.across(vDir, coordinate(pos, vDir.getAxis()));
            base.spanU = run.span(uDir);
            base.spanV = run.span(vDir);
            base.middleU = run.middleCountedFrom(uDir);
            base.middleV = run.middleCountedFrom(vDir);
            return base;
        }
    }

    /** The value one of the world axes has at a block, which is what a run is measured against. */
    private static int coordinate(BlockPos pos, Direction.Axis axis) {
        return switch (axis) {
            case X -> pos.getX();
            case Y -> pos.getY();
            case Z -> pos.getZ();
        };
    }

    /**
     * Where a point of a face sits on a sweep taken over the whole run of connected panels, where
     * {@code 0} is the end of the run the pattern starts from and {@code 1} the end it finishes at.
     *
     * <p>Two things are being added together here. The part of the run the block itself stands in, which
     * is measured along the world axis the pattern happens to be traveling over, and the point's own
     * place on its face. The axis is worked out from the face rather than guessed, because which way a
     * face's {@code u} runs through the world depends on which way the face is turned, and a pattern
     * that only read correctly on the faces a wall is painted on would be no use on the rest of the
     * block.
     *
     * <p>Adding them in that order and dividing by the length of the run is what makes two panels either
     * side of a join agree on what the sweep reads at it: they are the same point of the same run, so
     * they have to come out the same.
     */
    private static double sweep(RgbDirection direction, Direction face, double u, double v,
                                 @Nullable BlockPos pos, @Nullable RgbRun run) {
        double along = direction.position(u, v);
        if (run == null || pos == null) {
            return along;
        }
        Direction travel = direction.horizontal() ? ColoredBoxRenderer.uAxis(face) : ColoredBoxRenderer.vAxis(face);
        if (direction.reversed()) {
            travel = travel.getOpposite();
        }
        return (run.across(travel, coordinate(pos, travel.getAxis())) + along) / run.span(travel);
    }

    /**
 * Mixes a cell of the run's grid into one number, for a sparkle to take its color and its clock from.
 *
 * <p>The low four bits are its place among the sixteen colors the sweep runs through and the next five
 * are its clock, so one number carries both and a cell cannot be given a color without also being
 * given a blink. Two large odd multipliers are what scatter the cell numbers over those bits, which is
 * the whole point: cell numbers run in steps along a run, and taken straight they would hand a long
 * wall sixteen colors in sixteen long unbroken stripes rather than a scatter of them.
 */
private static int mix(int across, int down) {
        return (across * 0x9E3779B1 ^ down * 0x85EBCA77) & 0x7FFFFFFF;
    }

    /** How far a point of a face sits from the middle of it, which is what the rings measure. */
    private static double radius(double du, double dv) {
        return Math.sqrt(du * du + dv * dv);
    }
}
