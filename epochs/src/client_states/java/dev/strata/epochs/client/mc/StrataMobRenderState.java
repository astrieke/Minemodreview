package dev.strata.epochs.client.mc;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** Was der Renderer pro Bild von einem Strata-Mob braucht: Aktion, Aktionszeit und Leuchtstärke. */
public class StrataMobRenderState extends LivingEntityRenderState {
    public String action;
    public float actionSeconds;
    public float glow;
}
