package com.zoritism.webdisc.client;

import com.zoritism.webdisc.ModItems;
import com.zoritism.webdisc.item.WebDiscItem;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

public final class WebDiscClient {

    private WebDiscClient() {}

    public static void init() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(WebDiscClient::clientSetup);
    }

    private static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            try {
                com.zoritism.webdisc.client.audio.WebDiscAudioHelper.cleanupCacheOnClientInit();
            } catch (Throwable ignored) {}
        });

        event.enqueueWork(() -> ItemProperties.register(
                ModItems.WEBDISC.get(),
                new ResourceLocation("webdisc", "finalized"),
                (stack, level, entity, seed) -> (stack.getItem() instanceof WebDiscItem && WebDiscItem.isRecorded(stack)) ? 1.0F : 0.0F
        ));
    }
}