package com.mystic.rgbdeco.block;

import com.mystic.rgbdeco.RgbDeco;
import com.mystic.rgbdeco.blockentity.RgbControllerBlockEntity;
import com.mystic.rgbdeco.network.OpenControllerScreenPacket;
import com.mystic.rgbdeco.network.RgbNetwork;
import com.mystic.rgbdeco.pattern.RgbPattern;
import com.mystic.rgbdeco.registry.RgbBlockEntityTypes;
import com.mystic.rgbdeco.system.RgbRefresh;
import com.mystic.rgbdeco.system.RgbSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;

/**
 * The block that drives RGB panels. It is fed Forge energy, by a cable, a cell or anything else that
 * pushes into the same capability, and right clicking it while it holds any opens the screen that sets
 * the pattern of every panel it can reach.
 *
 * <p>A system is the panels touching a controller plus every panel connected to those, so a wall that
 * runs away from the controller is still part of it. Two controllers on the same panels would each be
 * writing the same blocks, so a system is only driven while it holds exactly one controller: the
 * second controller turns the system red and leaves every panel where it is.
 *
 * <p>Being the one in charge is also what lights the panels. A panel is lit only while exactly one
 * controller can still reach it, that controller holds energy, and the panel itself has some of that
 * energy in it: empty the controller and the whole system goes out, wire a second controller onto the
 * same panels and it goes out too, and take away the panel that was the only thing joining a stretch of
 * wall to its controller and that stretch goes out as well, because nothing is driving it any more.
 * Every one of those answers is written onto a panel as {@link RgbBlock#LIT}, and it is asked from
 * both ends, because a controller only hears about a wall it can still see and a panel cannot be left
 * holding a light nobody is responsible for. A wall that still reaches its controller is never
 * disturbed by work on the far side of it.
 *
 * <p>A controller also remembers the pattern its screen last set, which is what a panel put back
 * against it comes up on. A controller with nothing standing next to it still knows what its wall was
 * showing.
 *
 * <p>The controller itself gives off no light, which keeps the panels the only light in a construction.
 */
public class RgbControllerBlock extends BaseEntityBlock {
    public RgbControllerBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RgbControllerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        // Only the server draws energy and puts the panels in or out. A ticker on the client would be a
        // tick that does nothing at all, and every panel of a wall has one of these.
        if (level.isClientSide) {
            return null;
        }
        return createTickerHelper(type, RgbBlockEntityTypes.RGB_CONTROLLER_ENTITY.get(), RgbControllerBlockEntity::serverTick);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        refreshSystem(level, pos);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos neighborPos, boolean movedByPiston) {
        if (level.isClientSide) {
            return;
        }
        refreshSystem(level, pos);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        // A controller being broken is the one way a system loses the thing that decides whether it is
        // lit, and no panel is told about it: the panel that was next to it is simply gone, and the
        // panels further along the wall never hear a thing. The system is still whole at this point, so
        // it is measured here and put out. Only a controller that is really going away does this: a
        // controller replaced by another controller is measured by the one taking its place.
        if (!state.is(newState.getBlock()) && !(newState.getBlock() instanceof RgbControllerBlock) && !level.isClientSide) {
            RgbBlock.light(level, walk(level, pos).panelPositions(), false);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        boolean powered = powered(level, pos);
        if (level.isClientSide) {
            // The answer has to match the one the server will give, or the client would think the hand
            // was taken even when the server is about to leave the item the player is holding to it.
            return InteractionResult.sidedSuccess(powered);
        }
        if (!powered) {
            // Nothing here is holding the hand, so an item a player is holding still gets used.
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer server) {
            // The screen is only for a system this controller is allowed to drive. A system with a
            // second controller on it is locked, and the screen would be a grid of choices that cannot
            // reach a panel, so the server does not open it at all rather than opening it and having
            // every click refused. The block itself carries the conflict color for that.
            //
            // The counts are pushed as well, because this is the moment the player is about to be told
            // how many panels the system holds and the copy on their client may never have arrived.
            RgbSystem system = refreshSystem(server.serverLevel(), pos, true);
            if (system.controllers() > 1) {
                return InteractionResult.PASS;
            }
            RgbNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> server), new OpenControllerScreenPacket(pos.immutable()));
        }
        return InteractionResult.CONSUME;
    }

    /**
     * The panels and controllers that belong to the controller at {@code origin}: the panels touching
     * it, every panel connected to those, and every controller touching any of them. Panels are only
     * ever walked through panels, so two controllers standing side by side with nothing between them
     * are not a conflict, and neither is a controller with nothing attached.
     */
    public static RgbSystem scan(Level level, BlockPos origin) {
        if (level.isClientSide || !level.isLoaded(origin)
                || !(level.getBlockState(origin).getBlock() instanceof RgbControllerBlock)) {
            return RgbSystem.EMPTY;
        }
        return walk(level, origin);
    }

    /**
     * The walk itself, with the origin taken on trust rather than looked up. Only the panels around a
     * controller are read, so a caller that is holding the controller that stood there and nothing
     * else can still measure what it was driving.
     */
    private static RgbSystem walk(Level level, BlockPos origin) {
        ArrayList<BlockPos> panels = new ArrayList<>();
        HashSet<Long> seen = new HashSet<>();
        HashSet<Long> seenControllers = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        // The controller always counts itself, whether or not it reaches a single panel.
        int controllers = 1;
        seenControllers.add(origin.asLong());
        for (Direction direction : Direction.values()) {
            BlockPos next = origin.relative(direction);
            if (isPanel(level, next) && seen.add(next.asLong())) {
                queue.add(next);
            }
        }

        while (!queue.isEmpty() && panels.size() < RgbBlock.MAX_CLUSTER_SIZE) {
            BlockPos pos = queue.poll();
            panels.add(pos);
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (!level.isLoaded(next)) {
                    continue;
                }
                Block block = level.getBlockState(next).getBlock();
                if (block instanceof RgbBlock) {
                    if (seen.add(next.asLong())) {
                        queue.add(next);
                    }
                } else if (block instanceof RgbControllerBlock && seenControllers.add(next.asLong())) {
                    // A controller sitting against several panels of the same system is still only one
                    // controller, so it is counted by where it stands and not once per panel it touches.
                    controllers++;
                }
            }
        }

        if (!queue.isEmpty()) {
            RgbDeco.LOGGER.warn("RGB system at {} holds more than {} panels, the rest was left out", origin, RgbBlock.MAX_CLUSTER_SIZE);
        }
        return new RgbSystem(panels, controllers);
    }

    /**
     * True when the panel at {@code pos} is still driven, which is what decides whether it is lit: one
     * controller has to be able to reach it through the panels joining them, that controller has to hold
     * energy, and the panel has to have some of that energy in it.
     *
     * <p>Asked from the panel rather than from the controller because a panel is the one that can be left
     * holding the answer it was last given. A controller knows what it drives, and when it is broken, or
     * it has run dry, or the panel that joined it to the rest of the wall is taken out, it is the last
     * to hear about it and cannot be asked afterwards. So the walk here starts at the panel and goes
     * looking for whoever is still holding it, and comes back with what it found: a controller that is
     * still there and still powered, or nothing at all, or two of them, and none of those three is lit.
     *
     * <p>The walk goes through every panel whatever pattern it is showing, not just the ones this panel
     * shares a pattern with, because a controller reaches a run through whatever stands in between and a
     * second controller on the far side of another pattern is a conflict all the same.
     *
     * <p>A controller touching several panels of the same wall is one controller, so controllers are
     * counted by where they stand rather than once per panel they touch, and the walk stops the moment a
     * second one turns up: a system nobody is in charge of is not lit, and there is nothing to be gained
     * from measuring the rest of a wall that has already answered.
     */
    public static boolean driven(Level level, BlockPos pos) {
        if (level.isClientSide || !level.isLoaded(pos) || !(level.getBlockState(pos).getBlock() instanceof RgbBlock)) {
            return false;
        }
        return isDrivenInternally(level, pos);
    }

    /**
     * Determines whether a panel placed at {@code pos} will be driven immediately.
     * This avoids flashing light levels on the client when panels are placed by themselves,
     * while still allowing generated ones to default to lit.
     */
    public static boolean willBeDriven(Level level, BlockPos pos) {
        if (level.isClientSide || !level.isLoaded(pos)) {
            return false;
        }
        return isDrivenInternally(level, pos);
    }

    /**
     * Inner queue logic shared between {@link #driven} and {@link #willBeDriven}.
     */
    private static boolean isDrivenInternally(Level level, BlockPos pos) {
        return drivingController(level, pos) != null;
    }

    /**
     * The one powered controller that is in charge of the panel at {@code pos}, or {@code null} when
     * there is not exactly one.
     *
     * <p>Asked by the panel rather than by the controller for the reasons {@link #driven(Level, BlockPos)}
     * gives, and asked for the position as well as the yes or no because a caller that has found the
     * controller can hand the whole system to it rather than answering for one run at a time.
     *
     * <p>It does not check that {@code pos} is a panel, because a panel that is being placed is asked
     * about before there is a panel there: the walk starts from the position either way and only ever
     * reads what is around it.
     */
    @Nullable
    public static BlockPos drivingController(Level level, BlockPos pos) {
        if (level.isClientSide || !level.isLoaded(pos)) {
            return null;
        }
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        HashSet<Long> seen = new HashSet<>();
        HashSet<Long> controllers = new HashSet<>();
        BlockPos controller = null;
        seen.add(pos.asLong());
        queue.add(pos);

        while (!queue.isEmpty() && seen.size() < RgbBlock.MAX_CLUSTER_SIZE) {
            BlockPos current = queue.poll();
            for (Direction direction : Direction.values()) {
                BlockPos next = current.relative(direction);
                if (!level.isLoaded(next)) {
                    continue;
                }
                Block block = level.getBlockState(next).getBlock();
                if (block instanceof RgbBlock) {
                    if (seen.add(next.asLong())) {
                        queue.add(next);
                    }
                } else if (block instanceof RgbControllerBlock && controllers.add(next.asLong())) {
                    if (controllers.size() > 1) {
                        return null;
                    }
                    controller = next;
                }
            }
        }
        // A run larger than the safety net was never measured, so the one controller that turned up
        // cannot be said to have the whole of it. It goes out rather than being left lit on a guess.
        return controller != null && powered(level, controller) ? controller : null;
    }

    /**
     * Measures the system of a controller, stores its size in the block entity and tells every panel of
     * it whether it is lit. The system it measured is returned so a caller that is about to write a
     * pattern over it does not have to walk it a second time.
     */
    public static RgbSystem refreshSystem(Level level, BlockPos origin) {
        return refreshSystem(level, origin, false);
    }

    /**
     * Measures the system, optionally sending the counts to the players who can see the controller
     * whether they have moved or not. That is what a client gets when it has asked to be measured again:
     * the walk it asked for may well come back with the number the server already had, and the copy
     * that needs correcting is the one on the client, which a count that has not changed would never
     * send.
     */
    public static RgbSystem refreshSystem(Level level, BlockPos origin, boolean publish) {
        if (level.isClientSide || !level.isLoaded(origin)
                || !(level.getBlockState(origin).getBlock() instanceof RgbControllerBlock)) {
            return RgbSystem.EMPTY;
        }
        // Claimed before the walk, and for the same reason a run is: putting a panel in or out is a block
        // change, a block change calls onPlace, and onPlace measures the run around that panel, which
        // comes back here. A system already being measured is one whose panels this walk is in the middle
        // of deciding, so measuring it again would only repeat work the caller is about to have done.
        //
        // Answering EMPTY rather than walking anyway is what the callers can live with: they are all
        // asking as part of settling a wall, and the walk already standing on it holds the answer for
        // every panel of the system.
        if (!RgbRefresh.tryBeginSystem(origin)) {
            return RgbSystem.EMPTY;
        }
        try {
            return measureSystem(level, origin, publish);
        } finally {
            RgbRefresh.endSystem(origin);
        }
    }

    /**
     * The measurement itself, which {@link #refreshSystem(Level, BlockPos, boolean)} only reaches once it
     * holds the claim on this system.
     */
    private static RgbSystem measureSystem(Level level, BlockPos origin, boolean publish) {
        RgbSystem system = scan(level, origin);
        // Asked the way that never builds one: a controller whose block entity does not exist yet is
        // simply not told the size of its system here, and measures itself once it is in the chunk.
        if (level.getChunkAt(origin).getBlockEntity(origin, LevelChunk.EntityCreationType.CHECK) instanceof RgbControllerBlockEntity controller) {
            controller.setSystem(system.panels(), system.controllers());
            if (publish) {
                controller.publishSystem();
            }
        }
        // The panels are told as well as the controller. A panel is lit only while this controller is
        // the one in charge of the system, the controller holds energy, and the panel holds some too, so
        // a controller running dry or a second controller being wired onto the same panels puts the
        // whole of it out. That has to be pushed from here because a controller losing its last of the
        // energy is not a change any panel hears about.
        applyLight(level, origin, system);
        return system;
    }

    /**
     * Puts the light of an already measured system onto its panels, without walking it again.
     *
     * <p>Split out of {@link #measureSystem} so a caller that has just handed out energy can settle the
     * light from the same measurement it spent against. Which order those two happen in is the whole of
     * whether a wall stays lit: a panel is given exactly what it spends in a second, so whether it holds
     * anything at all is decided by whether it has just been given its share, and putting the light out
     * first asks about a panel that is empty by definition.
     */
    public static void applyLight(Level level, BlockPos origin, RgbSystem system) {
        if (level.isClientSide) {
            return;
        }
        RgbBlock.lightWhenCharged(level, system.panelPositions(), isDriving(level, origin) && system.controllers() == 1);
    }

    /** True when the controller at {@code origin} holds energy, which is what puts a system in charge. */
    private static boolean isDriving(Level level, BlockPos origin) {
        return powered(level, origin);
    }

    /**
     * True when there is energy behind the controller at {@code pos}.
     *
     * <p>Asked of the block entity rather than of the block state, because energy is not a redstone
     * signal and is not anything a state can hold: it is a number inside the block that a cable fills
     * over time. A controller whose block entity does not exist yet, because it has just been placed or
     * its chunk has not arrived, is not driving anything, which is the same answer the block used to
     * give when no signal was reaching it.
     *
     * <p>Asked the same way on both sides, because the two have to agree and the client is the one that
     * decides whether the hand was accepted before the server has been asked. What the client reads is
     * the flag the server pushed rather than the charge itself, so it can be a tick behind; it is a
     * controller that has run dry which matters, and that lasts for however long it takes a cable to
     * fill it again.
     */
    public static boolean powered(Level level, BlockPos pos) {
        if (!level.isLoaded(pos) || !(level.getBlockState(pos).getBlock() instanceof RgbControllerBlock)) {
            return false;
        }
        return level.getChunkAt(pos).getBlockEntity(pos, LevelChunk.EntityCreationType.CHECK)
                instanceof RgbControllerBlockEntity controller && controller.powered();
    }

    /**
     * Puts one pattern on every panel of the system, and only while this controller is the one allowed
     * to drive it. The caller is the network handler, so everything the player asked for is checked
     * again here: a controller that has run out of energy, that has been broken, that no longer owns the
     * system alone, or a pattern that does not exist, all leave the panels alone.
     */
    public static void applyPattern(Level level, BlockPos origin, int patternId) {
        if (level.isClientSide || patternId < 0 || patternId >= RgbPattern.COUNT) {
            return;
        }
        BlockState state = level.isLoaded(origin) ? level.getBlockState(origin) : null;
        if (state == null || !(state.getBlock() instanceof RgbControllerBlock) || !isDriving(level, origin)) {
            return;
        }

        // Measured through the one place that knows how: it stores the size on the controller and puts
        // the panels in or out along the way, so a click cannot leave a system showing one thing and
        // its panels showing another.
        RgbSystem system = refreshSystem(level, origin);
        if (!system.drivable()) {
            RgbDeco.LOGGER.debug("RGB controller at {} left the panels alone: {} panels, {} controllers, pattern {}",
                    origin, system.panels(), system.controllers(), patternId);
            return;
        }

        for (BlockPos pos : system.panelPositions()) {
            BlockState panel = level.getBlockState(pos);
            if (panel.getBlock() instanceof RgbBlock) {
                BlockState updated = RgbBlock.withPattern(panel, patternId);
                if (!updated.equals(panel)) {
                    // The pattern lives in the state, so flag two is enough: nothing about the shape
                    // of the system changed, so no neighbor needs to hear about it.
                    level.setBlock(pos, updated, 2);
                }
            }
        }

        // The pattern the system was last put on is the controller's to remember, so a panel put back
        // against it comes up on the same pattern as the wall rather than on whatever is standing next
        // to it. It is only remembered once the panels are actually showing it.
        if (level.getChunkAt(origin).getBlockEntity(origin, LevelChunk.EntityCreationType.CHECK) instanceof RgbControllerBlockEntity controller) {
            controller.rememberPattern(patternId);
        }

        // The gradient patterns spread themselves over a run of panels showing one pattern, and one
        // system can hold several runs, so each of them is measured once. Nothing has moved and no
        // pattern changed which controllers are attached, so the runs are only measured here: the
        // lighting was already settled by the refresh above, and asking again would walk the whole
        // wall once for every run on it.
        HashSet<Long> measured = new HashSet<>();
        for (BlockPos pos : system.panelPositions()) {
            if (measured.add(pos.asLong())) {
                for (BlockPos run : RgbBlock.refreshRun(level, pos, false)) {
                    measured.add(run.asLong());
                }
            }
        }
    }

    /**
     * The pattern a panel placed at {@code pos} should start on, so joining a wall does not break it
     * up. A panel put straight against a controller takes the pattern of the system that controller
     * is running, otherwise it takes the pattern of a panel already next to it.
     */
    public static int systemPattern(Level level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockPos next = pos.relative(direction);
            if (level.isLoaded(next) && level.getBlockState(next).getBlock() instanceof RgbControllerBlock) {
                return referencePattern(level, next);
            }
        }
        for (Direction direction : Direction.values()) {
            BlockPos next = pos.relative(direction);
            if (isPanel(level, next)) {
                return RgbBlock.patternId(level.getBlockState(next));
            }
        }
        return 0;
    }

    /**
     * The pattern a panel put back against this controller should come up on, which is the one its
     * screen last set.
     *
     * <p>What the controller remembers is asked first, because it is the answer that survives: a
     * controller with no panel standing against it has nothing left to read the pattern off, which is
     * exactly the state a controller is in while the wall is apart from it. Only a controller that has
     * never had a pattern set on it falls back to reading one off a panel, and to the state a fresh
     * panel rests in after that.
     */
    public static int referencePattern(Level level, BlockPos controllerPos) {
        if (level.getChunkAt(controllerPos).getBlockEntity(controllerPos, LevelChunk.EntityCreationType.CHECK)
                instanceof RgbControllerBlockEntity controller && controller.hasChosenPattern()) {
            return controller.lastPattern();
        }
        for (Direction direction : Direction.values()) {
            BlockPos next = controllerPos.relative(direction);
            if (isPanel(level, next)) {
                return RgbBlock.patternId(level.getBlockState(next));
            }
        }
        return 0;
    }

    /**
     * True when a panel stands right against this position. Six lookups, and it reads the world rather
     * than anything stored, so a client can ask the same question the server was asked and get the same
     * answer.
     */
    public static boolean panelNeighbor(Level level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (isPanel(level, pos.relative(direction))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPanel(Level level, BlockPos pos) {
        return level.isLoaded(pos) && level.getBlockState(pos).getBlock() instanceof RgbBlock;
    }

    /** The title the screen of a controller carries. */
    public static Component title() {
        return Component.translatable("screen.rgbdeco.rgb_controller");
    }
}
