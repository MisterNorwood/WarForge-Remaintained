package com.flansmod.warforge.common.network;

import com.flansmod.warforge.client.util.LayeredItemIconCache;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

@AllArgsConstructor
public class SiegeCampAttackInfoRender extends SiegeCampAttackInfo {
    public enum CenterMarkType {
        NONE,
        SIEGE_CAMP,
        PLAYER_FACE,
        CUSTOM_TEXTURE
    }

    @Getter
    @Setter
    @Nullable
    public ResourceLocation veinIcon = null;
    @Getter
    @Setter
    public CenterMarkType centerMarkType = CenterMarkType.NONE;
    @Getter
    @Setter
    @Nullable
    public ResourceLocation centerIcon = null;

    public SiegeCampAttackInfoRender(SiegeCampAttackInfo info) {
        super(info);
        retrieveVeinIcon();
    }

    public void retrieveVeinIcon() {
        if (mWarforgeVein == null) {
            return;
        }

        ItemStack stack = ItemStack.EMPTY;
        var compIt = mWarforgeVein.compIds.iterator();
        if (compIt.hasNext()) {
            stack = compIt.next().toStack();
        }

        if (!stack.isEmpty()) {
            veinIcon = LayeredItemIconCache.getIcon(stack);
        }
    }
}
