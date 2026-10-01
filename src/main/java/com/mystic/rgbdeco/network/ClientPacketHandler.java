package com.mystic.rgbdeco.network;

import com.mystic.rgbdeco.client.RgbControllerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * The client half of {@link OpenControllerScreenPacket}, and the only place in the mod that reaches for a
 * client class from a message handler.
 *
 * <p>This is a class of its own rather than a method on the packet because that is what keeps the server
 * working. A client-only method on a class the server does load is not enough: Forge strips it, so
 * anything holding a reference to it — the registration in {@link RgbNetwork} above all — fails to link
 * and the server dies on startup with a missing method rather than with anything to do with a screen. A
 * class is only loaded when it is named, so putting the client class behind one that only a client ever
 * names is what makes this safe, and {@link OpenControllerScreenPacket} asks for it through
 * {@link net.minecraftforge.fml.DistExecutor} so even that naming happens on the client only.
 *
 * <p>Kept out of the client package because this is the network's business and lives next to the packet
 * it answers, rather than in the place the rest of the drawing code is.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientPacketHandler {

    private ClientPacketHandler() {
    }

    /**
     * Opens the screen of the controller the packet names.
     *
     * <p>Hands the work back to the client's own thread rather than opening it here: a message arrives on
     * a network thread, and a screen is built and thrown away on whichever thread is drawing.
     */
    public static void open(OpenControllerScreenPacket packet, Supplier<NetworkEvent.Context> supplier) {
        BlockPos pos = packet.pos();
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> Minecraft.getInstance().setScreen(new RgbControllerScreen(pos)));
        context.setPacketHandled(true);
    }
}