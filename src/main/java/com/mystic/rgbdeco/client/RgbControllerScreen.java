package com.mystic.rgbdeco.client;

import com.mystic.rgbdeco.block.RgbControllerBlock;
import com.mystic.rgbdeco.blockentity.RgbControllerBlockEntity;
import com.mystic.rgbdeco.client.render.PatternColor;
import com.mystic.rgbdeco.client.render.RgbRenderer;
import com.mystic.rgbdeco.network.RgbNetwork;
import com.mystic.rgbdeco.network.RefreshSystemPacket;
import com.mystic.rgbdeco.network.SelectPatternPacket;
import com.mystic.rgbdeco.pattern.RgbPattern;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;
import java.util.Locale;

/**
 * The screen of a controller: every pattern there is, as a swatch, and clicking one puts it on the
 * whole system. The server decides whether the click counts, so this only has to show what is there.
 *
 * <p>Each swatch is drawn by the same code that paints a panel, so a swatch is a picture of the
 * pattern rather than a name for it: a gradient, a checker, a set of rings and a twinkle all look
 * like what the panel will do.
 *
 * <p>Nothing is sent to the client to keep this up to date. The block entity of the controller already
 * carries how many panels and controllers its system has, and the pattern of any panel is a block
 * state, both of which the client is sent as they change, so the screen reads them off the level it
 * is already holding. The screen does ask the server to measure the system once as it opens, so what
 * it shows is the server's own answer rather than a copy that could be from before the player came
 * back to the controller. A click is never held back on what that copy says, because the server checks
 * all of it again anyway.
 *
 * <p>The screen closes itself, and never opens, unless one controller holding energy is the one in
 * charge of its system. A system with two controllers on it is locked: no click from here would reach a
 * panel, so the grid would be showing choices that do nothing, and the counts are on the block itself
 * for anyone who has to find the second controller. The line under the grid says how much energy the
 * controller has left, because a wall that has gone out is otherwise a wall with no explanation.
 */
@OnlyIn(Dist.CLIENT)
public class RgbControllerScreen extends Screen {
    /** Patterns per page, laid out as a grid this many squares wide and tall. */
    private static final int COLUMNS = 7;
    private static final int ROWS = 7;
    private static final int PER_PAGE = COLUMNS * ROWS;
    private static final int SWATCH = 16;
    /** How many samples a side the face of a swatch is cut into, which is what makes a pattern read. */
    private static final int SWATCH_CELLS = 4;
    /** Space between two swatches, which also reads as the border of one. */
    private static final int GAP = 1;
    private static final int CELL = SWATCH + GAP;
    private static final int PADDING = 8;
    /** Space above the grid for the title and the two lines of state. */
    private static final int HEADER = 46;
    /**
     * Space under the grid for the pager row and the energy bar.
     *
     * <p>The bar is what the extra row is for: a wall that has gone out looks the same from every angle
     * and a player standing at the controller has no other way of telling a wall that has run out of
     * energy from one that has been unhooked from its controller.
     */
    private static final int FOOTER = 32;
    /** How tall the energy bar is drawn, about one and a half text lines. */
    private static final int BAR_HEIGHT = 5;
    private static final int BAR_TRACK_COLOR = 0xFF262626;
    private static final int BAR_EMPTY_COLOR = 0xFFB4451F;
    private static final int BAR_FULL_COLOR = 0xFF4A9B4A;

    private static final int PANEL_COLOR = 0xE0101010;
    private static final int EDGE_COLOR = 0xFF3A3A3A;
    private static final int TEXT_COLOR = 0xFFE0E0E0;
    private static final int DIM_TEXT_COLOR = 0xFF909090;
    private static final int READY_COLOR = 0xFF6BE06B;
    private static final int CONFLICT_COLOR = 0xFFE06B6B;
    private static final int HOVER_COLOR = 0xFFFFFFFF;
    private static final int PICKED_COLOR = 0xFFFFD24A;

    private final BlockPos pos;
    private int page;
    private int panelX;
    private int panelY;
    private int gridX;
    private int gridY;
    /** Set once the size of the system has been asked for, so a resize does not ask again. */
    private boolean asked;

    public RgbControllerScreen(BlockPos pos) {
        super(RgbControllerBlock.title());
        this.pos = pos.immutable();
    }

    @Override
    protected void init() {
        int gridWidth = COLUMNS * CELL - GAP;
        int panelWidth = gridWidth + PADDING * 2;
        int panelHeight = HEADER + ROWS * CELL - GAP + FOOTER;
        this.panelX = (this.width - panelWidth) / 2;
        this.panelY = (this.height - panelHeight) / 2;
        this.gridX = this.panelX + PADDING;
        this.gridY = this.panelY + HEADER;
        // The counts come from the server's own measurement, and the client only holds a copy of that
        // number. Asking once as the screen opens is what makes the screen the authority on its own
        // size rather than a copy that could be from before a panel was broken off a wall somewhere
        // else on the system. It is asked for here, and not in the constructor, because that is where
        // the level first exists.
        if (!this.asked && this.minecraft != null && this.minecraft.getConnection() != null) {
            this.asked = true;
            RgbNetwork.CHANNEL.sendToServer(new RefreshSystemPacket(this.pos));
        }
    }

    @Override
    public void tick() {
        LocalPlayer player = this.minecraft == null ? null : this.minecraft.player;
        Level level = this.minecraft == null ? null : this.minecraft.level;
        if (player == null || level == null || !player.isAlive()) {
            this.onClose();
            return;
        }
        BlockState state = level.getBlockState(this.pos);
        RgbControllerBlockEntity controller = controller();
        // The screen is only meant to be up while one controller holding energy is the one allowed to
        // drive the system. It never opens otherwise: a controller with nothing in it has nothing to
        // show, and a system with two controllers on it is locked, so there is no pattern a click from
        // here could put anywhere and the whole grid would be a lie. A conflict arriving while the
        // screen is up closes it for the same reason, and so does the last of the energy going.
        if (!(state.getBlock() instanceof RgbControllerBlock) || controller == null || !controller.powered()
                || controller.conflicted()) {
            this.onClose();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // The base screen draws the wash behind the world, which has to go down before the panel.
        super.render(graphics, mouseX, mouseY, partialTick);

        int gridWidth = COLUMNS * CELL - GAP;
        int gridHeight = ROWS * CELL - GAP;
        graphics.fill(this.panelX, this.panelY, this.panelX + gridWidth + PADDING * 2, this.panelY + HEADER + gridHeight + FOOTER, PANEL_COLOR);
        graphics.renderOutline(this.panelX, this.panelY, gridWidth + PADDING * 2, HEADER + gridHeight + FOOTER, EDGE_COLOR);

        graphics.drawCenteredString(this.font, this.title, this.panelX + (gridWidth + PADDING * 2) / 2, this.panelY + 7, TEXT_COLOR);

        RgbControllerBlockEntity controller = controller();
        int current = this.minecraft == null || this.minecraft.level == null
                ? 0
                : RgbControllerBlock.referencePattern(this.minecraft.level, this.pos);

        if (controller == null) {
            graphics.drawCenteredString(this.font, Component.translatable("gui.rgbdeco.status.gone"), this.width / 2, this.panelY + 21, CONFLICT_COLOR);
        } else if (!controller.powered()) {
            // Said before the counts, because a controller with nothing in it is not running its system
            // whatever size that system is, and the number of panels would only send a player looking
            // for a wiring problem that is not there.
            graphics.drawCenteredString(this.font, Component.translatable("gui.rgbdeco.status.no_power"), this.width / 2, this.panelY + 21, DIM_TEXT_COLOR);
        } else if (controller.panels() <= 0) {
            graphics.drawCenteredString(this.font, Component.translatable("gui.rgbdeco.status.no_panels"), this.width / 2, this.panelY + 21, DIM_TEXT_COLOR);
        } else if (controller.conflicted()) {
            // Both numbers, not only the controllers. A player who has wired a second controller into
            // the panels has come here to find out how many panels it is fighting over, and that is
            // the number the screen would otherwise have dropped exactly when it mattered.
            graphics.drawCenteredString(this.font, Component.translatable("gui.rgbdeco.status.conflict", controller.panels(), controller.controllers()), this.width / 2, this.panelY + 21, CONFLICT_COLOR);
        } else {
            graphics.drawCenteredString(this.font, Component.translatable("gui.rgbdeco.status.ready", controller.panels()), this.width / 2, this.panelY + 21, READY_COLOR);
        }
        graphics.drawCenteredString(this.font, Component.translatable("gui.rgbdeco.current", name(RgbPattern.byId(current))), this.width / 2, this.panelY + 33, DIM_TEXT_COLOR);

        this.energyBar(graphics, controller);

        int hovered = -1;
        for (int i = 0; i < PER_PAGE; i++) {
            int id = this.page * PER_PAGE + i;
            if (id >= RgbPattern.COUNT) {
                break;
            }
            int x = this.gridX + i % COLUMNS * CELL;
            int y = this.gridY + i / COLUMNS * CELL;
            this.swatch(graphics, RgbPattern.byId(id), x, y);
            if (id == current) {
                graphics.renderOutline(x - 1, y - 1, SWATCH + 2, SWATCH + 2, PICKED_COLOR);
            }
            if (mouseX >= x && mouseX < x + SWATCH && mouseY >= y && mouseY < y + SWATCH) {
                hovered = id;
                graphics.renderOutline(x - 1, y - 1, SWATCH + 2, SWATCH + 2, HOVER_COLOR);
            }
        }

        this.pager(graphics, mouseX, mouseY);

        if (hovered >= 0) {
            graphics.renderTooltip(this.font, name(RgbPattern.byId(hovered)), mouseX, mouseY);
        }
    }

/**
     * Draws how much energy the controller is holding, as a bar and a number.
     *
     * <p>The number comes off the block entity, which is a copy the server pushed, so it is a tick or
     * two behind the real charge and is only ever read by a player. Nothing is decided from it here:
     * whether a click counts and whether the screen stays up are both asked of the server again, and the
     * server has the real number.
     *
     * <p>The bar is drawn against the controller's own capacity rather than against a round number,
     * because a controller that is nearly full and a controller that is nearly empty are the two states
     * a player actually has to tell apart, and both read at a glance against the same end of the bar.
     */
    private void energyBar(GuiGraphics graphics, @Nullable RgbControllerBlockEntity controller) {
        int width = COLUMNS * CELL - GAP;
        int x = this.panelX + PADDING;
        int y = this.gridY + ROWS * CELL - GAP + 15;
        graphics.fill(x, y, x + width, y + BAR_HEIGHT, BAR_TRACK_COLOR);
        if (controller == null) {
            return;
        }
        int capacity = Math.max(1, controller.capacity());
        int filled = Math.round((float) controller.energy() / capacity * width);
        if (filled > 0) {
            // Red while low rather than at empty: the wall this is drawn over is already flickering by
            // the time the bar is a quarter down, and a bar that only turns red once there is nothing
            // left is a bar that is never useful.
            graphics.fill(x, y, x + filled, y + BAR_HEIGHT,
                    filled * 4 < width ? BAR_EMPTY_COLOR : BAR_FULL_COLOR);
        }
        graphics.drawCenteredString(this.font, Component.translatable("gui.rgbdeco.energy",
                format(controller.energy()), format(capacity)),
                this.panelX + width / 2 + PADDING, y + BAR_HEIGHT + 3, DIM_TEXT_COLOR);
    }

    /**
     * Draws one pattern the way a panel shows it: the item's own texture, the very one the block is
     * drawn with, with a face of the pattern itself laid into the flat middle of it and colored by the
     * very code the world renderer runs. Sampling that code is what keeps a swatch honest, a flat
     * block of a color no panel ever paints would not tell a rainbow from a checker.
     */
    private void swatch(GuiGraphics graphics, RgbPattern pattern, int x, int y) {
        // The frame is the item's own texture rather than a set of hard coded rings, so a swatch is
        // framed exactly as the block will be and cannot drift away from the texture.
        graphics.blit(RgbRenderer.PANEL_TEXTURE, x, y, 0, 0, SWATCH, SWATCH);

        // The face inside the frame, sampled on a small grid: a gradient, a checker or a ring still
        // has to read as itself across sixteen pixels.
        int texel = Math.max(1, SWATCH / 16);
        int from = texel * 2;
        int cell = Math.max(1, (SWATCH - from * 2) / SWATCH_CELLS);
        float time = Util.getMillis() * 0.001F;
        for (int j = 0; j < SWATCH_CELLS; j++) {
            for (int i = 0; i < SWATCH_CELLS; i++) {
                int cx = x + from + i * cell;
                int cy = y + from + j * cell;
                graphics.fill(cx, cy, cx + cell, cy + cell, PatternColor.at(pattern, Direction.SOUTH,
                        (from + i * cell + cell * 0.5D) / SWATCH,
                        (from + j * cell + cell * 0.5D) / SWATCH,
                        null, time, null));
            }
        }
    }

    /** The row under the grid that walks between the pages of patterns. */
    private void pager(GuiGraphics graphics, int mouseX, int mouseY) {
        int pages = pages();
        int rowY = this.gridY + ROWS * CELL - GAP + 4;
        int left = this.gridX;
        int right = this.gridX + COLUMNS * CELL - GAP;
        if (pages > 1) {
            this.arrow(graphics, Component.translatable("gui.rgbdeco.previous_page"), left, rowY, this.page > 0,
                    mouseX, mouseY);
            this.arrow(graphics, Component.translatable("gui.rgbdeco.next_page"), right - 7, rowY, this.page < pages - 1,
                    mouseX, mouseY);
        }
        graphics.drawCenteredString(this.font, Component.translatable("gui.rgbdeco.page", this.page + 1, pages), (left + right) / 2, rowY, DIM_TEXT_COLOR);
    }

    private void arrow(GuiGraphics graphics, Component glyph, int x, int y, boolean usable, int mouseX, int mouseY) {
        int color = usable ? (mouseX >= x && mouseX < x + 8 && mouseY >= y && mouseY < y + 8 ? HOVER_COLOR : TEXT_COLOR) : DIM_TEXT_COLOR;
        graphics.drawString(this.font, glyph, x, y, color, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        int rowY = this.gridY + ROWS * CELL - GAP + 4;
        if (mouseY >= rowY && mouseY < rowY + 8) {
            int pages = pages();
            if (pages > 1 && mouseX >= this.gridX && mouseX < this.gridX + 8 && this.page > 0) {
                this.page--;
                return true;
            }
            if (pages > 1 && mouseX >= this.gridX + COLUMNS * CELL - GAP - 8 && mouseX < this.gridX + COLUMNS * CELL - GAP
                    && this.page < pages - 1) {
                this.page++;
                return true;
            }
        }

        for (int i = 0; i < PER_PAGE; i++) {
            int id = this.page * PER_PAGE + i;
            if (id >= RgbPattern.COUNT) {
                break;
            }
            int x = this.gridX + i % COLUMNS * CELL;
            int y = this.gridY + i / COLUMNS * CELL;
            if (mouseX >= x && mouseX < x + SWATCH && mouseY >= y && mouseY < y + SWATCH) {
                // The click goes to the server whenever there is a controller here at all.
                // RgbControllerBlock.applyPattern checks the energy, the block and whether this
                // controller still owns the system alone before it writes anything, so deciding here
                // adds no safety. It only loses clicks: this screen reads a copy of the block entity
                // that can be a moment behind the server's, and a moment behind is exactly when a
                // player is most likely to be trying to change something.
                if (controller() != null) {
                    RgbNetwork.CHANNEL.sendToServer(new SelectPatternPacket(this.pos, id));
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        // The panels the screen is driving keep moving while it is up, which is the point of looking
        // at them while choosing the next one.
        return false;
    }

    @Nullable
    private RgbControllerBlockEntity controller() {
        if (this.minecraft == null || this.minecraft.level == null) {
            return null;
        }
        return this.minecraft.level.getBlockEntity(this.pos) instanceof RgbControllerBlockEntity controller ? controller : null;
    }

private int pages() {
        return Math.max(1, (RgbPattern.COUNT + PER_PAGE - 1) / PER_PAGE);
    }

    /**
     * How many digits a charge is written to before it steps up to a bigger unit.
     *
     * <p>Three is what a Forge energy amount is normally read at, and it is the point at which a number
     * stops being countable at a glance: 40000 is a number a player can hold in their head and "40000" is
     * six glyphs wide on a bar this size.
     */
    private static final int SIGNIFICANT_DIGITS = 3;

    /** The units a charge is counted in, each one a thousand of the one before it. */
    private static final String[] UNITS = {"FE", "kFE", "MFE", "GFE", "TFE", "PFE"};

    /**
     * A charge written the way an energy amount is normally read: the largest unit that leaves a whole
     * number, at up to {@link #SIGNIFICANT_DIGITS} digits.
     *
     * <p>Forge has no formatter of its own on this version, which is what this is standing in for.
     *
     * <p>The unit is part of the number rather than a separate translatable key, so that a language which
     * writes a number and its unit differently cannot put the two in the wrong order: "40 kFE" is read the
     * same way whatever the words are. Both halves are still the same in every language, which is why this
     * is not translated.
     */
    private static String format(int amount) {
        if (amount < 1000) {
            return amount + " " + UNITS[0];
        }
        double scaled = amount;
        int unit = 0;
        while (scaled >= 1000 && unit < UNITS.length - 1) {
            scaled /= 1000;
            unit++;
        }
        // Rounded before the decimal is decided rather than after, because a charge of 999999 is a whole
        // thousand kFE and has to be written as 1 MFE: rounding to "1000 kFE" puts four digits on the end
        // of a bar and is the one number that steps up past its own unit.
        long rounded = Math.round(scaled);
        if (rounded >= 1000 && unit < UNITS.length - 1) {
            scaled /= 1000;
            unit++;
        } else if (rounded >= 100 || scaled == Math.floor(scaled)) {
            scaled = rounded;
        }
        // One decimal only while it carries a digit the integer part does not, so a charge reads
        // "39.9 kFE" a moment before it reads "40 kFE" rather than passing through "40.0".
        String number = scaled == Math.floor(scaled)
                ? String.valueOf((long) scaled)
                : String.format(Locale.ROOT, "%.1f", scaled);
        return number + " " + UNITS[unit];
    }

/**
     * A pattern's name, read through the language the game is in.
     *
     * <p>Asked here rather than from {@link RgbPattern} because asking whether a key has been translated
     * means asking the client's language, which common code cannot do: the enum is loaded on a dedicated
     * server too, where there is no language to ask. A key that has not been translated falls back to the
     * English name rather than showing itself, because a translator part way through a new language leaves
     * gaps and "pattern.rgbdeco.rainbow_spin" on a tooltip is worse than a name nobody has translated yet.
     */
    private static Component name(RgbPattern pattern) {
        String key = pattern.translationKey();
        return I18n.exists(key) ? Component.translatable(key) : Component.literal(pattern.spokenName());
    }
}
