package top.cnpcmagic.client.compat.goety;

import com.Polarice3.Goety.api.magic.ISpell;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import top.cnpcmagic.client.provider.ClientMagicProvider;
import top.cnpcmagic.compat.goety.GoetyAdapter;

import java.util.List;

public class GoetyClientProvider implements ClientMagicProvider {
    private static final net.minecraft.client.renderer.RenderType PRISMA_BEAM = net.minecraft.client.renderer.RenderType.entityCutoutNoCull(
        ResourceLocation.withDefaultNamespace("textures/entity/guardian_beam.png"));

    /** Goety's PrismaBeamRenderer accepts Player only; retain its beam geometry/material for real NPCs. */
    public static void renderNpc(net.neoforged.neoforge.client.event.RenderLivingEvent.Post<?, ?> event) {
        if (!(event.getEntity() instanceof noppes.npcs.entity.EntityNPCInterface npc)) return;
        int waterTime = top.cnpcmagic.client.NpcAnimations.castingTime(npc, "goety:water_jet_focus");
        if (waterTime >= com.Polarice3.Goety.config.SpellConfig.WaterJetChargeUp.get()) {
            float partial = event.getPartialTick();
            var target = com.Polarice3.Goety.utils.MiscCapHelper.getClientTarget(npc);
            var end = target == null ? npc.pick(8, partial, false).getLocation()
                : target.getPosition(partial).add(0, target.getBbHeight() * 0.5, 0);
            var delta = end.subtract(npc.getPosition(partial).add(0, npc.getEyeHeight(), 0));
            var direction = delta.normalize();
            var pose = event.getPoseStack();
            pose.pushPose();
            pose.translate(0, npc.getEyeHeight(), 0);
            pose.mulPose(com.mojang.math.Axis.YP.rotation((float)(Math.PI / 2 - Math.atan2(direction.z, direction.x))));
            pose.mulPose(com.mojang.math.Axis.XP.rotation((float)Math.acos(direction.y)));
            float age = -(npc.tickCount + partial) * 0.4f;
            var buffers = event.getMultiBufferSource();
            top.cnpcmagic.mixin.MixinGoetyWaterJetRenderer.cnpcmagic$renderLayer(buffers.getBuffer(
                com.Polarice3.Goety.client.render.ModRenderType.magicBeam(ResourceLocation.parse("goety:textures/entity/water_jet/jet_inner.png"))),
                pose, 0.04f, (float)delta.length(), 16, age);
            top.cnpcmagic.mixin.MixinGoetyWaterJetRenderer.cnpcmagic$renderLayer(buffers.getBuffer(
                com.Polarice3.Goety.client.render.ModRenderType.magicBeam(ResourceLocation.parse("goety:textures/entity/water_jet/jet_outer.png"))),
                pose, 0.05f, (float)delta.length(), 128, age * 0.2f);
            top.cnpcmagic.client.gui.EditorClientVerification.goetyRendered(npc, "water_jet");
            pose.popPose();
            return;
        }
        int time = top.cnpcmagic.client.NpcAnimations.castingTime(npc, "goety:prisma_beam_focus");
        if (time < 0 || !(com.Polarice3.Goety.utils.MiscCapHelper.getClientTarget(npc) instanceof LivingEntity target)) return;
        float partial = event.getPartialTick();
        var delta = target.getPosition(partial).add(0, target.getBbHeight() * 0.5, 0)
            .subtract(npc.getPosition(partial).add(0, npc.getEyeHeight(), 0));
        float length = (float)delta.length() + 1;
        var direction = delta.normalize();
        var pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(0, npc.getEyeHeight(), 0);
        pose.mulPose(com.mojang.math.Axis.YP.rotation((float)(Math.PI / 2 - Math.atan2(direction.z, direction.x))));
        pose.mulPose(com.mojang.math.Axis.XP.rotation((float)Math.acos(direction.y)));
        float age = time + partial;
        float scale = com.Polarice3.Goety.client.render.PrismaBeamRenderer.getAttackAnimationScale(time, partial);
        float intensity = scale * scale;
        int red = 64 + (int)(intensity * 191), green = 32 + (int)(intensity * 191), blue = 128 - (int)(intensity * 64);
        float v0 = -1 + age * 0.5f % 1, v1 = length * 2.5f + v0;
        var vertices = event.getMultiBufferSource().getBuffer(PRISMA_BEAM);
        for (int plane = 0; plane < 2; plane++) {
            double angle = age * -0.075 + plane * Math.PI / 2;
            float x = (float)Math.cos(angle) * 0.2f, z = (float)Math.sin(angle) * 0.2f;
            beamVertex(vertices, pose.last(), -x, length, -z, red, green, blue, 0.4999f, v1);
            beamVertex(vertices, pose.last(), -x, 0, -z, red, green, blue, 0.4999f, v0);
            beamVertex(vertices, pose.last(), x, 0, z, red, green, blue, 0, v0);
            beamVertex(vertices, pose.last(), x, length, z, red, green, blue, 0, v1);
        }
        float capV = npc.tickCount % 2 == 0 ? 0.5f : 0;
        for (int corner = 0; corner < 4; corner++) {
            double angle = age * -0.075 + Math.PI * (0.75 - corner * 0.5);
            beamVertex(vertices, pose.last(), (float)Math.cos(angle) * 0.282f, length, (float)Math.sin(angle) * 0.282f,
                red, green, blue, corner == 0 || corner == 3 ? 0.5f : 1, capV + (corner < 2 ? 0.5f : 0));
        }
        top.cnpcmagic.client.gui.EditorClientVerification.goetyRendered(npc, "prisma");
        pose.popPose();
    }

    private static void beamVertex(com.mojang.blaze3d.vertex.VertexConsumer vertices, com.mojang.blaze3d.vertex.PoseStack.Pose pose,
                                   float x, float y, float z, int red, int green, int blue, float u, float v) {
        vertices.addVertex(pose, x, y, z).setColor(red, green, blue, 255).setUv(u, v)
            .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(pose, 0, 1, 0);
    }

    @Override
    public String getId() {
        return "goety";
    }

    @Override
    public List<String> getSpellIds() {
        return GoetyAdapter.getSpellIds();
    }

    @Override
    public String getDisplayName(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        return location == null ? id : BuiltInRegistries.ITEM.get(location).getDescription().getString();
    }

    @Override
    public int getMaxLevel(String id) {
        return GoetyAdapter.getMaxLevel(id);
    }

    @Override
    public List<Component> describe(String id, int level, LivingEntity caster) {
        ISpell spell = GoetyAdapter.getSpell(id);
        if (spell == null) return List.of();
        return List.of(Component.translatable("cnpcmagic.editor.propertyTimes", spell.defaultCastDuration() / 20.0, spell.defaultSpellCooldown() / 20.0),
            Component.translatable("cnpcmagic.editor.propertySouls", spell.defaultSoulCost()),
            Component.translatable("cnpcmagic.editor.propertyPotency", level - 1));
    }

    @SuppressWarnings("unchecked") // Renderer supplies this entity's model; wildcard erases its entity type.
    public static boolean applyPose(String id, ItemStack staff, LivingEntity entity, HumanoidModel<?> model) {
        ISpell spell = GoetyAdapter.getSpell(id);
        if (spell == null) {
            return false;
        }
        HumanoidModel.ArmPose pose = spell.getPose(entity, staff, spell.defaultStats());
        pose.applyTransform((HumanoidModel<LivingEntity>) model, entity, entity.getMainArm());
        return true;
    }
}
