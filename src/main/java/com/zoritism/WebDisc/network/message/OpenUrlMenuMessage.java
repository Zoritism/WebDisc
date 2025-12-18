package com.zoritism.webdisc.network.message;

import com.zoritism.webdisc.WebDiscMod;
import com.zoritism.webdisc.client.DiscUrlScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record OpenUrlMenuMessage(ItemStack disc) {

    public static void encode(OpenUrlMenuMessage msg, FriendlyByteBuf buf) {
        buf.writeItem(msg.disc);
    }

    public static OpenUrlMenuMessage decode(FriendlyByteBuf buf) {
        return new OpenUrlMenuMessage(buf.readItem());
    }

    public static void handle(OpenUrlMenuMessage msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> {
            if (!c.getDirection().getReceptionSide().isClient()) {
                return;
            }
            CompoundTag tag = msg.disc.getTag();
            if (tag == null) {
                tag = new CompoundTag();
            }
            String url = tag.getString(WebDiscMod.URL_NBT);
            DiscUrlScreen.open(url);
        });
        c.setPacketHandled(true);
    }
}