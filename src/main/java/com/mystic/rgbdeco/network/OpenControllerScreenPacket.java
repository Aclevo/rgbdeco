package com.mystic.rgbdeco.network;

import com.mystic.rgbdeco.RgbDeco;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * The server answers a right click on a powered controller by opening its screen. Nothing is sent
 * back the other way: the screen reads the block entity and the block states it can already see on the
 * client, which the block entity updates keep current.
 *
 * <p>Nothing in here touches a client class, and that is the whole of what this file has to be careful
 * about. The handling lives in {@link ClientPacketHandler}, which a dedicated server never loads, and
 * this only asks for it once it knows it is on a client: a method reference to a client-only method is
 * resolved when the class is read, so naming it here is enough to break the server even behind a branch.
 */
public record OpenControllerScreenPacket(BlockPos pos) {

    public static void encode(OpenControllerScreenPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.pos);
    }

    public static OpenControllerScreenPacket decode(FriendlyByteBuf buffer) {
        return new OpenControllerScreenPacket(buffer.readBlockPos());
    }

    public static void handle(OpenControllerScreenPacket packet, Supplier<NetworkEvent.Context> supplier) {
        // A server is only ever sent this by mistake or by a client on a version it should have been
        // refused by, so there is nothing to do with it rather than a case to handle.
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.open(packet, supplier));
    }
}