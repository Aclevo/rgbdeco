package com.mystic.rgbdeco.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The vertex colored box both of the mod's block entity renderers draw with.
 *
 * <p>A block entity renderer is handed a pose stack sitting on the block's own corner, so a renderer
 * shifts it half a block along each axis first. The box itself is then laid out from {@code -0.5} to
 * {@code +0.5} and every face carries its own {@code [0, 1]} texture space, laid out the way the
 * model of a block lays one out: {@code u} runs to the right of somebody standing outside the face and
 * {@code v} runs downwards, with {@code [0, 0]} at the top left of the face as it is seen.
 *
 * <p>A face is one textured quad in vertex colors: the block texture is sampled over the whole face
 * and the color each corner is given multiplies it, which is how the two blocks are painted. A panel
 * hands over the color of its pattern, a controller the color of its status, and the texture underneath
 * is the frame the item is cut into. Tinting rather than laying a second pass over the texture keeps
 * the whole face, frame included, at one brightness for one face, which is what tells the top of a
 * block from its sides.
 *
 * <p>Nothing here reads the world: what a block looks like is decided by the subclass, which only has
 * to say which color each corner of a face gets. One instance is only ever touched by the render
 * thread, which is what lets the scratch buffers below be reused from quad to quad.
 *
 * <p>Where a quad sits and which part of the texture it shows are two separate things here, and a face
 * always covers the whole of its side of the block. A subclass is free to sample a smaller piece of the
 * texture across it, which is how a frame is taken off an edge that carries on into a panel next door,
 * and doing so never leaves a gap in the block.
 */
@OnlyIn(Dist.CLIENT)
public abstract class ColoredBoxRenderer<B extends BlockEntity> implements BlockEntityRenderer<B> {
    private static final double SHADE_UP = 1.0D;
    private static final double SHADE_DOWN = 0.55D;
    private static final double SHADE_SIDE = 0.8D;

    /**
     * Where the four corners of the quad in flight are, one array per coordinate.
     *
     * <p>Three arrays of four doubles rather than an array of four {@link Vec3}, because a face cut
     * into the mesh its pattern asks for is drawn as hundreds of quads and every one of them used to
     * allocate seven objects: four corners, and the three that working out the winding needs. A wall of
     * sixteen grid patterns is a few thousand quads a block, so that was tens of thousands of short
     * lived objects a frame per block on screen, which is what the collector spends the frame on
     * rather than the block being drawn.
     */
    private final double[] cornerX = new double[4];
    private final double[] cornerY = new double[4];
    private final double[] cornerZ = new double[4];
    private final float[] uvs = new float[8];
    private final int[] colors = new int[4];

    /**
     * A rectangle of a face, in the {@code [0, 1]} texture space that face carries: {@code u0, v0} at
     * one corner and {@code u1, v1} at the other.
     */
    public record Rect(double u0, double v0, double u1, double v1) {
        /** The whole of a face, which is as much of a block as a face ever covers. */
        public static Rect whole() {
            return new Rect(0.0D, 0.0D, 1.0D, 1.0D);
        }

        /** The part of this rectangle between two fractions of it, which is how a face is cut into cells. */
        public Rect slice(double fromU, double fromV, double toU, double toV) {
            return new Rect(
                    this.u0 + (this.u1 - this.u0) * fromU, this.v0 + (this.v1 - this.v0) * fromV,
                    this.u0 + (this.u1 - this.u0) * toU, this.v0 + (this.v1 - this.v0) * toV);
        }

        /** True for a rectangle with no area in it, which has nothing to draw. */
        public boolean empty() {
            return this.u1 <= this.u0 || this.v1 <= this.v0;
        }
    }

    /**
     * Emits one quad of a face, given where on the block the quad sits and which part of the texture it
     * shows.
     *
     * <p>The two are deliberately not the same rectangle. Where a quad sits is a question about the block
     * and is always answered with the whole of the face it belongs to, while which part of the texture it
     * samples is a question about how the block is drawn and is what taking a frame off an edge or cutting
     * a face into cells changes. Letting the two drift together is how a panel came to be a wall with
     * holes in it: dropping a frame off an edge pulled the quad in by a texel as well as moving its
     * texture, and the neighboring panel had already had the same plane culled, so the strip in between
     * was simply never drawn by anything.
     *
     * <p>The four colors are given corner by corner, in the order the corners are laid out in, and each one
     * multiplies the texture where that corner sits. A caller that wants a flat color passes the same one
     * four times.
     */
    protected void face(VertexConsumer consumer, PoseStack pose, Direction face, Rect geometry, Rect texture,
                        int c0, int c1, int c2, int c3, int light) {
        this.face(consumer, pose, face,
                geometry.u0(), geometry.v0(), geometry.u1(), geometry.v1(),
                texture.u0(), texture.v0(), texture.u1(), texture.v1(),
                c0, c1, c2, c3, light);
    }

    /**
     * The same quad, with both of its rectangles given as eight numbers rather than as two objects.
     *
     * <p>A face cut into its pattern's mesh asks for this once per cell, so the pair of {@link Rect}s
     * the other form takes was two throwaway objects per quad, and the cut face of one panel is up to
     * a thousand quads.
     */
    protected void face(VertexConsumer consumer, PoseStack pose, Direction face,
                        double gu0, double gv0, double gu1, double gv1,
                        double tu0, double tv0, double tu1, double tv1,
                        int c0, int c1, int c2, int c3, int light) {
        if (gu1 <= gu0 || gv1 <= gv0 || tu1 <= tu0 || tv1 <= tv0) {
            return;
        }
        this.corner(0, face, gu0, gv0);
        this.corner(1, face, gu1, gv0);
        this.corner(2, face, gu1, gv1);
        this.corner(3, face, gu0, gv1);
        this.uvs[0] = (float) tu0;
        this.uvs[1] = (float) tv0;
        this.uvs[2] = (float) tu1;
        this.uvs[3] = (float) tv0;
        this.uvs[4] = (float) tu1;
        this.uvs[5] = (float) tv1;
        this.uvs[6] = (float) tu0;
        this.uvs[7] = (float) tv1;
        this.colors[0] = c0;
        this.colors[1] = c1;
        this.colors[2] = c2;
        this.colors[3] = c3;
        // A texture space with v running downwards means walking the corners in order, right along
        // the top and then down the side, turns the wrong way round as seen from outside the face,
        // which is the winding the rasteriser culls. Trading the two opposite corners puts the
        // winding back the right way round and only re-picks the diagonal of the same rectangle, so
        // it holds for every face the same way however the face happens to be turned.
        if (winding(face) <= 0.0D) {
            double swap = this.cornerX[1];
            this.cornerX[1] = this.cornerX[3];
            this.cornerX[3] = swap;
            swap = this.cornerY[1];
            this.cornerY[1] = this.cornerY[3];
            this.cornerY[3] = swap;
            swap = this.cornerZ[1];
            this.cornerZ[1] = this.cornerZ[3];
            this.cornerZ[3] = swap;
            int color = this.colors[1];
            this.colors[1] = this.colors[3];
            this.colors[3] = color;
            // The texture coordinates travel with the corner they belong to, so the two the swap
            // touches have to trade places as well or the quad would be textured the wrong way round.
            for (int i = 0; i < 2; i++) {
                float coordinate = this.uvs[2 + i];
                this.uvs[2 + i] = this.uvs[6 + i];
                this.uvs[6 + i] = coordinate;
            }
        }

        // Four vertices, because the render type this is drawn with asks the buffer builder for quads
        // rather than triangles. Handing it six would have it read the first four as a self
        // overlapping bowtie and glue the leftover two onto the next face, which is what turned a
        // panel into loose triangles with holes in them. The builder picks the diagonal and the
        // winding; the four corners only have to be in order around the rectangle.
        float nx = face.getStepX();
        float ny = face.getStepY();
        float nz = face.getStepZ();
        this.vertex(consumer, pose, 0, nx, ny, nz, light);
        this.vertex(consumer, pose, 1, nx, ny, nz, light);
        this.vertex(consumer, pose, 2, nx, ny, nz, light);
        this.vertex(consumer, pose, 3, nx, ny, nz, light);
    }

    /**
     * Puts one corner of the quad in flight where {@link #point(Direction, double, double)} would put
     * it, without building the {@link Vec3} to hold it.
     */
    private void corner(int index, Direction face, double u, double v) {
        // The same six cases as point(), written straight into the arrays. A quad asks for four
        // corners, so this runs four times for every quad drawn and the Vec3 it used to build was
        // four allocations of garbage per quad, tens of thousands a frame on a wall of them.
        switch (face) {
            case NORTH -> {
                this.cornerX[index] = 0.5D - u;
                this.cornerY[index] = 0.5D - v;
                this.cornerZ[index] = -0.5D;
            }
            case SOUTH -> {
                this.cornerX[index] = u - 0.5D;
                this.cornerY[index] = 0.5D - v;
                this.cornerZ[index] = 0.5D;
            }
            case WEST -> {
                this.cornerX[index] = -0.5D;
                this.cornerY[index] = 0.5D - v;
                this.cornerZ[index] = u - 0.5D;
            }
            case EAST -> {
                this.cornerX[index] = 0.5D;
                this.cornerY[index] = 0.5D - v;
                this.cornerZ[index] = 0.5D - u;
            }
            case UP -> {
                this.cornerX[index] = u - 0.5D;
                this.cornerY[index] = 0.5D;
                this.cornerZ[index] = v - 0.5D;
            }
            case DOWN -> {
                this.cornerX[index] = u - 0.5D;
                this.cornerY[index] = -0.5D;
                this.cornerZ[index] = 0.5D - v;
            }
        }
    }

    /**
     * How the quad in flight is wound as seen from outside its face, which is what tells the
     * rasteriser which way round to fill it.
     *
     * <p>It is the cross product of the two edges leaving the first corner, dotted with the way the face
     * points, worked out on the raw doubles rather than through {@link Vec3#subtract(Vec3)} and
     * {@link Vec3#cross(Vec3)}, which allocate an object apiece to answer a question three multiplications
     * answers.
     */
    private double winding(Direction face) {
        double ax = this.cornerX[1] - this.cornerX[0];
        double ay = this.cornerY[1] - this.cornerY[0];
        double az = this.cornerZ[1] - this.cornerZ[0];
        double bx = this.cornerX[3] - this.cornerX[0];
        double by = this.cornerY[3] - this.cornerY[0];
        double bz = this.cornerZ[3] - this.cornerZ[0];
        return (ay * bz - az * by) * face.getStepX()
                + (az * bx - ax * bz) * face.getStepY()
                + (ax * by - ay * bx) * face.getStepZ();
    }

    private void vertex(VertexConsumer consumer, PoseStack pose, int corner, float nx, float ny, float nz, int light) {
        int color = this.colors[corner];
        consumer.vertex(pose.last().pose(),
                (float) this.cornerX[corner], (float) this.cornerY[corner], (float) this.cornerZ[corner])
                .color(color >> 16 & 0xFF, color >> 8 & 0xFF, color & 0xFF, color >>> 24)
                .uv(this.uvs[corner * 2], this.uvs[corner * 2 + 1])
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(pose.last().normal(), nx, ny, nz)
                .endVertex();
    }

    /** How much of its own brightness a face keeps, which is what tells the top from the bottom. */
    protected static double faceShade(Direction face) {
        return switch (face.getAxis()) {
            case Y -> face == Direction.UP ? SHADE_UP : SHADE_DOWN;
            case X, Z -> SHADE_SIDE;
        };
    }

    /** How bright a face of the box is, which is what tells the top from the bottom. */
    protected static int shade(int color, Direction face) {
        return PatternColor.scale(color, faceShade(face));
    }

    /**
     * Maps the {@code [0, 1]} texture coordinates of a face onto the block, centered on its origin.
     *
     * <p>Each case is written the same way round: {@code u} moves along the direction that is to the
     * right of a player looking at that face from outside it, and {@code v} moves along the direction
     * that is downwards for them. That is the layout a block model uses, so a texture lands the way it
     * was drawn instead of mirrored on half the faces and upside down on the other half.
     */
    protected static Vec3 point(Direction face, double u, double v) {
        return switch (face) {
            case NORTH -> new Vec3(0.5D - u, 0.5D - v, -0.5D);
            case SOUTH -> new Vec3(u - 0.5D, 0.5D - v, 0.5D);
            case WEST -> new Vec3(-0.5D, 0.5D - v, u - 0.5D);
            case EAST -> new Vec3(0.5D, 0.5D - v, 0.5D - u);
            case UP -> new Vec3(u - 0.5D, 0.5D, v - 0.5D);
            case DOWN -> new Vec3(u - 0.5D, -0.5D, 0.5D - v);
        };
    }

    /**
     * Which way along the world the {@code u} of a face runs, for every face at once.
     *
     * <p>A face's own mapping moves along exactly one axis, so there is only ever one answer per face
     * and there are only six faces. Working them out from the mapping rather than writing them out a
     * second time keeps the two from drifting apart, and doing it once here rather than on every call
     * matters because the renderer asks for this once per vertex: a wall of sixteen grid patterns is
     * six thousand vertices a block, and the three {@link Vec3} each answer used to cost came to
     * tens of thousands of short lived objects a frame.
     */
    private static final Direction[] U_AXIS = new Direction[6];
    /** The same for the {@code v} of a face, which runs downwards as seen from outside it. */
    private static final Direction[] V_AXIS = new Direction[6];

    static {
        for (Direction face : Direction.values()) {
            U_AXIS[face.ordinal()] = axisOf(face, 1.0D, 0.0D);
            V_AXIS[face.ordinal()] = axisOf(face, 0.0D, 1.0D);
        }
    }

    /**
     * Which way along the world the {@code u} of a face runs, worked out from where the mapping put two
     * points of it rather than written out a second time. The mapping always moves along one axis and no
     * other, so there is only ever one answer, however the face happens to be turned.
     */
    public static Direction uAxis(Direction face) {
        return U_AXIS[face.ordinal()];
    }

    /** The same for the {@code v} of a face, which runs downwards as seen from outside it. */
    public static Direction vAxis(Direction face) {
        return V_AXIS[face.ordinal()];
    }

    /** Which single axis {@link #point(Direction, double, double)} moves along between those two points. */
    private static Direction axisOf(Direction face, double u, double v) {
        Vec3 from = point(face, 0.0D, 0.0D);
        Vec3 to = point(face, u, v);
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        if (dx > 0.0D) {
            return Direction.EAST;
        }
        if (dx < 0.0D) {
            return Direction.WEST;
        }
        if (dy > 0.0D) {
            return Direction.UP;
        }
        if (dy < 0.0D) {
            return Direction.DOWN;
        }
        if (dz > 0.0D) {
            return Direction.SOUTH;
        }
        if (dz < 0.0D) {
            return Direction.NORTH;
        }
        throw new IllegalStateException("A face of a block does not run anywhere");
    }
}
