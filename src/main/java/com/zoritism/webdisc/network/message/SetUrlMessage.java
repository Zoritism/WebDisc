package com.zoritism.webdisc.network.message;

import com.zoritism.webdisc.config.ModConfigHandler;
import com.zoritism.webdisc.item.WebDiscItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.net.URL;
import java.util.List;
import java.util.function.Supplier;

public record SetUrlMessage(String url) {

    public static void encode(SetUrlMessage msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.url);
    }

    public static SetUrlMessage decode(FriendlyByteBuf buf) {
        return new SetUrlMessage(buf.readUtf());
    }

    public static void handle(SetUrlMessage msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> {
            if (!c.getDirection().getReceptionSide().isServer()) {
                return;
            }
            Player p = c.getSender();
            if (p == null) return;

            ItemStack stack = p.getItemInHand(p.getUsedItemHand());
            if (!(stack.getItem() instanceof WebDiscItem)) return;

            String u = msg.url();
            if (u == null) u = "";

            try {
                new URL(u).toURI();
            } catch (Exception e) {
                return;
            }

            if (u.length() >= 400) {
                return;
            }

            List<? extends String> prefixes = ModConfigHandler.COMMON.webdiscWhitelistedUrls.get();
            boolean allowed = false;
            if (prefixes != null) {
                for (String prefix : prefixes) {
                    if (prefix != null && !prefix.isEmpty() && u.startsWith(prefix)) {
                        allowed = true;
                        break;
                    }
                }
            }

            if (!allowed) {
                return;
            }

            CompoundTag tag = stack.getOrCreateTag();
            tag.putString(WebDiscItem.URL_NBT, u);

            tag.putBoolean("webdisc:finalized", false);
            tag.remove("webdisc:durationTicks");
            tag.remove("webdisc:bucketTicks");

            stack.setTag(tag);
        });
        c.setPacketHandled(true);
    }
}