package com.mystic.rgbdeco.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mystic.rgbdeco.RgbDeco;
import com.mystic.rgbdeco.block.RgbControllerBlock;
import com.mystic.rgbdeco.blockentity.RgbControllerBlockEntity;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Draws a controller as six faces of {@code rgb_controller.png}, with the status color put only on the
 * lines of that texture.
 *
 * <p>The item is cut into two kinds of area, and only one of them takes the status. The near black
 * lines, which are the frame around the whole thing, the bar across the middle and the divider down
 * the middle of it, are where the block is read. The top light and the two recessed panels below it
 * are left exactly as the item draws them, so a controller keeps the face of the object it is and only
 * its outline changes. That is also what keeps it tellable from a panel at a glance, since a panel is
 * colored across its whole face and a controller is not.
 *
 * <p>The frame is drawn against a flat white texture rather than against the item's own, and that is the
 * whole reason the status can be read off the block at all. The lines in the item are painted almost
 * black, so tinting them in place would multiply the status down to a shade of nothing: a green frame
 * on a near black texel comes out almost black, and so does red. The shape of the frame is geometry
 * here rather than texture, so the color of those texels is thrown away and the status drawn over the
 * top at full strength, which is what a frame that has to be unmistakable should look like. The
 * recessed panels are still drawn with the item's own texture, since nothing is painted over them.
 *
 * <p>One flat color on a face is what tells the top of the block from its sides, so the status is
 * dimmed by the amount the face keeps before it is handed over.
 *
 * @see RgbControllerBlockEntity#displayColor(Level)
 * @see ColoredBoxRenderer
 */
@OnlyIn(Dist.CLIENT)
public class RgbControllerRenderer extends ColoredBoxRenderer<RgbControllerBlockEntity> {
    /** The item's texture, which is the whole of what the untouched parts of a face are made of. */
    public static final ResourceLocation CONTROLLER_TEXTURE = ResourceLocation.fromNamespaceAndPath(RgbDeco.MODID, "textures/block/rgb_controller.png");
    /** A texture that is white all over, so a frame drawn with it is the color it is given. */
    private static final ResourceLocation LINE_TEXTURE = ResourceLocation.fromNamespaceAndPath(RgbDeco.MODID, "textures/block/rgb_white.png");
    /** How much of its own brightness a face keeps when the texture is drawn untouched. */
    private static final int UNTOUCHED = 0xFFFFFFFF;

    /** Width of one texel of the item texture, which is the unit the frame is measured in. */
    private static final double T = 1.0D / 16.0D;

    /**
     * The areas of the texture that stay as the item draws them: the light bar across the top and the two
     * panels the divider splits the lower half into. A controller is never joined to another one, so what
     * a face covers and what it shows are the same area here.
     */
    private static final Rect[] PANELS = {
            new Rect(T, T, T * 15.0D, T * 4.0D),
            new Rect(T, T * 5.0D, T * 8.0D, T * 15.0D),
            new Rect(T * 9.0D, T * 5.0D, T * 15.0D, T * 15.0D),
    };

    /**
     * The lines of the texture, which take the status: the frame right around the edge, the bar across
     * the middle, and the divider standing on it. Every one of them is a run of texels the item paints
     * in the same near black, which is what makes them read as one frame.
     */
    private static final Rect[] LINES = {
            new Rect(0.0D, 0.0D, 1.0D, T),
            new Rect(0.0D, T * 15.0D, 1.0D, 1.0D),
            new Rect(0.0D, T, T, T * 15.0D),
            new Rect(T * 15.0D, T, 1.0D, T * 15.0D),
            new Rect(0.0D, T * 4.0D, 1.0D, T * 5.0D),
            new Rect(T * 8.0D, T * 5.0D, T * 9.0D, T * 15.0D),
    };

    @Override
    public void render(RgbControllerBlockEntity controller, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = controller.getLevel();
        if (level == null) {
            return;
        }
        BlockPos pos = controller.getBlockPos();
        int status = controller.displayColor(level);

        VertexConsumer textured = buffers.getBuffer(RenderType.entitySolid(CONTROLLER_TEXTURE));
        VertexConsumer frame = buffers.getBuffer(RenderType.entitySolid(LINE_TEXTURE));
        pose.pushPose();
        pose.translate(0.5D, 0.5D, 0.5D);
        int lightColor = LevelRenderer.getLightColor(level, pos);
        for (Direction face : Direction.values()) {
            if (shouldDrawFace(level, pos, face)) {
                this.drawFace(textured, frame, pose, face, status, lightColor);
            }
        }
        pose.popPose();
    }

    /**
     * One face, as the two kinds of area the item is cut into. The areas do not overlap and they cover
     * the face between them, so there is nothing z fighting however the two passes come out.
     */
    private void drawFace(VertexConsumer textured, VertexConsumer frame, PoseStack pose, Direction face, int status, int light) {
        for (Rect panel : PANELS) {
            this.face(textured, pose, face, panel, panel, UNTOUCHED, UNTOUCHED, UNTOUCHED, UNTOUCHED, light);
        }
        int color = shade(status, face);
        for (Rect line : LINES) {
            // The white texture is white everywhere, so which part of it a line samples makes no
            // difference and the line's own coordinates are kept, to say which line this is.
            this.face(frame, pose, face, line, line, color, color, color, color, light);
        }
    }

    /**
     * Two controllers side by side would draw the very same plane, which z fights, so exactly one of the
     * two keeps it: the one that sits on the lower side of the axis they share.
     */
    private static boolean shouldDrawFace(Level level, BlockPos pos, Direction face) {
        BlockPos neighbor = pos.relative(face);
        if (!level.isLoaded(neighbor) || !(level.getBlockState(neighbor).getBlock() instanceof RgbControllerBlock)) {
            return true;
        }
        return switch (face.getAxis()) {
            case X -> pos.getX() < neighbor.getX();
            case Y -> pos.getY() < neighbor.getY();
            case Z -> pos.getZ() < neighbor.getZ();
        };
    }
}
