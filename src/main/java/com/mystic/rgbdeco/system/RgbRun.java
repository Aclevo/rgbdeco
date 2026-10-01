package com.mystic.rgbdeco.system;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * The box a run of connected panels occupies, which is what the gradient patterns spread themselves
 * over, along with what is actually standing in it, which is what the shapes in the middle of a run
 * are measured from.
 *
 * <p>A run is not a line. It is whatever the panels showing one pattern have grown into, so the number
 * of panels in it says nothing about how far a pattern has to travel to cross it: nine panels laid
 * out as a square are three blocks across, and dividing the sweep by nine would squeeze a gradient
 * into a third of the wall it belongs on. The box is what a pattern is measured against instead, so a
 * run of any shape gets one sweep over the whole of it.
 *
 * <p>The two are the same for a straight run of panels, which is the only shape a run had before this
 * was kept. A wall is measured by how wide it is rather than by how many blocks are in it.
 *
 * <p>Each of the three axes is measured on its own, and a face is drawn from the two of its own
 * directions, so a run that is not a full box behaves like a wall: an L still has a middle left to right
 * and a middle top to bottom, and filling in a corner of it moves the pattern only along the axis that
 * corner was on.
 *
 * <p>What a run is asked here is always about one axis of the world at a time, because that is the only
 * thing a face can be measured along: a pattern running left or right on a face is running along
 * whichever world axis that face is turned, and a pattern running up or down is running along its
 * vertical. Which axis that is for any given face is worked out by the face's own texture space, so the
 * run never has to know what a pattern looks like to be measured.
 */
public record RgbRun(int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                     int sumX, int sumY, int sumZ, int count) {
    /**
     * The run a panel on its own belongs to, which is the block it stands in.
     */
    public static final RgbRun SINGLE = new RgbRun(0, 0, 0, 0, 0, 0, 0, 0, 0, 1);

    /**
     * No blocks at all, which is what a run is grown from.
     *
     * <p>It is not the same as {@link #SINGLE}. {@code SINGLE} is one block standing at the origin,
     * and growing from it would drag every run back to the origin with it, so a wall five blocks along
     * {@code x} at {@code x = 100} would measure a hundred and five blocks wide and put the middle of
     * its rings fifty blocks off the end of it.
     */
    public static final RgbRun EMPTY = new RgbRun(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

    /**
     * The run of the single block {@code pos}, which is what an empty run grows into first.
     */
    public static RgbRun at(BlockPos pos) {
        return new RgbRun(pos.getX(), pos.getY(), pos.getZ(),
                pos.getX(), pos.getY(), pos.getZ(),
                pos.getX(), pos.getY(), pos.getZ(), 1);
    }

    /**
     * The smallest box holding {@code pos}, which is how a run is grown as it is walked.
     *
     * <p>An empty run has no corners to grow from, so the first block it is given becomes all of it.
     * That is the whole difference between {@link #EMPTY} and {@link #SINGLE}: the box is measured
     * from the blocks actually standing in it, never from the world origin.
     */
    public RgbRun grow(BlockPos pos) {
        if (this.count == 0) {
            return RgbRun.at(pos);
        }
        return new RgbRun(
                Math.min(this.minX, pos.getX()), Math.min(this.minY, pos.getY()), Math.min(this.minZ, pos.getZ()),
                Math.max(this.maxX, pos.getX()), Math.max(this.maxY, pos.getY()), Math.max(this.maxZ, pos.getZ()),
                this.sumX + pos.getX(), this.sumY + pos.getY(), this.sumZ + pos.getZ(), this.count + 1);
    }

    /**
     * True for a run that has been walked and knows what is standing in it, as opposed to one that has
     * never held a block at all, which is {@link #EMPTY}.
     *
     * <p>A run that has never been measured is measured when it next loads, which is what stops a run
     * from being drawn as a panel on its own until something about it changes.
     *
     * <p>It is the count that says whether a run has been measured, not whether its box happens to
     * look like {@link #SINGLE}'s. A lone panel standing at the origin has a box identical to
     * {@code SINGLE} and a count of one, and asking it to be different from {@code SINGLE} meant such a
     * panel was never considered measured: it walked its run again on every chunk load for the life of
     * the world. The count is the thing that is actually known rather than guessed at, and a world
     * written before the box was kept carries no count at all.
     */
    public boolean measured() {
        return this.count > 0;
    }

    /**
     * Where the middle of the run is on one axis, in blocks from its low edge: half the run's own span.
     *
     * <p>It is measured on each axis on its own, so the middle along X answers only for X and the middle
     * along Y only for Y, and a face is drawn from the two of its own directions. That is what makes a
     * run that is an L behave like a wall: the panels that are missing along one axis say nothing about
     * where the pattern is along the other, so filling in a corner leaves the rings exactly where they
     * were, and a pattern only moves when there are panels further out along the axis it is being
     * measured on. Weighting it by how many panels stand in each column instead would drag the middle
     * sideways the moment a single panel was placed on the other axis, which is the same thing happening
     * on a wall.
     *
     * <p>It is the middle of the space the run fills rather than of the panels standing in it. On a run
     * that fills its box the two are the same number, so this costs nothing, and on a run that does not
     * the alternative has no stable answer to give.
     *
     * <p>Either end of the axis gives the same answer, because which end is the low one is decided by
     * the caller, which knows which way the face it is drawing is turned.
     */
    public double middle(Direction direction) {
        return this.span(direction) / 2D;
    }

    /**
     * The same middle, counted from the end a pattern running towards {@code travel} arrives at, which
     * is the end {@link #across(Direction, int)} counts blocks from.
     *
     * <p>Counting it the same way the blocks are counted is what keeps the middle of the run in the
     * same panel whichever way a face is turned: counted from the wrong end, the middle of a run comes
     * out mirrored, and a wheel centered on it turns about the wrong panel on half the faces.
     */
    public double middleCountedFrom(Direction travel) {
        return this.middle(travel);
    }

    /**
     * How many blocks the run is across on one axis, counting both ends.
     *
     * <p>It counts the blocks rather than measuring between the outside of the first and the outside of
     * the last, because it is what {@link #middleCountedFrom(Direction)} mirrors a middle against and what
     * a sweep divides by, and both of those need it to be the same length as the run they are reading: a
     * face's coordinate runs {@code 0} to the number of blocks in the run, so mirroring against anything
     * else puts the middle of the run off the end of the run. A run four blocks long mirrors against
     * four, not three and not six.
     */
    public double span(Direction direction) {
        return this.measured() ? this.max(direction) - this.min(direction) + 1 : 1D;
    }

    /**
     * The first block of the run along one direction, which is where a sweep along it starts.
     */
    public int min(Direction direction) {
        return switch (direction) {
            case NORTH, SOUTH -> this.minZ;
            case EAST, WEST -> this.minX;
            case UP, DOWN -> this.minY;
        };
    }

    /**
     * The last block of the run along one direction, which is the other end a sweep can start from.
     */
    public int max(Direction direction) {
        return switch (direction) {
            case NORTH, SOUTH -> this.maxZ;
            case EAST, WEST -> this.maxX;
            case UP, DOWN -> this.maxY;
        };
    }

    /**
     * Which block of the run {@code coordinate} stands in along the axis a pattern running towards
     * {@code travel} arrives along, counted from the end the pattern starts at: {@code 0} is the first
     * block of the run and {@code span - 1} the last.
     *
     * <p>It is a count of blocks and not a fraction of the run, because the caller divides it by the
     * length of the run once it has added the point's own place on its face. Answering here as a
     * fraction of the run would be counted a second time, and a gradient would only ever reach as far
     * as one block of a wall however long that wall is.
     *
     * <p>Which end is which is the whole point of taking a direction rather than an axis. Two panels
     * either side of a shared edge have to agree on what the sweep reads at that edge, and they only do
     * if the block they are standing in is counted in the direction the sweep is traveling. Measured the
     * other way round, the two ends of a two panel run both land in the middle of the sweep and the
     * gradient doubles back on itself along the join.
     *
     * <p>Counting towards the far end and not away from the one it starts at is what makes the pattern
     * point the way it was aimed. The two are the same measurement of how far along the run a block
     * sits, but only one of them agrees with which end the sweep finishes at: counted away, a pattern
     * aimed left comes out aimed right, and every distance on a run measured the other way is negative
     * besides.
     *
     * <p>A run one block along the axis has nowhere to travel, so that block counts as the first of the
     * run and shows the whole of it across its own face.
     */
    public int across(Direction travel, int coordinate) {
        return travel.getAxisDirection() == Direction.AxisDirection.POSITIVE
                ? coordinate - this.min(travel)
                : this.max(travel) - coordinate;
    }
}
