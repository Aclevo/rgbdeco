package com.mystic.rgbdeco.network;

import com.mystic.rgbdeco.block.RgbControllerBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * A player opened the screen of a controller and wants to be told what its system looks like.
 *
 * <p>The screen shows the size of the system, which the server measured, and the client only holds a
 * copy of that number. The copy is pushed out whenever the system changes, but a player who opens a
 * screen on a controller that has been standing there a while can be looking at a copy from before they
 * came back to it. Asking for it again costs one walk of a system the player is standing next to, and
 * it is the only way the number on the screen can be the server's own answer rather than a copy that
 * might be behind.
 *
 * <p>Nothing here is trusted: the position is checked against where the player is standing, and
 * {@link RgbControllerBlock#refreshSystem} works out for itself what it is looking at.
 */
public record RefreshSystemPacket(BlockPos pos) {

    public static void encode(RefreshSystemPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.pos);
    }

    public static RefreshSystemPacket decode(FriendlyByteBuf buffer) {
        return new RefreshSystemPacket(buffer.readBlockPos());
    }

    public static void handle(RefreshSystemPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                // The screen can be left open and the player can walk off, so the distance is checked
                // here rather than trusting the position the message carried.
                if (player.distanceToSqr(packet.pos.getCenter()) <= 64.0D) {
                    // Measures the system and pushes the answer out whether it moved or not, which is
                    // what fills in the numbers the screen is showing. Asking for a number that the
                    // server already has right is the whole point of asking, and a count that has not
                    // changed would otherwise never be sent to the copy of it that is wrong.
                    RgbControllerBlock.refreshSystem(player.serverLevel(), packet.pos, true);
                }
            }
        });
        context.setPacketHandled(true);
    }
}
