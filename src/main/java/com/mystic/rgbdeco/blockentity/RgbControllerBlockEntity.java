package com.mystic.rgbdeco.blockentity;

import com.mystic.rgbdeco.RgbDeco;
import com.mystic.rgbdeco.block.RgbBlock;
import com.mystic.rgbdeco.block.RgbControllerBlock;
import com.mystic.rgbdeco.energy.RgbEnergyStorage;
import com.mystic.rgbdeco.network.RefreshSystemPacket;
import com.mystic.rgbdeco.network.RgbNetwork;
import com.mystic.rgbdeco.registry.RgbBlockEntityTypes;
import com.mystic.rgbdeco.system.RgbSystem;
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

/**
 * Carries the two numbers that tell a controller how its system looks: how many panels it reaches and
 * how many controllers share them. The block itself is drawn by the client renderer from these, and
 * the screen reads them to say whether the system can be driven, so both sides stay in step through
 * the normal block entity update.
 *
 * <p>The two counts are the result of a walk and would otherwise be repeated for every block of the
 * system on every frame, and the last pattern chosen is stored because it cannot be worked out from
 * the world at all: a controller whose panels have all been taken away still knows what its wall was
 * showing, and a panel put back has to come up on it.
 *
 * <p>The controller is also where the energy of a system lives. It holds a Forge energy buffer that a
 * cable, a cell or a player fills from any side, and while it holds any it is the one in charge of its
 * panels: the system is lit, the screen opens, and a click puts a pattern on the wall. Empty it and
 * the whole system goes out, exactly as a controller that had lost its signal used to. What a panel
 * holds is only its own share of that, drawn down one unit at a time as it stays lit.
 */
public class RgbControllerBlockEntity extends BlockEntity {
    /** What a controller with no panels on its system shows, a block that is simply not doing anything. */
    public static final int OFF = 0xFF000000;
    /**
     * The healthy color, the one controller on a system that has panels to drive.
     *
     * <p>Kept as light as the status can be while still reading as a color rather than as white. A face
     * does not get the color at full strength: the sides keep four fifths of it and the bottom rather
     * less, so a status picked to be dark enough to look like a shade ends up nearly black on four of
     * the six faces of the block, and a controller is read from its side as often as from above.
     */
    public static final int HEALTHY = 0xFF4ADE80;
    /**
     * The conflict color, more than one controller on the same system, which locks the system.
     *
     * <p>A lighter red than the green is not beside the point. Red is the darker of the two at any
     * given weight, so holding the pair at the same apparent brightness means carrying red a step
     * further up than green, and it is what keeps a conflict as obvious as a working system.
     */
    public static final int CONFLICT = 0xFFF87171;

    /**
     * What a controller holds, which is a middle Powah energy cell's worth: enough to run a good sized
     * wall for a long while on a cable that is only trickling at a few hundred an hour, and small
     * enough that filling one from a furnace full of coal is a thing worth doing rather than a thing
     * that takes all afternoon.
     */
    public static final int CAPACITY = 40000;
    /** What a cable can push into a controller in one go, which is the rate a Powah cable pushes. */
    public static final int MAX_RECEIVE = 256;
/** How many ticks stand between two draws of energy, about once a second. */
    public static final int TICK_PERIOD = 20;
    /**
     * How far the charge has to move before it is worth pushing to the players who can see this.
     *
     * <p>A number of steps across the whole buffer rather than a number of units, because what the
     * charge has to be legible against is the bar: a step is one part in this many of the capacity, so
     * this many messages bring the bar from full to empty however big the buffer is, and the number
     * under it moves at the same time. A flat number of units would mean a big controller never reaches
     * its first message and a small one sends one every tick.
     *
     * <p>Two hundred and fifty-six is about two messages per pixel of the widest bar the screen draws,
     * so the bar steps as smoothly as it can be seen to, and no smoother than that.
     */
    private static final int CHARGE_STEPS = 256;

    private static final String PANELS = "panels";
    private static final String CONTROLLERS = "controllers";
    private static final String PATTERN = "pattern";
    private static final String POWERED = "powered";
    private static final String ENERGY = "energy";
    /** The answer a controller gives before anything has been asked of it: nothing chosen yet. */
    private static final int NOTHING_CHOSEN = -1;

    /** How many panels this controller reaches, directly or through other panels. */
    private int panels;
    /** How many controllers share those panels, this one included. */
    private int controllers;
    /**
     * The pattern the screen last set, kept here rather than worked out from the panels around it.
     *
     * <p>Reading the pattern back off a neighboring panel only works while a panel is still standing
     * against the controller. Take the panel away and there is nothing left to read, so a panel put
     * back would come up on the default pattern and leave the wall showing a block of nothing in the
     * middle of it until somebody opened the screen and chose the pattern again. What the player last
     * asked for is a thing the controller knows and does not lose by having its panels changed.
     */
    private int lastPattern = NOTHING_CHOSEN;
    /** The energy this controller is holding, and the only way anything ever fills it. */
    private final RgbEnergyStorage energy = new RgbEnergyStorage(CAPACITY, MAX_RECEIVE);
    private final LazyOptional<IEnergyStorage> energyHolder = LazyOptional.of(() -> this.energy);
/**
     * Whether the controller held anything the last time it was looked at, written down so a client
     * can draw the block without asking the server. It is the only thing about the energy that block is
     * drawn from: the charge itself is pushed out on the rate {@link #CHARGE_STEPS} sets, because a
     * screen draws a bar off it, and a client that is never told the charge draws a controller draining
     * away as a bar that never moves.
     */
    private boolean powered;
    /**
     * The charge the players were last told about, so the next push can tell what has changed since.
     *
     * <p>Deliberately not saved. It is the last number that left this controller rather than anything
     * about it, and a controller read back off disk has not told anybody anything yet. Left at
     * {@code -1} it forces the first push, which is what makes a screen opened on a controller that has
     * just been filled show the charge rather than a bar that quietly fills in on its own.
     */
    private int sentCharge = -1;
    /** Counts the ticks between two draws, so the draw is a rate rather than a per tick cost. */
    private int countdown;

    public RgbControllerBlockEntity(BlockPos pos, BlockState state) {
        super(RgbBlockEntityTypes.RGB_CONTROLLER_ENTITY.get(), pos, state);
    }

    /** How many panels the screen of this controller would drive. */
    public int panels() {
        return this.panels;
    }

    /** How many controllers are on the system, this one included. */
    public int controllers() {
        return this.controllers;
    }

/** True when more than one controller is wired into the same panels. */
    public boolean conflicted() {
        return this.controllers > 1;
    }

    /**
     * What the controller holds, and what anything reading this block entity off the level wants: a
     * system is in charge of itself only while there is energy behind it.
     *
     * <p>Read off the block entity rather than the block state, which is where a redstone signal used to
     * be kept and where nothing is kept now. The two ways of answering this are deliberately the same
     * method, so a controller cannot end up lit on the block while the walk that puts light on the
     * panels says the opposite.
     */
    public boolean powered() {
        return this.powered;
    }

    /** The energy this controller holds, for a screen that has to show how much is left. */
    public int energy() {
        return this.energy.getEnergyStored();
    }

    /** The energy a controller can hold, so a screen can draw a bar against something. */
    public int capacity() {
        return CAPACITY;
    }

    /**
     * The color the controller is drawn in from the counts alone: off while there are no panels to
     * drive, the conflict color once a second controller shows up, and the healthy color in between.
     * This cannot see whether the controller holds any energy, so what is actually drawn comes from
     * {@link #displayColor(Level)}, which asks about that first.
     */
    public int displayColor() {
        if (this.panels <= 0) {
            return OFF;
        }
        return this.controllers > 1 ? CONFLICT : HEALTHY;
    }

    /**
     * The same color, but one that can tell the copy of the counts held here has fallen behind, and one
     * that knows whether this controller still holds any energy.
     *
     * <p>The energy is asked of the block entity and not of the counts, because the counts describe the
     * size of a system and say nothing about whether anything is feeding it. A controller with a wall on
     * it and nothing filling it has panels to drive and nothing to drive them with, and drawing it as
     * healthy says a system is running when not one panel of it is lit. A controller is black unless
     * something is actually holding it, which is also why the fallback below cannot hand back a color
     * for an unpowered one.
     *
     * <p>A count of no panels next to a panel the client can plainly see is not a system with no panels:
     * it is a number that has not arrived. The server pushes the counts out when they change, but a
     * controller that was already standing in a chunk a player came back to can be holding the count it
     * was first measured with, and a block drawn from a count of nothing is a block that shows no color at
     * all however much is wired to it.
     *
     * <p>So the world is believed over the count: if a panel is standing against this controller, the
     * controller is drawn as alive, and the server is asked to measure the system again so the count
     * catches up. Asking is throttled, because the reason the count is wrong is also true on every
     * frame and a message per frame per controller would be a flood.
     */
    public int displayColor(Level level) {
        if (!this.powered) {
            return OFF;
        }
        if (this.panels <= 0 && RgbControllerBlock.panelNeighbor(level, this.getBlockPos())) {
            this.askToMeasureAgain(level);
            return this.controllers > 1 ? CONFLICT : HEALTHY;
        }
        return this.displayColor();
    }

    /** How many ticks a controller waits before asking to be measured again, about two seconds. */
    private static final long MEASURE_AGAIN = 40L;

    /** When this controller last asked, on the client only, and never worth saving. */
    private long lastAsk = -1000L;

    private void askToMeasureAgain(Level level) {
        if (!level.isClientSide) {
            return;
        }
        long now = level.getGameTime();
        if (now - this.lastAsk < MEASURE_AGAIN) {
            return;
        }
        this.lastAsk = now;
        RgbNetwork.CHANNEL.sendToServer(new RefreshSystemPacket(this.getBlockPos().immutable()));
    }

    /**
     * Stores the size of the system and sends it to the players who can see it, so the block is drawn
     * in the right color and the screen over it knows whether the system can be driven. A system that
     * has not changed writes nothing.
     */
    public void setSystem(int panels, int controllers) {
        if (panels != this.panels || controllers != this.controllers) {
            this.panels = panels;
            this.controllers = controllers;
            this.setChanged();
            RgbDeco.syncToClients(this.getLevel(), this);
        }
    }

    /**
     * Sends the counts on whether they have changed or not.
     *
     * <p>Only a system that has actually changed pushes itself, which keeps a wall from sending a
     * message per panel as it is built. That leaves one gap: a client holding a count that was never
     * arrived at, which is what a controller in a chunk a player comes back to has, and asking the
     * server to measure again measures the count the server already had right and so has nothing to
     * send. The copy that is wrong is the one nobody would ever hear about again, so a measurement
     * that was asked for is sent whether it moved or not.
     */
    public void publishSystem() {
        RgbDeco.syncToClients(this.getLevel(), this);
    }

    /** The pattern the screen of this controller last set, or {@link #NOTHING_CHOSEN} if none has been. */
    public int lastPattern() {
        return this.lastPattern;
    }

    /** True once a pattern has been chosen on this controller's screen, so it has something to restore. */
    public boolean hasChosenPattern() {
        return this.lastPattern != NOTHING_CHOSEN;
    }

    /**
     * Remembers the pattern the screen last set, so a panel that is put back against this controller
     * comes up on it rather than on whatever happens to be next to it.
     */
    public void rememberPattern(int patternId) {
        if (this.lastPattern == patternId) {
            return;
        }
        this.lastPattern = patternId;
        this.setChanged();
        RgbDeco.syncToClients(this.getLevel(), this);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        // A world written before the counts existed has nothing to load, so the first time a
        // controller joins a level the system around it is measured. A controller that already knows
        // about a conflict keeps it, which is what keeps loading a chunk to one walk per controller.
        //
        // As on the panel, this has to wait until onLoad: a walk started from setLevel cannot see this
        // controller, because the chunk only adds it to its map after setLevel has returned.
        Level level = this.getLevel();
        if (level != null && !level.isClientSide && this.controllers <= 1) {
            RgbControllerBlock.refreshSystem(level, this.getBlockPos());
        }
    }

    /**
     * The tag a client is handed when the chunk this controller is in arrives.
     *
     * <p>Vanilla leaves this empty and leaves it to a block entity to push itself whenever something
     * about it changes, which is fine for a block that is only ever told about changes. It is not fine
     * here, because what this block holds is a measurement of the world rather than a record of an
     * edit: nothing about placing a panel, or breaking one two rooms away, is a change to this block
     * entity, so the counts sitting on the server are the only thing that knows how many panels and how
     * many controllers there are, and a client that loaded the chunk without them holds zeroes.
     *
     * <p>A controller holding zeroes is not a controller with nothing on it. It is drawn with no color at
     * all, which is how two controllers wired into one wall came to sit there green rather than red
     * while the screen over them said there were no panels connected. Answering this with the counts is
     * what puts the truth in a client's hands before it draws anything.
     */
    @Override
    public CompoundTag getUpdateTag() {
        return this.saveWithoutMetadata();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt(PANELS, this.panels);
        tag.putInt(CONTROLLERS, this.controllers);
        tag.putBoolean(POWERED, this.powered);
        tag.put(ENERGY, this.energy.store());
        if (this.hasChosenPattern()) {
            tag.putInt(PATTERN, this.lastPattern);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.panels = Math.max(0, tag.getInt(PANELS));
        this.controllers = Math.max(1, tag.getInt(CONTROLLERS));
        this.powered = tag.getBoolean(POWERED);
        this.energy.restore(tag.getCompound(ENERGY));
        this.lastPattern = tag.contains(PATTERN) ? tag.getInt(PATTERN) : NOTHING_CHOSEN;
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        this.energyHolder.invalidate();
    }

    /**
     * The buffer, offered on every side rather than on one face, because what fills a controller is a
     * cable run into it and there is no face of a cube that is the front.
*/
    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        return cap == ForgeCapabilities.ENERGY ? this.energyHolder.cast() : super.getCapability(cap, side);
    }

    /**
     * Draws the system's energy and hands each panel its share.
     *
     * <p>The cost of a system is one unit per panel per second, taken from the controller, because a
     * system is only ever as big as the panels it is running and a fixed cost per tick would make a
     * large wall free and a single panel ruinous. A controller with no panels on its system spends
     * nothing at all: an empty controller standing by itself costs nothing to own.
     *
     * <p>The draw is the same answer as {@link #powered()} reaching the panels, so it happens here
     * rather than leaving each panel to go and ask. A controller that runs out puts the system out on
     * the same tick, which is what stops a wall being lit by a buffer that was emptied a second ago,
     * and it hands the lights back out on the first tick it is filled again.
     *
* <p>Only the change of the flag is pushed out to clients, because a client draws this block from the
     * stored {@code powered} and all it needs for that is to know that it changed. The charge is a
     * different matter: the screen draws a bar and a number off it, so a client never told the charge
     * shows a controller draining away as a bar that does not move. Energy arriving through
     * {@link RgbEnergyStorage} pushes nothing itself, because a cable fills a buffer many times between
     * two of these and a message for each of those would be a flood from one cable. What pushes it is
     * the draw below, on the rate {@link #CHARGE_STEPS} sets.
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state, RgbControllerBlockEntity controller) {
        if (--controller.countdown > 0) {
            return;
        }
        controller.countdown = TICK_PERIOD;
        boolean wasPowered = controller.powered;
        controller.powered = controller.energy.getEnergyStored() > 0;

        // Measured here, every second, which is the one walk this does on its own account rather than
        // because something in the world changed. Everything else about a system's size is already
        // re-measured by whatever changed it: putting a panel up or down sends a neighbor change, and a
        // controller being joined up sends one too. So the walk here is on a wall of any size at a rate
        // of once a second, and not a walk per block change, which is what a player building a wall
        // would otherwise pay for.
        //
        // Measured without lighting anything, which is what {@link RgbControllerBlock#scan} is: the panels
        // have to be handed their share before it can be decided whether they are lit, and a walk that
        // put the light out as it went would be deciding that before there was anything to decide on.
        RgbSystem system = RgbControllerBlock.scan(level, pos);

        if (controller.powered && system.drivable() && system.panels() > 0) {
            // Only what there was to spend is taken, and the panels are handed that same number rather
            // than the number of panels there are. A controller on its last unit is therefore handing
            // out one unit of light and taking one back, so the wall settles at the size the charge can
            // actually pay for instead of staying lit on a buffer that has nothing left in it.
            int paid = controller.energy.spend(system.panels());
            if (paid > 0) {
                controller.setChanged();
                RgbBlock.shareEnergy(level, system.panelPositions(), paid);
            }
        }

        // Settled after the share, not before. A panel is handed exactly what it spends in a second, so a
        // driven wall sits at one or two units rather than at a hundred, and whether a panel is showing
        // is decided by whether it has just been handed its unit. Asking before handing one out asks
        // about a panel that is empty by definition.
        RgbControllerBlock.applyLight(level, pos, system);

        // A controller holding energy for the hundredth second looks exactly as it did for the ninety ninth,
        // and every second of it would be a message to everyone nearby for nothing.
        if (wasPowered != controller.powered) {
            controller.setChanged();
            RgbDeco.syncToClients(level, controller);
        }
        // The charge is pushed on how far it has moved rather than every draw, because what a system
        // costs is its own size: a wall of two panels spends two a second and moves a pixel an hour, a
        // wall of two hundred spends two hundred and moves more than the bar is wide. Asking against the
        // bar instead sends both the same number of times over a full discharge, which is as often as
        // the bar can be seen to move at all.
        int charge = controller.energy.getEnergyStored();
        int step = Math.max(1, CAPACITY / CHARGE_STEPS);
        if (controller.sentCharge < 0 || Math.abs(charge - controller.sentCharge) >= step) {
            controller.sentCharge = charge;
            RgbDeco.syncToClients(level, controller);
        }
    }

    }
