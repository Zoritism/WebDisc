package com.zoritism.webdisc.mixin;

import com.zoritism.webdisc.WebDiscMod;
import com.zoritism.webdisc.item.WebDiscItem;
import com.zoritism.webdisc.network.WebDiscNetwork;
import com.zoritism.webdisc.network.message.PlayWebDiscMessage;
import com.zoritism.webdisc.registry.WebDiscRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraftforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(JukeboxBlockEntity.class)
public abstract class MixinJukeboxBlockEntity {

    @Inject(method = "startPlaying()V", at = @At("HEAD"))
    private void webdisc$startPlaying(CallbackInfo ci) {
        JukeboxBlockEntity self = (JukeboxBlockEntity) (Object) this;
        Level level = self.getLevel();
        if (!(level instanceof ServerLevel server)) return;

        ItemStack stack = self.getFirstItem();
        if (!stack.is(WebDiscRegistry.CUSTOM_RECORD.get())) return;

        if (!WebDiscItem.isRecorded(stack)) {
            WebDiscMod.LOGGER.info("[WebDisc][Jukebox] startPlaying: custom record not recorded, skip");
            return;
        }

        CompoundTag tag = stack.getTag();
        if (tag == null) tag = new CompoundTag();
        String url = tag.getString(WebDiscMod.URL_NBT);
        if (url == null || url.isEmpty()) {
            WebDiscMod.LOGGER.info("[WebDisc][Jukebox] startPlaying: recorded disc has empty URL, skip");
            return;
        }

        WebDiscMod.LOGGER.info("[WebDisc][Jukebox] startPlaying at {} with url={}", self.getBlockPos(), url);

        server.players().forEach(p -> {
            WebDiscNetwork.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> (ServerPlayer) p),
                    new PlayWebDiscMessage(self.getBlockPos(), url));
        });
    }

    @Inject(method = "popOutRecord()V", at = @At("TAIL"))
    private void webdisc$popOutRecord(CallbackInfo ci) {
        JukeboxBlockEntity self = (JukeboxBlockEntity) (Object) this;
        Level level = self.getLevel();
        if (!(level instanceof ServerLevel server)) return;

        WebDiscMod.LOGGER.info("[WebDisc][Jukebox] popOutRecord at {}", self.getBlockPos());

        server.players().forEach(p -> {
            WebDiscNetwork.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> (ServerPlayer) p),
                    new PlayWebDiscMessage(self.getBlockPos(), ""));
        });
    }

    @Inject(method = "removeItem(II)Lnet/minecraft/world/item/ItemStack;", at = @At("TAIL"))
    private void webdisc$removeItem(int slot, int amount, CallbackInfoReturnable<ItemStack> cir) {
        JukeboxBlockEntity self = (JukeboxBlockEntity) (Object) this;
        Level level = self.getLevel();
        if (!(level instanceof ServerLevel server)) return;
        if (!self.getFirstItem().isEmpty()) return;

        WebDiscMod.LOGGER.info("[WebDisc][Jukebox] removeItem cleared jukebox at {}", self.getBlockPos());

        server.players().forEach(p -> {
            WebDiscNetwork.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> (ServerPlayer) p),
                    new PlayWebDiscMessage(self.getBlockPos(), ""));
        });
    }
}