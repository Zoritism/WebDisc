package com.zoritism.webdisc.item;

import com.mojang.logging.LogUtils;
import com.zoritism.webdisc.network.NetworkHandler;
import com.zoritism.webdisc.network.message.OpenUrlMenuMessage;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.RecordItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.List;
import java.util.function.Supplier;

/**
 * Записываемый “веб-диск”.
 * URL хранится в NBT по ключу URL_NBT, длительность:
 * - webdisc:durationTicks — "сырая" (измеренная) длительность;
 * - webdisc:bucketTicks   — квантованная по бакетам (для проигрывания).
 *
 * finalized=true:
 * - разрешает проигрывание;
 * - запрещает открывать GUI на ПКМ.
 */
public class WebDiscItem extends RecordItem {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final String URL_NBT = "webdisc:url";
    private static final String TAG_RECORDED = "webdisc:finalized";
    private static final String TAG_DURATION = "webdisc:durationTicks";
    private static final String TAG_BUCKET_DURATION = "webdisc:bucketTicks";

    public WebDiscItem(Properties properties, int comparatorOutput, Supplier<SoundEvent> sound, int lengthSeconds) {
        super(comparatorOutput, sound, properties, lengthSeconds);
    }

    public static boolean isRecorded(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(TAG_RECORDED);
    }

    public static int getDurationTicks(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        CompoundTag tag = stack.getTag();
        if (tag == null) return 0;
        int len = tag.getInt(TAG_DURATION);
        return Math.max(len, 0);
    }

    /**
     * Квантованная длительность (бакеты по 5 секунд).
     * Если bucketTicks ещё нет (старые диски) — используем raw durationTicks.
     */
    public static int getBucketTicks(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        CompoundTag tag = stack.getTag();
        if (tag == null) return 0;
        int bucket = tag.getInt(TAG_BUCKET_DURATION);
        if (bucket > 0) return bucket;
        int raw = tag.getInt(TAG_DURATION);
        return Math.max(raw, 0);
    }

    public static String getUrl(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        CompoundTag tag = stack.getTag();
        if (tag == null) return "";
        return tag.getString(URL_NBT);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // Если диск уже записан — ведём себя как обычный рекорд (GUI не открываем)
        if (isRecorded(stack)) {
            return InteractionResultHolder.pass(stack);
        }

        // Не записан: открываем GUI на сервере
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            ItemStack copy = stack.copy();
            try {
                NetworkHandler.CHANNEL.send(
                        PacketDistributor.PLAYER.with(() -> sp),
                        new OpenUrlMenuMessage(copy)
                );
            } catch (Throwable t) {
                LOGGER.info("[WebDisc] use(): failed to send OpenUrlMenuMessage: {}", t.toString());
            }
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, level, entity, slot, selected);
    }

    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        return super.onEntityItemUpdate(stack, entity);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        CompoundTag tag = stack.getTag();
        if (tag == null) tag = new CompoundTag();

        String url = tag.getString(URL_NBT);
        if (!url.isEmpty() && flag.isAdvanced()) {
            Component urlCmp = Component.literal(url).withStyle(ChatFormatting.BLUE);
            lines.add(
                    Component.translatable("item.webdisc.webdisc.tooltip", urlCmp)
                            .withStyle(ChatFormatting.GRAY)
            );
        }

        if (tag.getBoolean(TAG_RECORDED)) {
            int len = tag.getInt(TAG_DURATION);
            int seconds = Math.max(0, len / 20);
            lines.add(
                    Component.translatable("item.webdisc.webdisc.recorded", seconds)
                            .withStyle(ChatFormatting.GREEN)
            );
        }

        super.appendHoverText(stack, level, lines, flag);
    }
}