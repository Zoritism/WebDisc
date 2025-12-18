package com.zoritism.webdisc.item;

import com.zoritism.webdisc.WebDiscMod;
import com.zoritism.webdisc.network.WebDiscNetwork;
import com.zoritism.webdisc.network.message.OpenUrlMenuMessage;
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

    // Старые имена оставляем для совместимости, но привязываем к новым ключам
    private static final String TAG_RECORDED = "webdisc:finalized";
    private static final String TAG_DURATION = "webdisc:durationTicks";

    public WebDiscItem(Properties properties, int comparatorOutput, Supplier<SoundEvent> sound, int lengthSeconds) {
        super(comparatorOutput, sound, properties, lengthSeconds);
    }

    public static boolean isRecorded(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        CompoundTag tag = stack.getTag();
        if (tag == null) return false;
        return tag.getBoolean(TAG_RECORDED);
    }

    public static int getDurationTicks(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        CompoundTag tag = stack.getTag();
        if (tag == null) return 0;
        return Math.max(0, tag.getInt(TAG_DURATION));
    }

    /**
     * Вспомогательный метод, если где-то в коде требуется пометить диск записанным вручную.
     * Использует те же поля, что и FinalizeRecordMessage.
     */
    public static void markRecorded(ItemStack stack, String url, int durationTicks) {
        if (stack == null || stack.isEmpty()) return;
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString(WebDiscMod.URL_NBT, url == null ? "" : url);
        tag.putBoolean(TAG_RECORDED, true);
        tag.putInt(TAG_DURATION, Math.max(0, durationTicks));
        stack.setTag(tag);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }

        // Если диск уже записан (finalized) — не открываем меню, просто возвращаем успех
        if (isRecorded(stack)) {
            return InteractionResultHolder.success(stack);
        }

        if (player instanceof ServerPlayer sp) {
            WebDiscNetwork.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> sp),
                    new OpenUrlMenuMessage(stack)
            );
        }
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        CompoundTag tag = stack.getTag();
        if (tag == null) tag = new CompoundTag();
        String url = tag.getString(WebDiscMod.URL_NBT);
        boolean recorded = tag.getBoolean(TAG_RECORDED);
        int duration = tag.getInt(TAG_DURATION);

        if (!url.isEmpty() && flag.isAdvanced()) {
            Component urlCmp = Component.literal(url).withStyle(ChatFormatting.BLUE);
            lines.add(Component.translatable("item.webdisc.web_disc.tooltip", urlCmp).withStyle(ChatFormatting.GRAY));
        }

        if (recorded) {
            double seconds = duration / 20.0;
            lines.add(Component.literal("Записано, длительность: " + String.format("%.1f", seconds) + " c")
                    .withStyle(ChatFormatting.GREEN));
        } else {
            lines.add(Component.literal("Пустой диск: ПКМ для записи URL").withStyle(ChatFormatting.YELLOW));
        }

        super.appendHoverText(stack, level, lines, flag);
    }

    @Override
    public int getLengthInTicks() {
        return 0;
    }
}