package com.mystic.rgbdeco;

import com.mojang.logging.LogUtils;
import com.mystic.rgbdeco.network.RgbNetwork;
import com.mystic.rgbdeco.registry.RgbBlockEntityTypes;
import com.mystic.rgbdeco.registry.RgbBlocks;
import com.mystic.rgbdeco.registry.RgbItems;
import com.mystic.rgbdeco.registry.RgbTabs;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(RgbDeco.MODID)
public class RgbDeco {
    public static final String MODID = "rgbdeco";
    public static final Logger LOGGER = LogUtils.getLogger();

    public RgbDeco(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();
        RgbBlocks.register(modEventBus);
        RgbItems.register(modEventBus);
        RgbTabs.register(modEventBus);
        RgbBlockEntityTypes.register(modEventBus);

        // The two packets of the mod: the pattern a player picks, and the screen the server opens
        RgbNetwork.register();
    }

    /**
     * Sends the block entity's own data to the players who can see it.
     *
     * <p>{@code setChanged()} only marks the chunk for saving, it does not tell anybody: the counts a
     * block entity keeps, the size of a run and the size of a system, therefore stay whatever the
     * client last read from disk unless they are pushed out by hand. A client that was never told
     * draws every panel of a run as if it stood on its own, and reads a controller that reaches no
     * panel, so a screen over it would refuse every click.
     *
     * <p>Radius is the same eight blocks the pattern packet allows, which is well past the furthest a
     * player is sent these blocks anyway.
     */
    public static void syncToClients(Level level, BlockEntity blockEntity) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        BlockPos pos = blockEntity.getBlockPos();
        serverLevel.getServer().getPlayerList().broadcast(null, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                64.0D, serverLevel.dimension(), ClientboundBlockEntityDataPacket.create(blockEntity));
    }
}