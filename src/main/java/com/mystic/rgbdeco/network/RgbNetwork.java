package com.mystic.rgbdeco.network;

import com.mystic.rgbdeco.RgbDeco;
import com.mystic.rgbdeco.block.RgbControllerBlock;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

/**
 * The one channel the mod talks over. It carries two messages from the player and one back: the player
 * picks a pattern or asks the size of a system, and the server answers; and the server opens the screen
 * of a controller that has been right clicked.
 *
 * <p>None of them is trusted on its own. The messages from the player only name a controller, and
 * {@link RgbControllerBlock#applyPattern} and {@link RgbControllerBlock#refreshSystem} work out for
 * themselves whether that controller is there, is close enough, and is allowed to do what was asked.
 */
public final class RgbNetwork {
    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(RgbDeco.MODID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals);

    private RgbNetwork() {
    }

    public static void register() {
        int id = 0;
        RgbNetwork.CHANNEL.registerMessage(id++, SelectPatternPacket.class,
                SelectPatternPacket::encode, SelectPatternPacket::decode, SelectPatternPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        RgbNetwork.CHANNEL.registerMessage(id++, RefreshSystemPacket.class,
                RefreshSystemPacket::encode, RefreshSystemPacket::decode, RefreshSystemPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        RgbNetwork.CHANNEL.registerMessage(id, OpenControllerScreenPacket.class,
                OpenControllerScreenPacket::encode, OpenControllerScreenPacket::decode, OpenControllerScreenPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
}
