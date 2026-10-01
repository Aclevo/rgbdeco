package com.mystic.rgbdeco.pattern;

import com.mystic.rgbdeco.block.RgbBlock;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;

import javax.annotation.Nullable;
import java.util.Locale;

/**
 * Every texture state an {@link RgbBlock} can show. A block walks through this list one entry per
 * redstone pulse, wrapping around at the end.
 *
 * <p>The list has three groups:
 * <ul>
 *   <li>the solid dyes, read straight out of {@link DyeColor} so they are exactly the colors the
 *       rest of the game dyes with, ordered so the cycle walks the spectrum and ends on the
 *       neutrals. {@link #BLACK} comes first because it is the state a panel sits in until it is
 *       pulsed for the first time;</li>
 *   <li>the rainbow, which sweeps the chromatic dyes;</li>
 *   <li>the monochromatic patterns, the same motions in shades of gray. White is achromatic, so it
 *       belongs to this family rather than to the rainbow, though it is a solid color as well.</li>
 * </ul>
 *
 * <p>Both animated families come in one pattern per {@link RgbMotion}, so every motion can be seen in
 * full color and in black and white. The four gradient motions also come in one pattern per
 * {@link RgbDirection}, so a run of panels can be pointed left, right, up or down across a face instead
 * of being stuck on one way of running. The animated variants store {@code -1} as their color and are
 * computed per vertex by the client renderer, which keeps the animation continuous across connected
 * blocks.
 *
 * <p>The order of this list is the id a panel stores, so entries are added rather than inserted and a
 * pattern is never renamed in place. Changing the list itself does move everything after it, which turns
 * the walls of anyone who has panels down by an earlier version into different patterns; that is a
 * deliberate cost of the list being changed and not something to work around quietly.
 */
public enum RgbPattern {
    /** The state a panel rests in, and the dark end of the monochromatic sweep. */
    BLACK(DyeColor.BLACK, 4),

    RED(DyeColor.RED, 4),
    ORANGE(DyeColor.ORANGE, 4),
    YELLOW(DyeColor.YELLOW, 4),
    LIME(DyeColor.LIME, 4),
    GREEN(DyeColor.GREEN, 4),
    CYAN(DyeColor.CYAN, 4),
    LIGHT_BLUE(DyeColor.LIGHT_BLUE, 4),
    BLUE(DyeColor.BLUE, 4),
    PURPLE(DyeColor.PURPLE, 4),
    MAGENTA(DyeColor.MAGENTA, 4),
    PINK(DyeColor.PINK, 4),
    WHITE(DyeColor.WHITE, 4),
    LIGHT_GRAY(DyeColor.LIGHT_GRAY, 4),
    GRAY(DyeColor.GRAY, 4),
    BROWN(DyeColor.BROWN, 4),

    /** Rainbow flowing across every face of the block, one pattern per way it can be pointed. */
    RAINBOW_LEFT(-1, 4, false, RgbMotion.FLOW, RgbDirection.LEFT),
    RAINBOW_RIGHT(-1, 4, false, RgbMotion.FLOW, RgbDirection.RIGHT),
    RAINBOW_UP(-1, 4, false, RgbMotion.FLOW, RgbDirection.UP),
    RAINBOW_DOWN(-1, 4, false, RgbMotion.FLOW, RgbDirection.DOWN),

    /** The same flow, frozen where it stands. */
    RAINBOW_STILL_LEFT(-1, 4, false, RgbMotion.STILL, RgbDirection.LEFT),
    RAINBOW_STILL_RIGHT(-1, 4, false, RgbMotion.STILL, RgbDirection.RIGHT),
    RAINBOW_STILL_UP(-1, 4, false, RgbMotion.STILL, RgbDirection.UP),
    RAINBOW_STILL_DOWN(-1, 4, false, RgbMotion.STILL, RgbDirection.DOWN),

    /** Wide horizontal rainbow bands that scroll along the direction, like a color gradient wall. */
    RAINBOW_BANDS_LEFT(-1, 4, false, RgbMotion.BANDS, RgbDirection.LEFT),
    RAINBOW_BANDS_RIGHT(-1, 4, false, RgbMotion.BANDS, RgbDirection.RIGHT),
    RAINBOW_BANDS_UP(-1, 4, false, RgbMotion.BANDS, RgbDirection.UP),
    RAINBOW_BANDS_DOWN(-1, 4, false, RgbMotion.BANDS, RgbDirection.DOWN),

    /** Rainbow waves running along the direction, rising and falling as they scroll. */
    RAINBOW_WAVE_LEFT(-1, 8, false, RgbMotion.WAVE, RgbDirection.LEFT),
    RAINBOW_WAVE_RIGHT(-1, 8, false, RgbMotion.WAVE, RgbDirection.RIGHT),
    RAINBOW_WAVE_UP(-1, 8, false, RgbMotion.WAVE, RgbDirection.UP),
    RAINBOW_WAVE_DOWN(-1, 8, false, RgbMotion.WAVE, RgbDirection.DOWN),

    /** A checkerboard of three rainbow stops over the whole run, flipping as it scrolls. */
    RAINBOW_CHECKER(-1, 8, false, RgbMotion.CHECKER),
    /** A rainbow wheel of wedges, turning round the middle of the run. */
    RAINBOW_SPIN(-1, 8, false, RgbMotion.SPIN),
    /** Rings of rainbow expanding out of the middle of the run. */
    RAINBOW_RINGS(-1, 8, false, RgbMotion.RINGS),
    /** A rainbow bar on both of a face's directions at once, reading as a cross over the run. */
    RAINBOW_CROSS(-1, 8, false, RgbMotion.CROSS),
    /** One bright rainbow bar sweeping left to right across the run. */
    RAINBOW_SCAN_HORIZONTAL(-1, 8, false, RgbMotion.SCAN_HORIZONTAL),
    /** One bright rainbow bar sweeping top to bottom across the run. */
    RAINBOW_SCAN_VERTICAL(-1, 8, false, RgbMotion.SCAN_VERTICAL),
    /** Cells twinkling on their own clocks across the run, each holding its own rainbow stop. */
    RAINBOW_SPARKLE(-1, 8, false, RgbMotion.SPARKLE),
    /** One hue for the whole block, slowly cycling and pulsing in brightness. */
    RAINBOW_PULSE(-1, 4, false, RgbMotion.PULSE),

    /** The rainbow, in shades of gray from black up to white, one pattern per way it can be pointed. */
    MONOCHROMATIC_LEFT(-1, 4, true, RgbMotion.FLOW, RgbDirection.LEFT),
    MONOCHROMATIC_RIGHT(-1, 4, true, RgbMotion.FLOW, RgbDirection.RIGHT),
    MONOCHROMATIC_UP(-1, 4, true, RgbMotion.FLOW, RgbDirection.UP),
    MONOCHROMATIC_DOWN(-1, 4, true, RgbMotion.FLOW, RgbDirection.DOWN),

    /** The same flow in gray, frozen where it stands. */
    MONOCHROMATIC_STILL_LEFT(-1, 4, true, RgbMotion.STILL, RgbDirection.LEFT),
    MONOCHROMATIC_STILL_RIGHT(-1, 4, true, RgbMotion.STILL, RgbDirection.RIGHT),
    MONOCHROMATIC_STILL_UP(-1, 4, true, RgbMotion.STILL, RgbDirection.UP),
    MONOCHROMATIC_STILL_DOWN(-1, 4, true, RgbMotion.STILL, RgbDirection.DOWN),

    /** Scrolling bands of gray, running black to white and back. */
    MONOCHROMATIC_BANDS_LEFT(-1, 4, true, RgbMotion.BANDS, RgbDirection.LEFT),
    MONOCHROMATIC_BANDS_RIGHT(-1, 4, true, RgbMotion.BANDS, RgbDirection.RIGHT),
    MONOCHROMATIC_BANDS_UP(-1, 4, true, RgbMotion.BANDS, RgbDirection.UP),
    MONOCHROMATIC_BANDS_DOWN(-1, 4, true, RgbMotion.BANDS, RgbDirection.DOWN),

    /** Waves of gray running along the direction, rising and falling as they scroll. */
    MONOCHROMATIC_WAVE_LEFT(-1, 8, true, RgbMotion.WAVE, RgbDirection.LEFT),
    MONOCHROMATIC_WAVE_RIGHT(-1, 8, true, RgbMotion.WAVE, RgbDirection.RIGHT),
    MONOCHROMATIC_WAVE_UP(-1, 8, true, RgbMotion.WAVE, RgbDirection.UP),
    MONOCHROMATIC_WAVE_DOWN(-1, 8, true, RgbMotion.WAVE, RgbDirection.DOWN),

    /** A checkerboard of three gray stops over the whole run, flipping as it scrolls. */
    MONOCHROMATIC_CHECKER(-1, 8, true, RgbMotion.CHECKER),
    /** A spinning wheel of gray, running black to white and back round the middle of the run. */
    MONOCHROMATIC_SPIN(-1, 8, true, RgbMotion.SPIN),
    /** Rings of gray expanding out of the middle of the run. */
    MONOCHROMATIC_RINGS(-1, 8, true, RgbMotion.RINGS),
    /** A bar of gray on both of a face's directions at once, reading as a cross over the run. */
    MONOCHROMATIC_CROSS(-1, 8, true, RgbMotion.CROSS),
    /** One bright band of gray sweeping left to right, running black to white and back. */
    MONOCHROMATIC_SCAN_HORIZONTAL(-1, 8, true, RgbMotion.SCAN_HORIZONTAL),
    /** One bright band of gray sweeping top to bottom. */
    MONOCHROMATIC_SCAN_VERTICAL(-1, 8, true, RgbMotion.SCAN_VERTICAL),
    /** Cells twinkling on their own clocks across the run, each holding its own shade of gray. */
    MONOCHROMATIC_SPARKLE(-1, 8, true, RgbMotion.SPARKLE),
    /** One shade for the whole block, breathing between black and white. */
    MONOCHROMATIC_PULSE(-1, 4, true, RgbMotion.PULSE);

    private static final RgbPattern[] VALUES = values();
    /** The chromatic dyes in the order a rainbow runs through them, from red round to pink. */
    private static final int[] DYE_RAINBOW = {RED.color, ORANGE.color, YELLOW.color, LIME.color, GREEN.color, CYAN.color, LIGHT_BLUE.color, BLUE.color, PURPLE.color, MAGENTA.color, PINK.color};
    /** The achromatic dyes, the whole range the monochromatic patterns sweep through. */
    private static final int[] DYE_MONOCHROME = {BLACK.color, GRAY.color, WHITE.color};
    /** A block state property holds sixteen values, so a longer id is split over two of them. */
    private static final int PHASE_SIZE = 16;

    /** Amount of patterns, must stay within what {@link #variant(int)} and {@link #phase(int)} can hold. */
    public static final int COUNT = VALUES.length;

    static {
        if (COUNT > PHASE_SIZE * PHASE_SIZE) {
            throw new IllegalStateException("A panel only has " + PHASE_SIZE * PHASE_SIZE + " patterns, " + COUNT + " are defined");
        }
        for (RgbPattern pattern : VALUES) {
            if (pattern.motion.isGradient() != (pattern.direction != null)) {
                throw new IllegalStateException(pattern + " has to carry a direction exactly when its motion " + pattern.motion + " is a gradient");
            }
        }
    }

    private final int color;
    private final int grid;
    private final boolean monochrome;
    private final RgbMotion motion;
    /** Set for the motions that are aimed along a direction, which are the ones that need one. */
    @Nullable
    private final RgbDirection direction;

    RgbPattern(int color, int grid) {
        this(color, grid, false, RgbMotion.NONE, null);
    }

    RgbPattern(DyeColor dye, int grid) {
        this(pack(dye), grid);
    }

    RgbPattern(int color, int grid, boolean monochrome, RgbMotion motion) {
        this(color, grid, monochrome, motion, null);
    }

    RgbPattern(int color, int grid, boolean monochrome, RgbMotion motion, @Nullable RgbDirection direction) {
        this.color = color;
        this.grid = grid;
        this.monochrome = monochrome;
        this.motion = motion;
        this.direction = direction;
    }

    public static RgbPattern byId(int id) {
        return VALUES[Math.floorMod(id, COUNT)];
    }

    /**
     * The translation key this pattern's name is read from.
     *
     * <p>Built from the constant's own name rather than stored beside it, because a name that has to be
     * written twice is a name that will be written twice wrongly. The constant is already the id, it is
     * never renamed in place, and lowercasing it is what makes the key match the one a translator sees in
     * {@code en_us.json}.
     *
     * <p>This is only the key, and what to do about a missing one is left to whoever asks: asking whether
     * a key exists means asking the language the client is in, which is not something common code can do.
     */
    public String translationKey() {
        return "pattern.rgbdeco." + this.name().toLowerCase(Locale.ROOT);
    }

    /**
     * The constant read out as words, which is what the screen showed before the names were translatable
     * and what an untranslated one falls back to.
     */
    public String spokenName() {
        StringBuilder titled = new StringBuilder();
        boolean start = true;
        for (String word : this.name().toLowerCase(Locale.ROOT).split("_")) {
            if (word.isEmpty()) {
                continue;
            }
            if (!start) {
                titled.append(' ');
            }
            titled.append(Character.toUpperCase(word.charAt(0))).append(word, 1, word.length());
            start = false;
        }
        return titled.toString();
    }

    public int nextId() {
        return Math.floorMod(ordinal() + 1, COUNT);
    }

    /** The low half of a pattern id, the part a single block state property can hold. */
    public static int variant(int id) {
        return Math.floorMod(id, PHASE_SIZE);
    }

    /** The high half of a pattern id, the part that pushes it past sixteen patterns. */
    public static int phase(int id) {
        return Math.floorMod(id, PHASE_SIZE * PHASE_SIZE) / PHASE_SIZE;
    }

    /** Puts the two halves back together, the inverse of {@link #variant(int)} and {@link #phase(int)}. */
    public static int id(int variant, int phase) {
        return phase * PHASE_SIZE + variant;
    }

    public boolean isAnimated() {
        return color < 0;
    }

    /** True for the patterns that sweep shades of gray instead of the dyes. */
    public boolean isMonochromatic() {
        return monochrome;
    }

    /**
     * How this pattern moves across a face. A rainbow pattern and its monochromatic twin share one
     * motion, which is what makes them step along together.
     */
    public RgbMotion motion() {
        return motion;
    }

    /**
     * True for the patterns that are aimed along a direction, which is what decides they are measured
     * along one axis of the run. It says nothing about how large a pattern is drawn: every animated
     * motion covers the whole run, so a wheel and a flow are the same size on a wall and only differ in
     * which way they are pointed.
     */
    public boolean isGradient() {
        return motion.isGradient();
    }

    /**
     * The direction this pattern runs in, or {@code null} for the patterns that do not run
     * anywhere. A gradient motion always carries one, which the class initializer checks.
     */
    @Nullable
    public RgbDirection direction() {
        return direction;
    }

    /**
     * The direction to measure a pattern along, falling back to left for the patterns that do not run
     * anywhere. Only a gradient motion reads this, and those always carry their own direction, so the
     * fallback never shows up.
     */
    public RgbDirection runAlong() {
        return direction == null ? RgbDirection.LEFT : direction;
    }

    /** Packed RGB of the solid colors, {@code -1} for the animated variants. */
    public int flatColor() {
        return color;
    }

    /**
     * How many quads a single face is cut into; the surface grid needs at least four.
     *
     * <p>This is the mesh a pattern is drawn on and is what decides how much of it a shape can hold.
     *
     * <p>It is squared, and every cell of it is a quad with four corners the pattern is sampled at, so
     * a grid of eight is sixty four quads and two hundred and fifty six vertices for a single face, and
     * a block is six faces of that. A solid color is drawn as one flat quad whatever it asks for here,
     * because it changes nowhere and the mesh would be geometry spent on a picture that never needed it.
     *
     * <p>A gradient is happy on four, because every quad of it is the same picture as the one next door.
     * A wheel of wedges or a set of rings crossing the spectrum are a different matter: their colors
     * change from one cell of the mesh to the next, so the fewer cells there are the more of each one
     * is blended away and the shape turns into a blur. Those ask for a finer mesh and pay for it in
     * the quads they are drawn with.
     */
    public int grid() {
        return grid;
    }

    /**
     * The color an animated pattern shows at {@code position} along its sweep, where {@code 0} is
     * where it starts and {@code 1} comes back round to it. The dyes the sweep runs through are
     * mixed into each other, so it never jumps from one color to the next.
     */
    public int animatedColor(double position, double brightness) {
        return sweep(monochrome ? DYE_MONOCHROME : DYE_RAINBOW, position, brightness);
    }

    /** Runs {@code position} around a closed loop of dyes, mixing each into the next one. */
    private static int sweep(int[] stops, double position, double brightness) {
        double along = (position % 1.0D + 1.0D) % 1.0D * stops.length;
        int index = (int) along;
        double blend = along - index;
        int from = stops[index];
        int to = stops[(index + 1) % stops.length];
        int r = mix(from >> 16 & 0xFF, to >> 16 & 0xFF, blend, brightness);
        int g = mix(from >> 8 & 0xFF, to >> 8 & 0xFF, blend, brightness);
        int b = mix(from & 0xFF, to & 0xFF, blend, brightness);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int mix(int from, int to, double blend, double brightness) {
        return Mth.clamp((int) ((from + (to - from) * blend) * brightness + 0.5D), 0, 255);
    }

    /** The color vanilla tints wool and friends with, as one packed RGB value. */
    private static int pack(DyeColor dye) {
        float[] channels = dye.getTextureDiffuseColors();
        int r = Mth.clamp((int) (channels[0] * 255.0F + 0.5F), 0, 255);
        int g = Mth.clamp((int) (channels[1] * 255.0F + 0.5F), 0, 255);
        int b = Mth.clamp((int) (channels[2] * 255.0F + 0.5F), 0, 255);
        return (r << 16) | (g << 8) | b;
    }
}
