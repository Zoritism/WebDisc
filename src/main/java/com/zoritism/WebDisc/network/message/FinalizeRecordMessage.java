package com.zoritism.webdisc.network.message;

import com.zoritism.webdisc.WebDiscMod;
import com.zoritism.webdisc.registry.WebDiscRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C2S: клиент завершил запись диска и сообщает:
 * - URL
 * - длительность в тиках.
 *
 * Сервер:
 * - проверяет, что диск в руке, имеет тот же URL;
 * - проставляет webdisc:finalized=true и webdisc:durationTicks.
 */
public record FinalizeRecordMessage(String url, int lengthTicks) {

    public static void encode(FinalizeRecordMessage msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.url);
        buf.writeVarInt(msg.lengthTicks);
    }

    public static FinalizeRecordMessage decode(FriendlyByteBuf buf) {
        return new FinalizeRecordMessage(buf.readUtf(), buf.readVarInt());
    }

    public static void handle(FinalizeRecordMessage msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> {
            if (!c.getDirection().getReceptionSide().isServer()) return;
            Player p = c.getSender();
            if (p == null) return;

            ItemStack stack = p.getItemInHand(p.getUsedItemHand());
            if (!stack.is(WebDiscRegistry.CUSTOM_RECORD.get())) return;

            CompoundTag tag = stack.getOrCreateTag();
            String currentUrl = tag.getString(WebDiscMod.URL_NBT);
            if (currentUrl == null || currentUrl.isEmpty()) return;
            if (!currentUrl.equals(msg.url())) return;

            int len = Math.max(1, msg.lengthTicks());
            if (len > 20 * 3600) { // жёсткий лимит 1 час на всякий случай
                len = 20 * 3600;
            }

            tag.putBoolean("webdisc:finalized", true);
            tag.putInt("webdisc:durationTicks", len);
            stack.setTag(tag);
        });
        c.setPacketHandled(true);
    }
}