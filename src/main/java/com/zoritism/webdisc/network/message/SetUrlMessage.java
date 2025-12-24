package com.zoritism.webdisc.network.message;

import com.mojang.logging.LogUtils;
import com.zoritism.webdisc.config.ModConfigHandler;
import com.zoritism.webdisc.item.WebDiscItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import org.slf4j.Logger;

import java.net.URL;
import java.util.List;
import java.util.function.Supplier;

public record SetUrlMessage(String url) {

    private static final Logger LOGGER = LogUtils.getLogger();

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
                LOGGER.info("[WebDisc] SetUrlMessage: invalid URL '{}': {}", u, e.toString());
                return;
            }

            if (u.length() >= 400) {
                LOGGER.info("[WebDisc] SetUrlMessage: URL too long ({} chars)", u.length());
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
                LOGGER.info("[WebDisc] SetUrlMessage: URL '{}' is not whitelisted", u);
                return;
            }

            CompoundTag tag = stack.getOrCreateTag();
            tag.putString(WebDiscItem.URL_NBT, u);

            // сбрасываем запись: финализация будет только после FinalizeRecordMessage
            tag.putBoolean("webdisc:finalized", false);
            tag.remove("webdisc:durationTicks");
            tag.remove("webdisc:bucketTicks");

            stack.setTag(tag);
        });
        c.setPacketHandled(true);
    }
}