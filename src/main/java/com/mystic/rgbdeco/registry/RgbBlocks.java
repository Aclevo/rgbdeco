package com.mystic.rgbdeco.registry;

import com.mystic.rgbdeco.RgbDeco;
import com.mystic.rgbdeco.block.RgbBlock;
import com.mystic.rgbdeco.block.RgbControllerBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Every block of the mod, and the items that place them.
 *
 * <p>The item of a block lives here rather than in {@link RgbItems} because it is not a thing of its
 * own: it is the same block in a player's hand, registered under the same name, and splitting the two
 * across files would mean a block could only be read once both files had been understood. The items a
 * player crafts and carries in their own right are the ones in {@link RgbItems}.
 *
 * <p>All of it is a {@link DeferredRegister}, which only records what should exist and fills the real
 * registries once Forge has finished loading classes. That is why every supplier below is a lambda:
 * {@code RGB_BLOCK.get()} cannot be answered while this class is still being read, and a block that asked
 * for its own texture here would run before there was a screen to ask.
 */
public final class RgbBlocks {

    /** The blocks of this mod, registered under its own namespace. */
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, RgbDeco.MODID);

    /**
     * The RGB panel: the renderer draws it, and the controller beside it is what decides its pattern.
     * A panel only gives off light while a controller is in charge of it, so a panel that is out is out
     * of the light as well as out of the color.
     */
    public static final RegistryObject<RgbBlock> RGB_BLOCK = BLOCKS.register("rgb_block",
            () -> new RgbBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_RED)
                    .strength(2.0F, 6.0F)
                    .sound(SoundType.STONE)
                    .lightLevel(state -> state.getValue(RgbBlock.LIT) ? 15 : 0)));

    /** The panel as an item, registered under the same name as the block it places. */
    public static final RegistryObject<Item> RGB_BLOCK_ITEM =
            RgbItems.ITEMS.register("rgb_block", () -> new BlockItem(RGB_BLOCK.get(), new Item.Properties()));

    /**
     * The controller: it holds Forge energy, filled by a cable or a cell, and while it holds any the
     * screen opens and the screen sets the pattern of every panel it reaches.
     *
     * <p>No light level on purpose, so a controller never lights up a wall of its own and the panels stay
     * the only light in the build.
     */
    public static final RegistryObject<RgbControllerBlock> RGB_CONTROLLER = BLOCKS.register("rgb_controller",
            () -> new RgbControllerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.QUARTZ)
                    .strength(1.5F, 6.0F)
                    .sound(SoundType.STONE)));

    /** The controller as an item, registered under the same name as the block it places. */
    public static final RegistryObject<Item> RGB_CONTROLLER_ITEM =
            RgbItems.ITEMS.register("rgb_controller", () -> new BlockItem(RGB_CONTROLLER.get(), new Item.Properties()));

    private RgbBlocks() {
    }

    /** Hands the blocks to the bus that will actually put them in the game. */
    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}