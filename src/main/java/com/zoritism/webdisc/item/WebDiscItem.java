package com.zoritism.webdisc.item;

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

import java.util.List;
import java.util.function.Supplier;

public class WebDiscItem extends RecordItem {

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

        if (isRecorded(stack)) {
            return InteractionResultHolder.pass(stack);
        }

        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            ItemStack copy = stack.copy();
            try {
                NetworkHandler.CHANNEL.send(
                        PacketDistributor.PLAYER.with(() -> sp),
                        new OpenUrlMenuMessage(copy)
                );
            } catch (Throwable t) {
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