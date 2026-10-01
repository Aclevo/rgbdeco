package com.mystic.rgbdeco.client;

import com.mystic.rgbdeco.RgbDeco;
import com.mystic.rgbdeco.client.render.RgbControllerRenderer;
import com.mystic.rgbdeco.client.render.RgbRenderer;
import com.mystic.rgbdeco.registry.RgbBlockEntityTypes;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = RgbDeco.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientEvents {
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(RgbBlockEntityTypes.RGB_BLOCK_ENTITY.get(), type -> new RgbRenderer());
        event.registerBlockEntityRenderer(RgbBlockEntityTypes.RGB_CONTROLLER_ENTITY.get(), type -> new RgbControllerRenderer());
    }
}