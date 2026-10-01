package com.mystic.rgbdeco.energy;

import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.energy.EnergyStorage;

/**
 * A buffer of Forge energy that can be written into a block entity's tag and read back out of it.
 *
 * <p>{@link EnergyStorage} keeps its charge in a plain field with nothing written around it, so a
 * buffer that has to survive being saved to disk and put back is this: the charge is lifted into the
 * tag on the way out and dropped back into the buffer on the way in, and the capacity is left to the
 * block that owns it to state again so that a capacity raised by a config or by a future version does
 * not come back from disk as the old number.
 *
 * <p>Both buffers of the mod are receive only, so that nothing hands energy back out to a cable or a
 * hopper: what a controller or a panel has taken in is only ever spent on lighting itself, and a
 * neighbor that tries to pull from one of these blocks is told there is nothing to pull. Spending is
 * {@link #spend(int)} rather than the inherited {@code extractEnergy}, because a zero {@code maxExtract}
 * refuses extraction outright, which is the wrong answer for the block spending on itself.
 */
public class RgbEnergyStorage extends EnergyStorage {

    private static final String ENERGY = "energy";

    public RgbEnergyStorage(int capacity, int maxReceive) {
        super(capacity, maxReceive, 0, 0);
    }

    /**
     * The charge, as a plain int rather than the whole buffer.
     *
     * <p>Not the inherited {@code serializeNBT}, which is a tag of its own: a block entity saves into a
     * compound and reads back out of one, so the number is written straight into the buffer it owns.
     */
    public CompoundTag store() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(ENERGY, this.energy);
        return tag;
    }

    /**
     * Spends what the block is lighting itself with, and answers how much it actually got.
     *
     * <p>This exists because the buffer cannot extract, and {@code maxExtract} of zero is what stops a
     * cable pulling from it. That also stops the block spending its own charge through the inherited
     * {@code extractEnergy}, which refuses outright rather than giving a short answer: a controller
     * drawn to be costing a unit per panel per second has been handing out energy it never took, and a
     * panel drawn to drain itself has been sitting on a full buffer for ever.
     *
     * <p>The number this answers is not a promise, it is what there was to spend, which is what a
     * caller that pays out of one buffer to a hundred panels needs: the panels nearest the controller
     * are offered it first, and the ones past the end of the charge are offered nothing and hold
     * whatever they had.
     */
    public int spend(int amount) {
        int taken = Math.min(Math.max(amount, 0), this.energy);
        this.energy -= taken;
        return taken;
    }

    /**
     * Takes the charge back out of a tag, clamped to what this buffer can actually hold.
     *
     * <p>Clamped rather than trusted because the number comes off disk: a world written by a build
     * with a larger buffer, or hand edited, would otherwise put more charge in than the buffer can
     * give out, and every later read of the charge would ask for more than the block was ever given.
     */
    public void restore(CompoundTag tag) {
        this.energy = Math.max(0, Math.min(this.capacity, tag.getInt(ENERGY)));
    }
}