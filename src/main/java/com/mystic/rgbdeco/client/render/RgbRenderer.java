package com.mystic.rgbdeco.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mystic.rgbdeco.RgbDeco;
import com.mystic.rgbdeco.block.RgbBlock;
import com.mystic.rgbdeco.blockentity.RgbBlockEntity;
import com.mystic.rgbdeco.pattern.RgbPattern;
import com.mystic.rgbdeco.system.RgbRun;
import net.minecraft.Util;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;

/**
 * Draws an RGB panel as six faces of {@code rgb_panel.png}, colored by the pattern the block is set
 * to.
 *
 * <p>The texture is the whole face and the pattern is what colors it: every corner of a face is handed
 * the color the pattern has at that point, and the shader multiplies the two. There is no frame drawn
 * separately and no second pass laid over the top, so a panel is one cube of a texture that happens to
 * be lit, and the gray rings and dark corner texels the item is cut into stay part of it and take the
 * color with everything else.
 *
 * <p>That is also what makes it read as a block. A face that is one color at one brightness, frame
 * included, tells the top of the cube from its sides; a frame left at full brightness on every face
 * would look the same whichever way it was turned.
 *
 * <p>The frame is drawn around the run rather than around every block, which is what lets a wall of
 * panels read as one panel instead of a row of tiles. The texture is cut with a one texel border, so a
 * face whose edge carries on into another panel of the same run samples past that border and lets the
 * flat middle of the texture run straight through, and a face whose edge ends the run keeps it. Every
 * panel of a run therefore shows the same frame on the same sides of the construction and none at all
 * between two blocks, and the gradient underneath it is spread over the whole run by
 * {@link PatternColor}, so the two scale together.
 *
 * @see ColoredBoxRenderer
 * @see PatternColor
 */
@OnlyIn(Dist.CLIENT)
public class RgbRenderer extends ColoredBoxRenderer<RgbBlockEntity> {
    /** The item's texture, which is the whole of what a face is made of. */
    public static final ResourceLocation PANEL_TEXTURE = ResourceLocation.fromNamespaceAndPath(RgbDeco.MODID, "textures/block/rgb_panel.png");

    /** How wide the frame the texture is cut with is, in texture space, which is one texel. */
    private static final double FRAME = 1.0D / 16.0D;
    /**
     * What a panel looks like while nothing is driving it: the texture at a low brightness.
     *
     * <p>It is left dark enough to say the panel is out, but not so dark that it stops being a block. A
     * flat near black face is a hole in a wall as far as the eye is concerned, and a wall with holes in
     * it looks like broken geometry rather than like a panel that is switched off. The texture is
     * sampled as it is, so the frame and the shading of the item still make the face out.
     */
    private static final int UNLIT = 0xFF4A4A4A;

    /**
     * The pattern being drawn, kept as an id because the frame has to be compared against the panels
     * next door: two panels of different patterns are two panels, not one run, and the frame between
     * them stays.
     */
    private int pattern;
    /**
     * The box of the run this panel is part of, read from its block entity before the faces are laid
     * out. The gradient patterns spread themselves over the whole of it, so a panel on its own shows
     * the pattern as it was drawn and a big construction shows one sweep across all of it instead of
     * the same sweep repeated on every block.
     */
    private RgbRun run = RgbRun.EMPTY;
    /**
     * Whether a controller is in charge of this panel, which is read from the block state. A panel that
     * is not lit is drawn dark and unpatterned, and gives off no light either, because both come from
     * the same property.
     */
    private boolean lit = true;
    /**
     * Whether the panel over each of the six edges is another panel of this run, indexed by
     * {@link Direction#ordinal()}.
     *
     * <p>Read once per block rather than once per edge asked about. Six faces each ask about four
     * edges, and each of those was a chunk lookup and a block state read, so one block on screen cost
     * thirty of them to answer the same six questions twenty four times over.
     */
    private final boolean[] neighbors = new boolean[6];

    @Override
    public void render(RgbBlockEntity blockEntity, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = blockEntity.getLevel();
        if (level == null) {
            return;
        }
        BlockPos pos = blockEntity.getBlockPos();
        BlockState state = blockEntity.getBlockState();
        this.pattern = RgbBlock.patternId(state);
        this.run = blockEntity.run();
        this.lit = state.getValue(RgbBlock.LIT);
        for (Direction direction : Direction.values()) {
            this.neighbors[direction.ordinal()] = joined(level, pos, direction);
        }

        VertexConsumer consumer = buffers.getBuffer(RenderType.entitySolid(PANEL_TEXTURE));
        pose.pushPose();
        pose.translate(0.5D, 0.5D, 0.5D);
        this.drawBlock(consumer, pose, level, pos, RgbPattern.byId(this.pattern), LevelRenderer.getLightColor(level, pos));
        pose.popPose();
    }

    /**
     * Renders a whole panel.
     *
     * @param level level the neighbors are read from, {@code null} outlines every face
     * @param pos   position of the panel, {@code null} outlines every face
     * @param shown what the panel shows
     */
    public void drawBlock(VertexConsumer consumer, PoseStack pose, @Nullable Level level, @Nullable BlockPos pos,
                          RgbPattern shown, int light) {
        float time = Util.getMillis() * 0.001F;
        for (Direction face : Direction.values()) {
            if (shouldDrawFace(level, pos, face)) {
                this.drawFace(consumer, pose, level, pos, face, shown, time, light);
            }
        }
    }

    /**
     * Two neighboring panels would draw the very same plane, which z fights, so exactly one of the
     * two keeps it: the one that sits on the lower side of the axis they share.
     */
    private static boolean shouldDrawFace(@Nullable Level level, @Nullable BlockPos pos, Direction face) {
        if (level == null || pos == null) {
            return true;
        }
        BlockPos neighbor = pos.relative(face);
        if (!level.isLoaded(neighbor) || !(level.getBlockState(neighbor).getBlock() instanceof RgbBlock)) {
            return true;
        }
        return switch (face.getAxis()) {
            case X -> pos.getX() < neighbor.getX();
            case Y -> pos.getY() < neighbor.getY();
            case Z -> pos.getZ() < neighbor.getZ();
        };
    }

    /**
     * One face, the texture, colored by the pattern, and the frame taken off every edge the run carries
     * on past this block.
     */
    private void drawFace(VertexConsumer consumer, PoseStack pose, @Nullable Level level, @Nullable BlockPos pos,
                          Direction face, RgbPattern shown, float time, int light) {
        // Which way the texture space of this face runs, worked out from the very mapping the face is
        // laid out with rather than written out a second time, so a face that is turned the other way
        // is measured the other way without anybody having to remember it.
        Direction alongU = ColoredBoxRenderer.uAxis(face);
        Direction alongV = ColoredBoxRenderer.vAxis(face);

        // The part of the texture this face shows, which is the flat middle of it and nothing of the
        // border on any edge the run carries on past. The face itself still covers the whole of its side
        // of the block either way: the frame is left off the picture, not off the block.
        Rect texture = new Rect(
                this.neighbors[alongU.getOpposite().ordinal()] ? FRAME : 0.0D,
                this.neighbors[alongV.getOpposite().ordinal()] ? FRAME : 0.0D,
                this.neighbors[alongU.ordinal()] ? 1.0D - FRAME : 1.0D,
                this.neighbors[alongV.ordinal()] ? 1.0D - FRAME : 1.0D);

        // A face is cut into the grid the pattern asks for, so a gradient stays smooth across it and a
        // checker or a set of rings has the cells to live in. The quads tile the face from edge to edge
        // and each one samples the part of the texture it covers, so the texture is never stretched any
        // further than a frame taken off an edge asks for.
        // A panel that is out is not cut at all: one flat tone over the whole of it, and no pattern
        // sampled anywhere, since there is nothing showing to be smooth.
        if (!this.lit) {
            int color = shade(UNLIT, face);
            this.face(consumer, pose, face, Rect.whole(), texture, color, color, color, color, light);
            return;
        }
        // A pattern that does not move is one flat color, so its face is drawn as one quad however fine
        // a mesh it asked for. The grid only exists to give something that changes from one cell to the
        // next somewhere to change in, and a solid dye changes nowhere, so cutting it up was sixteen
        // times the geometry for a picture identical to drawing one quad.
        if (!shown.isAnimated()) {
            this.face(consumer, pose, face, Rect.whole(), texture,
                    shade(PatternColor.at(shown, face, 0.0D, 0.0D, pos, time, this.run), face),
                    shade(PatternColor.at(shown, face, 1.0D, 0.0D, pos, time, this.run), face),
                    shade(PatternColor.at(shown, face, 1.0D, 1.0D, pos, time, this.run), face),
                    shade(PatternColor.at(shown, face, 0.0D, 1.0D, pos, time, this.run), face),
                    light);
            return;
        }
        int grid = shown.grid();
        for (int i = 0; i < grid; i++) {
            for (int j = 0; j < grid; j++) {
                double fromU = (double) i / grid;
                double fromV = (double) j / grid;
                double toU = (double) (i + 1) / grid;
                double toV = (double) (j + 1) / grid;
                // The two rectangles are passed as eight numbers rather than built, because this runs
                // once per cell of every face of every panel on screen and each Rect would be two
                // objects thrown away the moment the quad is out.
                this.face(consumer, pose, face, fromU, fromV, toU, toV,
                        texture.u0() + (texture.u1() - texture.u0()) * fromU,
                        texture.v0() + (texture.v1() - texture.v0()) * fromV,
                        texture.u0() + (texture.u1() - texture.u0()) * toU,
                        texture.v0() + (texture.v1() - texture.v0()) * toV,
                        shade(PatternColor.at(shown, face, fromU, fromV, pos, time, this.run), face),
                        shade(PatternColor.at(shown, face, toU, fromV, pos, time, this.run), face),
                        shade(PatternColor.at(shown, face, toU, toV, pos, time, this.run), face),
                        shade(PatternColor.at(shown, face, fromU, toV, pos, time, this.run), face),
                        light);
            }
        }
    }

    /**
     * True when the panel over that edge is another panel of this same run, which is what makes the
     * frame stop there. A neighbor showing a different pattern is a different run, so the frame stays
     * between the two of them, and a neighbor that is not a panel at all ends the run outright.
     */
    private boolean joined(@Nullable Level level, @Nullable BlockPos pos, Direction direction) {
        if (level == null || pos == null) {
            return false;
        }
        BlockPos next = pos.relative(direction);
        if (!level.isLoaded(next)) {
            return false;
        }
        BlockState state = level.getBlockState(next);
        return state.getBlock() instanceof RgbBlock && RgbBlock.patternId(state) == this.pattern;
    }
}
