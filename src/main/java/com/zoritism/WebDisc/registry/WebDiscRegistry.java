package com.zoritism.webdisc.registry;

import com.zoritism.webdisc.WebDiscMod;
import com.zoritism.webdisc.item.WebDiscItem;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class WebDiscRegistry {

    private WebDiscRegistry() {}

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, WebDiscMod.MOD_ID);

    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, WebDiscMod.MOD_ID);

    public static final RegistryObject<SoundEvent> PLACEHOLDER_SOUND =
            SOUND_EVENTS.register("placeholder_sound",
                    () -> SoundEvent.createVariableRangeEvent(WebDiscMod.id("placeholder_sound")));

    public static final RegistryObject<WebDiscItem> CUSTOM_RECORD =
            ITEMS.register("web_disc", () ->
                    new WebDiscItem(
                            new Item.Properties().stacksTo(1).rarity(Rarity.RARE),
                            0,
                            PLACEHOLDER_SOUND,
                            1
                    )
            );
}