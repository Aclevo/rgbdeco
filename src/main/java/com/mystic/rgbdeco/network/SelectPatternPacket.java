package com.mystic.rgbdeco.network;

import com.mystic.rgbdeco.block.RgbControllerBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * The player picked a pattern in the screen of a controller. The message only names where the
 * controller is and which pattern was clicked; whether that controller is allowed to put that pattern
 * on its panels is worked out on the server when the message arrives.
 */
public record SelectPatternPacket(BlockPos pos, int pattern) {

    public static void encode(SelectPatternPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.pos);
        buffer.writeVarInt(packet.pattern);
    }

    public static SelectPatternPacket decode(FriendlyByteBuf buffer) {
        return new SelectPatternPacket(buffer.readBlockPos(), buffer.readVarInt());
    }

    public static void handle(SelectPatternPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                // The screen can be left open and the player can walk off, so the distance is checked
                // here rather than trusting the position the message carried.
                if (player.distanceToSqr(packet.pos.getCenter()) <= 64.0D) {
                    RgbControllerBlock.applyPattern(player.serverLevel(), packet.pos, packet.pattern);
                }
            }
        });
        context.setPacketHandled(true);
    }
}
