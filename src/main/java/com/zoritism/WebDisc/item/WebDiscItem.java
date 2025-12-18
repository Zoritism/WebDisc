package com.zoritism.webdisc.item;

import com.zoritism.webdisc.WebDiscMod;
import com.zoritism.webdisc.network.WebDiscNetwork;
import com.zoritism.webdisc.network.payload.OpenDiscUrlScreenMessage;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.RecordItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

public class WebDiscItem extends RecordItem {

    public WebDiscItem(Properties properties, int comparatorOutput, Supplier<SoundEvent> sound, int lengthSeconds) {
        super(comparatorOutput, sound, properties, lengthSeconds);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer sp) {
            WebDiscNetwork.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> sp),
                    new OpenDiscUrlScreenMessage(stack)
            );
        }
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        CompoundTag tag = stack.getTag();
        if (tag == null) tag = new CompoundTag();
        String url = tag.getString(WebDiscMod.URL_NBT);
        if (!url.isEmpty() && flag.isAdvanced()) {
            Component urlCmp = Component.literal(url).withStyle(ChatFormatting.BLUE);
            lines.add(Component.translatable("item.webdisc.web_disc.tooltip", urlCmp).withStyle(ChatFormatting.GRAY));
        }
        super.appendHoverText(stack, level, lines, flag);
    }

    @Override
    public int getLengthInTicks() {
        return 0;
    }
}
