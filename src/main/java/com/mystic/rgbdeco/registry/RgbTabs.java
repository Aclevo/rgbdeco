package com.mystic.rgbdeco.registry;

import com.mystic.rgbdeco.RgbDeco;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * The creative tab of the mod, holding its blocks and its parts together.
 *
 * <p>A tab is a registry entry in its own right rather than a field on a block, so it is registered like
 * one. Nothing is asked of any vanilla tab, so nothing of this mod is offered anywhere but here.
 *
 * <p>The tab asks for its items through a supplier, so it is built before the item registry is filled and
 * the {@code .get()} calls inside it only run once the game is asking the tab what to show.
 */
public final class RgbTabs {

    /** The creative tab of this mod. */
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, RgbDeco.MODID);

    /**
     * The tab itself, holding the two blocks and the three parts they are built from.
     *
     * <p>Building a tab does not put it anywhere: {@link CreativeModeTab#builder()} only makes the
     * object, and the game finds tabs by walking the creative tab registry, so an unregistered tab is
     * never shown. Registering it is what puts it in the list.
     */
    public static final RegistryObject<CreativeModeTab> TAB = CREATIVE_TABS.register("rgb_deco",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.rgbdeco"))
                    .icon(() -> new ItemStack(RgbBlocks.RGB_BLOCK_ITEM.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(RgbBlocks.RGB_BLOCK_ITEM.get());
                        output.accept(RgbBlocks.RGB_CONTROLLER_ITEM.get());
                        output.accept(RgbItems.LIGHT_BULB.get());
                        output.accept(RgbItems.RGB_LIGHT_BULB.get());
                        output.accept(RgbItems.CONTROL_COMPONENT.get());
                    })
                    .build());

    private RgbTabs() {
    }

    /** Hands the tab to the bus that will actually put it in the game. */
    public static void register(IEventBus modEventBus) {
        CREATIVE_TABS.register(modEventBus);
    }
}