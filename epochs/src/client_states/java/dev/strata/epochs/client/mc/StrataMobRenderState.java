package dev.strata.epochs.client.mc;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** Was der Renderer pro Bild von einem Strata-Mob braucht: Aktion, Aktionszeit und Leuchtstärke. */
public class StrataMobRenderState extends LivingEntityRenderState {
    public String action;
    public float actionSeconds;
    public int stage;
    /** Leuchtstärke je Ebene (0..1). */
    public final float[] glow = new float[4];
}
