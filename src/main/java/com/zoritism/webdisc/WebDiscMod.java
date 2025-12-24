package com.zoritism.webdisc;

import com.zoritism.webdisc.client.WebDiscClient;
import com.zoritism.webdisc.network.NetworkHandler;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(WebDiscMod.MODID)
public final class WebDiscMod {

    public static final String MODID = "webdisc";

    public WebDiscMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        NetworkHandler.register();

        WebDiscSounds.SOUND_EVENTS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModTabs.TABS.register(modEventBus);

        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> WebDiscClient::init);
    }
}