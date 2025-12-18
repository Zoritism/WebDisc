package com.zoritism.webdisc.mixin;

import com.zoritism.webdisc.client.audio.WebDiscAudioHelper;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

@Mixin(SoundBufferLibrary.class)
public abstract class MixinSoundBufferLibrary {

    @Inject(
            method = "getStream",
            at = @At("HEAD"),
            cancellable = true
    )
    private void webdisc$getStream(ResourceLocation rl, boolean loop, CallbackInfoReturnable<CompletableFuture<AudioStream>> cir) {
        CompletableFuture<AudioStream> future = WebDiscAudioHelper.getStream(rl, loop);
        if (future != null) {
            cir.setReturnValue(future);
            cir.cancel();
        }
    }
}