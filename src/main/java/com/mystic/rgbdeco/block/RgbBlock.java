package com.mystic.rgbdeco.block;

import com.mystic.rgbdeco.RgbDeco;
import com.mystic.rgbdeco.blockentity.RgbBlockEntity;
import com.mystic.rgbdeco.pattern.RgbPattern;
import com.mystic.rgbdeco.registry.RgbBlockEntityTypes;
import com.mystic.rgbdeco.system.RgbRefresh;
import com.mystic.rgbdeco.system.RgbRun;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/**
 * A decorative RGB panel. The pattern it shows lives in the block state, whether a controller is driving
 * it right now lives there too, the frame around a run of connected panels is worked out by the renderer
 * from the world, and the one thing that has to be stored is the box the run occupies, which lives in
 * the {@link RgbBlockEntity}.
 *
 * <p>A panel has no redstone behavior of its own, and no input of any kind. An
 * {@link RgbControllerBlock} holds the energy that pays for the system and its screen writes the
 * pattern onto every panel of it, so nothing can change a panel without a controller saying so. That
 * controller is also what lights the panels: a panel is in {@link #LIT} for as long as a single powered
 * controller can still reach it and the panel has some of that energy in it, and no longer. Empty the
 * controller and the system goes out, wire a second controller onto it and it goes out too, and take
 * away the panel that was the only thing joining a stretch of wall to its controller and that stretch
 * goes out too, rather than standing there lit for nobody. A wall that still reaches its controller is
 * left exactly as it was.
 *
 * <p>A panel also holds energy of its own, which is what stops a whole wall going out at the one moment
 * the controller's buffer runs dry: the panels nearest the controller are handed a share first and hold
 * their light for a while longer, and a wall longer than the energy coming into it settles rather than
 * strobing, with the far end going dark before the near end. A cable can be run into a panel as well as
 * into the controller, which does not light a panel by itself but keeps that panel's own buffer topped
 * up, so it is the panels of a long wall that are fed this way which hold their light through a supply
 * that has run short.
 */
public class RgbBlock extends BaseEntityBlock {
    public static final IntegerProperty VARIANT = IntegerProperty.create("variant", 0, 15);
    /** The high half of the pattern id, there are more patterns than a single property can hold. */
    public static final IntegerProperty PHASE = IntegerProperty.create("phase", 0, 15);
    /**
     * Whether a controller is in charge of this panel right now, which is what lights it.
     *
     * <p>This lives in the block state rather than in the block entity on purpose: the light a block
     * gives off is read from its state, and a panel that is out should stop lighting the room as well
     * as stop glowing. The state is the one thing every client is already sent, so the block and the
     * light can never disagree with each other, which two separate copies of the answer could.
     */
    public static final BooleanProperty LIT = BooleanProperty.create("lit");

    /** Safety net so a huge (or player made loop) construction can never hang a server tick. */
    static final int MAX_CLUSTER_SIZE = 20000;

    public RgbBlock(Properties properties) {
        super(properties);
        // A new panel starts lit. It is the safe answer to start from: a panel put down next to a
        // controller is measured by the refresh that follows and turns out for itself, and a panel put
        // down next to nothing would have nothing to say it is out, so a default of unlit would make
        // every panel in a world that has no controller in it visibly dead.
        this.registerDefaultState(withLit(this.stateDefinition.any().setValue(VARIANT, 0).setValue(PHASE, 0), true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(VARIANT, PHASE, LIT);
    }

    /** The pattern a state shows, {@link RgbPattern#BLACK} for anything that is not a panel. */
    public static int patternId(BlockState state) {
        return state.getBlock() instanceof RgbBlock ? RgbPattern.id(state.getValue(VARIANT), state.getValue(PHASE)) : 0;
    }

    /** Puts a pattern back into a state, splitting its id over the two properties. */
    public static BlockState withPattern(BlockState state, int id) {
        return state.setValue(VARIANT, RgbPattern.variant(id)).setValue(PHASE, RgbPattern.phase(id));
    }

    /**
     * Puts a panel in or out, which is what a controller decides for every panel it can reach. A panel
     * is lit only while exactly one controller holding energy is in charge of it and the panel has some
     * of that energy in it: empty the controller and the whole system goes out, and wire a second
     * controller to the same panels and it goes out too, because neither of the two is then the one in
     * charge.
     */
    public static BlockState withLit(BlockState state, boolean lit) {
        return state.setValue(LIT, lit);
    }

    /**
     * Puts a whole list of panels in or out at once, which is how a controller reaches the ones it can
     * drive. A panel that is already in the state being asked for is left alone, so a system that has
     * not changed writes nothing.
     */
    public static void light(Level level, List<BlockPos> panels, boolean lit) {
        if (level.isClientSide) {
            return;
        }
        for (BlockPos pos : panels) {
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof RgbBlock)) {
                continue;
            }
            BlockState updated = withLit(state, lit);
            if (!updated.equals(state)) {
                // Flag two keeps the change off the neighbors, which is most of what stops this walking
                // into itself. It does not stop all of it: onPlace is called for a block change whatever
                // flags it was made with, so this still re-enters refreshRun, which is what RgbRefresh is
                // there for. A uniform answer is what made the old one get away with it, because every
                // panel of a system is then written the same value and the next pass finds nothing to do.
                level.setBlock(pos, updated, 2);
            }
        }
    }

    /**
     * Puts the panels of a system in or out one at a time, according to whether each one is charged,
     * which is the question a wall with a limited supply behind it actually turns on.
     *
     * <p>A controller has one buffer for a whole wall and a panel has a small one of its own, so the
     * light of a long wall no longer arrives and leaves all at once. A panel that has nothing left in
     * it goes out on its own, and comes back on its own as soon as the controller's next draw reaches
     * it, which is what a player with less energy than their wall needs sees: the panels nearest the
     * controller keep their light and the far end of the wall goes out, and it comes back from that end
     * as the wall is filled again.
     *
     * <p>Asked of the panel as {@link RgbBlockEntity#showing()} rather than as whether it holds energy, so
     * this and the panel's own decision to go out are the same question with the same answer. A panel
     * whose block entity does not exist yet is taken as showing, because building one here would make a
     * wall of fresh panels each start a block entity on the walk.
     */
    public static void lightWhenCharged(Level level, List<BlockPos> panels, boolean driving) {
        if (level.isClientSide) {
            return;
        }
        for (BlockPos pos : panels) {
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof RgbBlock)) {
                continue;
            }
            // Bracketed as it is on purpose. Written the way the obvious way round reads, this is
            // "(driving && ... instanceof ...) ? panel.charged() : true", which puts a wall that is not
            // being driven to true rather than to false, so a controller that has run out asks for its
            // panels to be put out and this answers that every one of them is lit. Being driven is a
            // condition of a panel being lit rather than part of what is being asked of it.
            //
            // Asked as showing() rather than as charged() so this and the panel's own decision to go out
            // are the same question with the same answer. A panel that has only just spent its last unit
            // still shows, which is what keeps a measurement landing between two shares from blinking
            // half a wall.
            RgbBlockEntity panel = level.getChunkAt(pos).getBlockEntity(pos, LevelChunk.EntityCreationType.CHECK)
                    instanceof RgbBlockEntity found ? found : null;
            boolean charged = driving && (panel == null || panel.showing());
            BlockState updated = withLit(state, charged);
            if (!updated.equals(state)) {
                // Two panels of one wall can disagree here where they could not disagree before, so each
                // of these writes starts a fresh round of onPlace walking the run around that panel and
                // asking the controller to measure its system again. The guard in refreshRun and
                // refreshSystem is what keeps those rounds from nesting into each other; see RgbRefresh.
                level.setBlock(pos, updated, 2);
            }
        }
    }

    /**
     * Hands {@code units} of a controller's energy to the panels of its system, nearest the controller
     * first, and only up to what the panels will take.
     *
     * <p>The panels are already nearest the controller first, because the walk that found them is a
     * walk out from it, and that order is what a supply too small for the whole wall shows: the end of
     * the wall the controller is touching is filled and lit, and the end a room away is the one that
     * goes dark.
     *
     * <p>What is handed out is the energy the controller actually managed to take rather than the number
     * of panels there are, so a controller on its way out fills the first few panels of its wall and
     * leaves the rest to go dark on their own buffers. That is what stops a wall of two hundred panels
     * staying lit on a controller holding a handful of energy.
     */
    public static void shareEnergy(Level level, List<BlockPos> panels, int units) {
        if (level.isClientSide || units <= 0) {
            return;
        }
        for (BlockPos pos : panels) {
            if (units <= 0 || !level.isLoaded(pos)) {
                continue;
            }
            // Asked the way that never builds one: a panel that has no block entity yet is not handed
            // energy here, and picks it up on the next draw once it is really in the chunk.
            if (level.getChunkAt(pos).getBlockEntity(pos, LevelChunk.EntityCreationType.CHECK) instanceof RgbBlockEntity panel) {
                units -= panel.receiveShare(1);
            }
        }
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Join whatever is already built here so a new panel does not break up a connected run, or
        // split the system of a controller off from the rest of it.
        BlockState state = withPattern(this.defaultBlockState(), RgbControllerBlock.systemPattern(context.getLevel(), context.getClickedPos()));
        // Pre-calculate whether this panel will be driven so it doesn't flash from its default light level of 15 down to 0,
        // which happens if a player places a panel by itself because the default state is lit.
        return withLit(state, RgbControllerBlock.willBeDriven(context.getLevel(), context.getClickedPos()));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RgbBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        // Only the server spends what a panel is holding. A ticker on the client would be a tick that
        // does nothing at all, and a wall of panels has one of these on every block of it.
        if (level.isClientSide) {
            return null;
        }
        return createTickerHelper(type, RgbBlockEntityTypes.RGB_BLOCK_ENTITY.get(), RgbBlockEntity::serverTick);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        refreshRun(level, pos);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos neighborPos, boolean movedByPiston) {
        if (level.isClientSide) {
            return;
        }
        // A panel has come or gone next to this one, so the run may be bigger or smaller now.
        refreshRun(level, pos);
    }

    /**
     * Counts the panels of the same pattern connected to {@code origin} and writes the box they
     * occupy into every block entity of the run, which is how the renderer learns how far the run it
     * draws reaches. The panels of one run always show the same pattern, so this walks the same group
     * a controller writes to and stops at the same safety net. The run is returned so a caller that
     * has just given a whole system one pattern can measure each of its runs without walking anything
     * twice.
     */
    public static List<BlockPos> refreshRun(Level level, BlockPos origin) {
        return refreshRun(level, origin, true);
    }

    /**
     * The same measurement, and whether the run should be told what it is now being driven by.
     *
     * <p>That question is only worth asking when the shape of the world has just changed. Writing a
     * pattern over a system moves nothing, and the lighting was settled by the controller that wrote it,
     * so a caller that is only measuring runs passes {@code false} and does not pay for a second walk
     * of a wall it has just walked. A panel placed or broken does change the shape, and that is the
     * caller that has to ask.
     */
    public static List<BlockPos> refreshRun(Level level, BlockPos origin, boolean decideLight) {
        if (level.isClientSide || !level.isLoaded(origin)) {
            return Collections.emptyList();
        }
        BlockState originState = level.getBlockState(origin);
        if (!(originState.getBlock() instanceof RgbBlock)) {
            return Collections.emptyList();
        }
        // Claimed before the walk and not before the lookup above, because a position that is not a panel
        // has no run to claim and is asked about on every block change of anything at all.
        //
        // This is what keeps putting a panel in or out from walking back into itself: writing the light is
        // a block change, a block change calls onPlace, onPlace measures the run around that panel, and a
        // run that is already being measured is the one whose panels the walk currently standing on is in
        // the middle of deciding. See RgbRefresh for why a per panel answer needs this and a uniform one
        // does not.
        //
        // Any run at all will do for that, rather than this one by name. Claiming by position alone bounds
        // nothing: the light of a run is written panel by panel, each write calls onPlace, and each onPlace
        // claims a position of its own and measures again, so a wall was walked once per panel of it,
        // nested as deep as the wall is long. A pass already standing on the wall has written what every
        // panel of its run is before it wrote any light, and a light change does not alter the shape of a
        // run, so there is nothing for the nested pass to find.
        if (RgbRefresh.runInProgress() || !RgbRefresh.tryBeginRun(origin)) {
            return Collections.emptyList();
        }
        try {
            return measure(level, origin, decideLight);
        } finally {
            RgbRefresh.endRun(origin);
        }
    }

    /**
     * The measurement itself, which {@link #refreshRun(Level, BlockPos, boolean)} only reaches once it
     * holds the claim on this run.
     */
    private static List<BlockPos> measure(Level level, BlockPos origin, boolean decideLight) {
        BlockState originState = level.getBlockState(origin);
        int pattern = patternId(originState);

        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        ArrayList<BlockPos> run = new ArrayList<>();
        HashSet<Long> seen = new HashSet<>();
        queue.add(origin);
        seen.add(origin.asLong());
        RgbRun box = RgbRun.EMPTY;

        // The pattern is tested before a neighbor is queued rather than when it is taken off the queue,
        // which is the same set of blocks either way and the walk stops where the run stops either way.
        // Two panels of one pattern standing side by side are one run, so a neighbor showing a
        // different one is the end of the run and is never expanded through: asked afterwards, the
        // walk had already paid to read it and throw it away, and the queue in between held panels that
        // were not part of the run at all, which is what let a wall of alternating patterns cost far more
        // to measure than a solid one of the same size.
        while (!queue.isEmpty() && run.size() < MAX_CLUSTER_SIZE) {
            BlockPos pos = queue.poll();
            run.add(pos);
            box = box.grow(pos);
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (!seen.add(next.asLong()) || !level.isLoaded(next)) {
                    continue;
                }
                BlockState nextState = level.getBlockState(next);
                if (nextState.getBlock() instanceof RgbBlock && patternId(nextState) == pattern) {
                    queue.add(next);
                }
            }
        }

        // A controller reaching this run is asked to re-measure its system as well, because a panel
        // coming or going can change the size of what it drives, and only the controller knows the
        // number the screen and the block itself are drawn from.
        HashSet<Long> refreshed = new HashSet<>();
        for (BlockPos pos : run) {
            // Asked for the way that never builds one: a panel whose block entity does not exist yet is
            // simply not told the size of its run here, and measures itself once it is finally added to
            // the chunk.
            if (level.getChunkAt(pos).getBlockEntity(pos, LevelChunk.EntityCreationType.CHECK) instanceof RgbBlockEntity panel) {
                panel.setRun(box);
            }
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (refreshed.add(next.asLong()) && isLoadedController(level, next)) {
                    // The controller owns whether these panels are lit and puts the answer on all of
                    // them, so asking it is what keeps one run from holding two different answers, and
                    // what re-counts its system when a panel is taken out of the middle of a wall.
                    RgbControllerBlock.refreshSystem(level, next);
                }
            }
        }
        // What is decided last, and only when the shape of the world may have changed, is whether the
        // run still has anyone driving it. The controllers above have already answered for everything
        // they can see, so what is left to settle is a run that has just lost the panel which was
        // joining it to them: nothing reaches it any more, and a wall nothing is driving is not one
        // that should still be glowing. A run that still reaches its controller comes back lit and
        // writes nothing, so work on one end of a wall never disturbs the other.
        // The panels are put in or out by the controller that is in charge of them, reached from
        // wherever it happens to be touching, rather than answered for this run on its own. A run only
        // holds the panels showing one pattern, so a wall of a second pattern standing between it and
        // its controller leaves the run with nobody to ask, and asking nothing is what left a panel
        // that had been off sitting in the dark after it had been joined to a system that was lit: it
        // was never in the run that got answered, so nothing ever told it otherwise.
        //
        // A run nothing drives is put out directly, since there is no controller to hand it to and a
        // panel nothing is driving has to go dark rather than keep the answer it was last given.
        if (decideLight) {
            BlockPos controller = RgbControllerBlock.drivingController(level, origin);
            if (controller == null) {
                light(level, run, false);
            } else {
                RgbControllerBlock.refreshSystem(level, controller);
            }
        }
        if (!queue.isEmpty()) {
            RgbDeco.LOGGER.warn("RGB run at {} is larger than {} blocks, its patterns are scaled short", origin, MAX_CLUSTER_SIZE);
        }
        return run;
    }

    /** True when a controller is standing at {@code pos}, and its chunk is here to be asked. */
    private static boolean isLoadedController(Level level, BlockPos pos) {
        return level.isLoaded(pos) && level.getBlockState(pos).getBlock() instanceof RgbControllerBlock;
    }
}
