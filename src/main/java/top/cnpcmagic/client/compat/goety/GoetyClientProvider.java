package top.cnpcmagic.client.compat.goety;

import com.Polarice3.Goety.api.magic.ISpell;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import top.cnpcmagic.client.provider.ClientMagicProvider;
import top.cnpcmagic.compat.goety.GoetyAdapter;

import java.util.List;

public class GoetyClientProvider implements ClientMagicProvider {
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
