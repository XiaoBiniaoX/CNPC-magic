package top.cnpcmagic.client;

import electroblob.wizardry.entity.living.ISpellCaster;
import electroblob.wizardry.registry.Spells;
import electroblob.wizardry.spell.Spell;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import noppes.npcs.entity.EntityNPCInterface;
import org.lwjgl.opengl.GL11;
import top.cnpcmagic.CNPCMagic;
import top.cnpcmagic.mixin.MixinRenderShield;

/** Native ward textures, animation and shield mesh attached to the actual NPC renderer. */
@Mod.EventBusSubscriber(modid = CNPCMagic.MOD_ID, value = Side.CLIENT)
public final class NativeShieldRendering {
    private static final ResourceLocation SHIELD = new ResourceLocation("ebwizardry", "textures/entity/shield.png");
    private static final ResourceLocation WARD = new ResourceLocation("ebwizardry", "textures/entity/shadow_ward.png");

    private NativeShieldRendering() { }

    @SubscribeEvent
    public static void render(RenderLivingEvent.Post<?> event) {
        if (!(event.getEntity() instanceof EntityNPCInterface)) return;
        EntityNPCInterface npc = (EntityNPCInterface) event.getEntity();
        Spell spell = ((ISpellCaster) npc).getContinuousSpell();
        if (spell != Spells.shield && spell != Spells.shadow_ward) return;
        boolean shield = spell == Spells.shield;
        float lightX = OpenGlHelper.lastBrightnessX;
        float lightY = OpenGlHelper.lastBrightnessY;
        // Use raw GL state with an attribute stack so other mods' renderer caches stay untouched.
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_LIGHTING_BIT | GL11.GL_CURRENT_BIT);
        GlStateManager.pushMatrix();
        try {
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glBlendFunc(shield ? GL11.GL_ONE : GL11.GL_SRC_ALPHA,
                shield ? GL11.GL_SRC_ALPHA : GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glShadeModel(GL11.GL_SMOOTH);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240, 240);
            GlStateManager.translate(event.getX(), event.getY() + npc.getEyeHeight() * 0.8, event.getZ());
            GlStateManager.rotate(-npc.rotationYawHead, 0, 1, 0);
            GlStateManager.rotate(npc.rotationPitch, 1, 0, 0);
            GlStateManager.translate(0, 0, 1);
            Minecraft.getMinecraft().renderEngine.bindTexture(shield ? SHIELD : WARD);
            Tessellator tessellator = Tessellator.getInstance();
            if (shield) {
                GL11.glMatrixMode(GL11.GL_TEXTURE);
                GL11.glPushMatrix();
                try {
                    GL11.glLoadIdentity();
                    GL11.glTranslatef(0.5f, 0.5f, 0);
                    float s = 1 - ((npc.ticksExisted + Minecraft.getMinecraft().getRenderPartialTicks()) % 5) / 5 * 0.52f;
                    GL11.glScalef(s * s, s * s, 1);
                    GL11.glTranslatef(-0.5f, -0.5f, 0);
                    GL11.glMatrixMode(GL11.GL_MODELVIEW);
                    MixinRenderShield.cnpcmagic$render(tessellator);
                } finally {
                    GL11.glMatrixMode(GL11.GL_TEXTURE);
                    GL11.glPopMatrix();
                    GL11.glMatrixMode(GL11.GL_MODELVIEW);
                }
            } else {
                GlStateManager.rotate((npc.ticksExisted + Minecraft.getMinecraft().getRenderPartialTicks()) * -2, 0, 0, 1);
                GlStateManager.scale(1.1, 1.1, 1.1);
                BufferBuilder buffer = tessellator.getBuffer();
                buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
                buffer.pos(-0.5, 0.5, -0.5).tex(0, 0).endVertex();
                buffer.pos(0.5, 0.5, -0.5).tex(1, 0).endVertex();
                buffer.pos(0.5, -0.5, -0.5).tex(1, 1).endVertex();
                buffer.pos(-0.5, -0.5, -0.5).tex(0, 1).endVertex();
                tessellator.draw();
            }
        } finally {
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lightX, lightY);
            GlStateManager.popMatrix();
            GL11.glPopAttrib();
        }
    }
}
