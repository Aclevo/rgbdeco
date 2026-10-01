package com.mystic.rgbdeco.registry;

import com.mystic.rgbdeco.RgbDeco;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * The items of the mod that are things in their own right rather than a block in a player's hand.
 *
 * <p>The items that place a block are registered in {@link RgbBlocks} beside the block they place, and
 * this is the other half: the parts a player crafts, holds a stack of, and spends on something else. None
 * of these can be placed, none has a behavior of its own, and all of them exist to be eaten by a recipe,
 * which is why they are plain items rather than blocks that would need a model and a form to stand in.
 */
public final class RgbItems {

    /** The items of this mod, registered under its own namespace. */
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, RgbDeco.MODID);

    /**
     * An ordinary light bulb, which is deliberately not a block and deliberately does nothing.
     *
     * <p>Minecraft has no bulb of its own, so this is one: a thing that looks like the part of a panel
     * that actually shows something and lights nothing at all when it is put down. It exists to be eaten
     * by the RGB bulb recipe, and a player holding one is never in any doubt about that because there is
     * no other use for it.
     */
    public static final RegistryObject<Item> LIGHT_BULB =
            ITEMS.register("light_bulb", () -> new Item(new Item.Properties()));

    /**
     * A light bulb that has been made to take a color rather than to give one out, which is the part of
     * a panel that actually shows something.
     *
     * <p>Crafted out of an ordinary bulb and a redstone torch, which is what the two halves of it are:
     * something to give off light, and something to be told what to do. A panel is three of these, so
     * the cost of a wall is a wall's worth of bulbs rather than a single item, and a player building a
     * long run feels that in the recipe rather than only on a screen.
     */
    public static final RegistryObject<Item> RGB_LIGHT_BULB =
            ITEMS.register("rgb_light_bulb", () -> new Item(new Item.Properties()));

    /**
     * The part of a controller that decides what a system is showing, taken out as an item of its own.
     *
     * <p>A controller is the one block in this mod that reads a screen and writes to other blocks, and a
     * recipe of its own is what says so: it is not a panel with something added, it is a piece of
     * control gear that has been built around one. Crafted out of a comparator and a redstone block,
     * which is the comparison and the signal it does its work from.
     */
    public static final RegistryObject<Item> CONTROL_COMPONENT =
            ITEMS.register("control_component", () -> new Item(new Item.Properties()));

    private RgbItems() {
    }

    /** Hands the items to the bus that will actually put them in the game. */
    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}