package com.zoritism.webdisc;

import com.mojang.logging.LogUtils;
import com.zoritism.webdisc.config.WebDiscConfig;
import com.zoritism.webdisc.network.WebDiscNetwork;
import com.zoritism.webdisc.registry.WebDiscRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(WebDiscMod.MOD_ID)
public class WebDiscMod {

    public static final String MOD_ID = "webdisc";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final String URL_NBT = "webdisc:url";

    public WebDiscMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, WebDiscConfig.serverSpec);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () ->
                () -> ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, WebDiscConfig.clientSpec));

        modBus.register(WebDiscConfig.class);

        WebDiscRegistry.ITEMS.register(modBus);
        WebDiscRegistry.SOUND_EVENTS.register(modBus);

        modBus.addListener(this::onCommonSetup);
        modBus.addListener(this::addCreativeTabItems);
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(WebDiscNetwork::init);
    }

    private void addCreativeTabItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(WebDiscRegistry.CUSTOM_RECORD.get());
        }
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}
