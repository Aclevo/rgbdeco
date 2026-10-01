package com.mystic.rgbdeco.blockentity;

import com.mystic.rgbdeco.RgbDeco;
import com.mystic.rgbdeco.block.RgbBlock;
import com.mystic.rgbdeco.energy.RgbEnergyStorage;
import com.mystic.rgbdeco.registry.RgbBlockEntityTypes;
import com.mystic.rgbdeco.system.RgbRun;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Carries the one number of shape a panel cannot work out for itself: the box of the run it belongs to.
 * The color lives in the block state and the frame is worked out by the renderer from the world, but the
 * gradient patterns spread themselves over the run, so the renderer has to be told how far the run
 * reaches.
 *
 * <p>What is stored is the box rather than a count of panels, because a run is not a line. Nine panels
 * in a square are three blocks across, and scaling a gradient by nine would put a third of a sweep
 * into a third of the wall.
 *
 * <p>A panel also holds its own energy. The controller pays for the system and hands out one unit per
 * panel per second, and a panel spends what it has been given for as long as it is lit. That is what
 * makes the light of a wall depend on more than the one block at the end of it: the panels nearest the
 * controller are handed energy first and hold their light, and a wall longer than the energy coming in
 * fades along its length rather than every panel going out at the same moment the buffer runs dry.
 */
public class RgbBlockEntity extends BlockEntity {
    private static final String MIN_X = "min_x";
    private static final String MIN_Y = "min_y";
    private static final String MIN_Z = "min_z";
    private static final String MAX_X = "max_x";
    private static final String MAX_Y = "max_y";
    private static final String MAX_Z = "max_z";
    private static final String SUM_X = "sum_x";
    private static final String SUM_Y = "sum_y";
    private static final String SUM_Z = "sum_z";
    private static final String COUNT = "count";
    private static final String VERSION = "version";
    private static final String ENERGY = "energy";
    /**
     * Which shape of stored run this mod writes.
     *
     * <p>One is a run measured from the world origin rather than from the panels standing in it, so a
     * run saved by it carries a box with the origin as one of its corners: a wall at {@code y = 68} was
     * saved as starting at {@code y = 0}. Nothing about such a run looks broken to the code reading it
     * back, and it is never measured again because it counts itself as measured, so a world that had
     * one written into it kept drawing its rings and its gradients against that box for good.
     *
     * <p>A saved run older than this is therefore thrown away and measured again when the panel next
     * loads, which costs one walk per panel once and then stops.
     */
    private static final int RUN_VERSION = 2;

    /**
     * The box of the run this panel is drawn as part of.
     *
     * <p>It starts empty rather than as a single panel, which is what leaves it unmeasured: a panel
     * knows nothing about the panels around it until something measures it, and a run that had already
     * been counted on the way in would never find out.
     */
    private RgbRun run = RgbRun.EMPTY;
    /** The energy this panel is holding, handed to it by the controller and spent while it is lit. */
    private final RgbEnergyStorage energy = new RgbEnergyStorage(CAPACITY, MAX_RECEIVE);
    private final LazyOptional<IEnergyStorage> energyHolder = LazyOptional.of(() -> this.energy);
    /** Counts the ticks between two draws, so the draw is a rate rather than a per tick cost. */
    private int countdown;

    public RgbBlockEntity(BlockPos pos, BlockState state) {
        super(RgbBlockEntityTypes.RGB_BLOCK_ENTITY.get(), pos, state);
    }

    /**
     * What a panel holds, which is a small buffer rather than nothing.
     *
     * <p>One second of a panel's light is one unit, so the buffer is a couple of minutes of a panel
     * running on what it was last handed. That is what stops the light of a wall flickering panel by
     * panel every time the controller's second draw comes round, and it is small enough that a panel
     * at the far end of a long wall runs out before the one beside the controller does.
     */
public static final int CAPACITY = 160;
    /** What a cable can push into a panel in one go, which is above the share a controller hands out. */
    public static final int MAX_RECEIVE = 16;
    /**
     * How many draws in a row a panel with nothing in it keeps its light for.
     *
     * <p>One covers the gap between a panel spending the last unit it was given and the controller handing
     * it the next one, which is a whole draw apart on a wall spending everything it has. Two would hide a
     * flicker that lasted longer than that, and a panel the controller has actually stopped paying for
     * still goes out inside a second and a half.
     */
    private static final int DRAW_GRACE = 1;

    /** The run this panel belongs to, which is what the patterns scale themselves to. */
public RgbRun run() {
        return this.run;
    }

    /** True while this panel has energy of its own to keep its light on. */
    public boolean charged() {
        return this.energy.getEnergyStored() > 0;
    }

    /**
     * True while this panel should be showing its light, which is what the controller writes onto the
     * block state.
     *
     * <p>A panel is handed what it spends in a second, so a driven panel sits at one unit and drops to
     * nothing the instant it has spent it. Judged on the buffer alone, that is a wall that blinks: any
     * measurement that re-decides the light without handing out energy first — placing a panel, opening
     * a screen — finds half the wall empty and puts it out, and the wall comes back on the next draw.
     * So a panel that has only just run out keeps its light for {@link #DRAW_GRACE} draws more, which
     * covers the gap between two shares and still lets a wall the controller has stopped paying for go
     * dark.
     *
     * <p>Counted to the same {@link #DRAW_GRACE} as {@link #serverTick} puts a panel out, because the
     * two answers have to be the same number: a controller one draw stricter than the panel itself takes
     * a panel's light away while the panel is still holding it, which is a wall blinking for a second on
     * and off every time a panel is placed on it.
     */
    public boolean showing() {
        return this.charged() || this.starved <= DRAW_GRACE;
    }

    /**
     * Counts the draws a panel has spent nothing on, which is what {@link #showing()} is counted from.
     *
     * <p>Not saved. It is what has happened to this panel since it was last looked at rather than
     * anything about it, and a panel read back off disk has spent nothing yet.
     */
private int starved;

    /**
     * Takes a share of a controller's energy, which is the only way a panel is ever filled, and answers
     * what it actually took.
     *
     * <p>Answering rather than just taking is what lets a controller hand out a fixed amount of energy
     * down a wall that is longer than that: the panels nearest it take the units offered and the rest
     * are offered none, so the wall is lit as far as the charge reaches rather than all the way or not
     * at all.
     */
    public int receiveShare(int amount) {
        int before = this.energy.getEnergyStored();
        this.energy.receiveEnergy(amount, false);
        int taken = this.energy.getEnergyStored() - before;
        if (taken > 0) {
            // Being paid for is what clears the count, and it has to clear it here rather than being left
            // for the next draw: a panel that was one empty draw short of going out is filled and lit in
            // the same pass, and one that was left holding the count would put itself out a draw later
            // even though it is being paid for.
            this.starved = 0;
        }
return taken;
    }

    /**
     * Spends what this panel is holding, and puts the panel out once it has nothing left to spend and
     * has been given nothing to replace it.
     *
     * <p>Asked once a second rather than every tick, to match the controller's draw: a panel spending
     * every tick would empty a buffer meant for a couple of minutes in a couple of minutes of ticks and
     * would put a block change on every panel of a wall at the same moment, which is the flicker this
     * buffer exists to stop.
     *
     * <p>A panel that runs out goes out through {@link RgbBlock#light} rather than by reaching round the
     * system for a controller to do it, so a wall whose energy has run out is not left half lit waiting
     * on the next time somebody happens to walk it. It takes {@link #DRAW_GRACE} empty draws rather than
     * the first, because a driven panel spends the unit it was just given before the controller hands it
     * the next one, and a wall that went out in that gap would be dark for the whole of every second.
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state, RgbBlockEntity panel) {
        if (--panel.countdown > 0) {
            return;
        }
        panel.countdown = RgbControllerBlockEntity.TICK_PERIOD;
        if (!panel.charged()) {
            panel.starved++;
            // The only reason to touch the block at all: a panel put down by a player with nothing behind
            // it should not stand there lit, and one the controller has stopped paying for should not
            // either. The panel right next to the controller spends the unit it was given one draw before
            // the next one arrives, which is what the grace above is for.
            if (panel.starved > DRAW_GRACE) {
                RgbBlock.light(level, List.of(pos), false);
            }
            return;
        }
        panel.energy.spend(1);
        panel.setChanged();
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        this.energyHolder.invalidate();
    }

    /**
     * The buffer, offered on every side. A panel has no front, and a cable run along a wall reaches
     * each panel on whichever side the cable happens to be against.
*/
    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        return cap == ForgeCapabilities.ENERGY ? this.energyHolder.cast() : super.getCapability(cap, side);
    }

    /**
     * Stores the box of the run and sends it to the players who can see it, so a gradient is spread
     * over the whole of a run on every side and not just the one looking at it. A run that has not
     * changed writes nothing.
     */
    public void setRun(RgbRun run) {
        if (!run.equals(this.run)) {
            this.run = run;
            this.setChanged();
            RgbDeco.syncToClients(this.getLevel(), this);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        // A world written before the box existed has nothing to load, so the first time a panel joins
        // a level the run around it is measured. A panel that already knows its run keeps it, which is
        // what keeps loading a chunk to one walk per run instead of one per panel.
        //
        // This has to happen here rather than in setLevel: LevelChunk adds a block entity to its map
        // after setLevel has already returned, so a walk started from setLevel cannot see this panel,
        // asks the level for it, is handed a second block entity made for the same block, and that one
        // starts a walk of its own. The measure for measuring a run once per run is not worth a crash.
        Level level = this.getLevel();
        if (level != null && !level.isClientSide && !this.run.measured()) {
            RgbBlock.refreshRun(level, this.getBlockPos());
        }
    }

    /**
     * The tag a client is handed when the chunk this panel is in arrives, which is the box of the run it
     * belongs to.
     *
     * <p>Vanilla leaves this empty, on the grounds that a block entity only has to push itself when it
     * changes and a client that misses an update is still looking at the right block. The run is not
     * like that: it is a measurement of the panels around this one, taken by a walk on the server, and
     * the walk is only run when something about the wall changes. So the box on the client has to come
     * across with the chunk itself rather than be pushed, or a panel that loads as part of a wall draws
     * as a panel on its own until the wall next happens to change.
     */
    @Override
    public CompoundTag getUpdateTag() {
        return this.saveWithoutMetadata();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt(MIN_X, this.run.minX());
        tag.putInt(MIN_Y, this.run.minY());
        tag.putInt(MIN_Z, this.run.minZ());
        tag.putInt(MAX_X, this.run.maxX());
        tag.putInt(MAX_Y, this.run.maxY());
        tag.putInt(MAX_Z, this.run.maxZ());
        tag.putInt(SUM_X, this.run.sumX());
        tag.putInt(SUM_Y, this.run.sumY());
        tag.putInt(SUM_Z, this.run.sumZ());
        tag.putInt(COUNT, this.run.count());
        tag.putInt(VERSION, RUN_VERSION);
        tag.put(ENERGY, this.energy.store());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        // A run stored by an older version is not read at all. It looks like a perfectly good run by
        // every measure the drawing code makes, so it would be trusted, and the box it carries has the
        // world origin in it, which is what put the middle of a wall's rings off the end of the wall.
        // Dropping it leaves an unmeasured run, which onLoad measures for itself.
        if (tag.getInt(VERSION) < RUN_VERSION) {
            this.run = RgbRun.EMPTY;
            return;
        }
        // A world written before the sums existed loads them as nothing, which leaves a run that knows
        // its box but not what is standing in it; it is measured again when it next loads rather than
        // being trusted to carry the middle of its box for the rest of time.
        this.run = new RgbRun(
                Math.min(tag.getInt(MIN_X), tag.getInt(MAX_X)), Math.min(tag.getInt(MIN_Y), tag.getInt(MAX_Y)),
                Math.min(tag.getInt(MIN_Z), tag.getInt(MAX_Z)), Math.max(tag.getInt(MIN_X), tag.getInt(MAX_X)),
                Math.max(tag.getInt(MIN_Y), tag.getInt(MAX_Y)), Math.max(tag.getInt(MIN_Z), tag.getInt(MAX_Z)),
                tag.getInt(SUM_X), tag.getInt(SUM_Y), tag.getInt(SUM_Z), tag.getInt(COUNT));
        // A world written before panels held energy loads nothing, which is an empty buffer rather than
        // a broken one: the panel comes up uncharged, the controller hands it a share on its next draw,
        // and the wall lights again without anything having to be done to it by hand.
        this.energy.restore(tag.getCompound(ENERGY));
    }
}

