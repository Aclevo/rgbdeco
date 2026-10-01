package com.mystic.rgbdeco.registry;

import com.mystic.rgbdeco.RgbDeco;
import com.mystic.rgbdeco.blockentity.RgbBlockEntity;
import com.mystic.rgbdeco.blockentity.RgbControllerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * The block entity types of the mod, which is what tells the game which block entity belongs to which
 * block and which of them it is allowed to save and send.
 *
 * <p>A separate register from the blocks because it is a registry in its own right. A block is looked up
 * by the game from its name; a block entity type is a third of a thing that belongs to no other block and
 * is asked for by name from the chunk it was saved into, which is why it has to be registered under the
 * block's name and kept in step with it.
 *
 * <p>Each type is built from the block it belongs to rather than being a separate object, because that
 * is what lets the game refuse to load a block entity onto a block that no longer exists.
 */
public final class RgbBlockEntityTypes {

    /** The block entity types of this mod, registered under its own namespace. */
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, RgbDeco.MODID);

    /** What a panel block holds: the box of the run it belongs to, and its own energy. */
    public static final RegistryObject<BlockEntityType<RgbBlockEntity>> RGB_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("rgb_block",
                    () -> BlockEntityType.Builder.of(RgbBlockEntity::new, RgbBlocks.RGB_BLOCK.get()).build(null));

    /** What a controller block holds: the size of its system, its pattern, and its energy. */
    public static final RegistryObject<BlockEntityType<RgbControllerBlockEntity>> RGB_CONTROLLER_ENTITY =
            BLOCK_ENTITIES.register("rgb_controller",
                    () -> BlockEntityType.Builder.of(RgbControllerBlockEntity::new, RgbBlocks.RGB_CONTROLLER.get()).build(null));

    private RgbBlockEntityTypes() {
    }

    /** Hands the types to the bus that will actually put them in the game. */
    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }
}