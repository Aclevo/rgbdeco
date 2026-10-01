package com.mystic.rgbdeco.system;

import net.minecraft.core.BlockPos;

import java.util.HashSet;

/**
 * A guard that stops one refresh starting another refresh of the same thing.
 *
 * <p>Putting a panel in or out is a block state change, and Minecraft calls {@code onPlace} for a block
 * state change whatever flags it was made with. So writing the light of a panel walks straight back into
 * {@code onPlace}, which measures the run around that panel, which asks any controller it can reach to
 * measure its system, which writes the light of every panel of it — including the panel the walk started
 * from. That cycle is fine while each pass settles everything in one go, and is a stack overflow the
 * moment a pass leaves something else to settle.
 *
 * <p>A uniform answer settles itself: every panel of a system gets the same value, the second pass finds
 * nothing left to write, and the cycle ends after one bounce. A per panel answer does not: on a wall where
 * one panel is charged and its neighbor is not, each pass has another panel to write and another walk to
 * be started from, so the depth grows with the length of the wall rather than with anything the code can
 * see. {@link #tryBegin} is what stops that, by refusing a refresh of something already being refreshed
 * on this thread: the pass already running covers the whole system or the whole run, so anything nested
 * inside it is work that pass is about to do anyway.
 *
 * <p>One set per kind of thing, because the two kinds are not the same thing and either can be the one
 * that repeats. A run and the system it sits in can each be asked for while that same one is in progress,
 * and a guard that treated them as one would refuse work that is not repeated.
 *
 * <p>Held per thread rather than statically, because a block update is only ever re-entered on the thread
 * that started it, and a shared set would let one thread's refresh refuse another's.
 */
public final class RgbRefresh {
    private static final ThreadLocal<HashSet<BlockPos>> RUNS = ThreadLocal.withInitial(HashSet::new);
    private static final ThreadLocal<HashSet<BlockPos>> SYSTEMS = ThreadLocal.withInitial(HashSet::new);

    private RgbRefresh() {
    }

    /**
     * Claims a run to be measured, or answers false when this thread is already measuring it.
     *
     * <p>Every answer true has to be given back with {@link #endRun}, which is why this is not something
     * to call and forget: a run left claimed would never be measured again for the life of the thread.
     */
    public static boolean tryBeginRun(BlockPos pos) {
        return RUNS.get().add(pos.immutable());
    }

    /** Gives a run back, whether or not the measurement that claimed it got as far as doing anything. */
    public static void endRun(BlockPos pos) {
        RUNS.get().remove(pos);
    }

    /**
     * Claims a controller's system to be measured, or answers false when this thread is already measuring
     * it. Answering false leaves the counts on the block entity as they were, which is the answer the
     * pass already running is in the middle of correcting.
     */
    public static boolean tryBeginSystem(BlockPos pos) {
        return SYSTEMS.get().add(pos.immutable());
    }

    /** Gives a system back, whether or not the measurement that claimed it got as far as doing anything. */
    public static void endSystem(BlockPos pos) {
        SYSTEMS.get().remove(pos);
    }

    /** Whether anything at all is being measured on this thread, for a caller that wants to know. */
    public static boolean busy() {
        return !RUNS.get().isEmpty() || !SYSTEMS.get().isEmpty();
    }

    /**
     * Whether a run is already being measured on this thread, whichever one it is.
     *
     * <p>This is the one a caller has to ask before starting a run of its own, because claiming by
     * position alone does not bound the depth. Writing the light of a run is a block change, a block
     * change calls {@code onPlace}, and {@code onPlace} starts a run measurement again: a pass standing
     * on one panel lights another, and the light of that one starts a pass standing on it. Each of those
     * passes claims a position of its own, so none of them is refused and the stack grows by one frame
     * per panel of the wall until it overflows.
     *
     * <p>Nothing is lost by refusing the nested pass. The pass already running told every panel of its
     * run what the run is before it wrote any light, and lighting a panel does not change the shape of
     * the run around it, so the measurement the nested pass would have made is the one already stored.
     */
    public static boolean runInProgress() {
        return !RUNS.get().isEmpty();
    }
}