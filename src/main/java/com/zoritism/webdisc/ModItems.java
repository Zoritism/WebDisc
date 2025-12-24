package com.zoritism.webdisc;

import com.zoritism.webdisc.item.WebDiscItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, WebDiscMod.MODID);

    public static final RegistryObject<Item> WEBDISC =
            ITEMS.register("webdisc", () ->
                    new WebDiscItem(
                            new Item.Properties().stacksTo(1).rarity(Rarity.RARE),
                            0,
                            () -> com.zoritism.webdisc.WebDiscSounds.PLACEHOLDER_SOUND.get(),
                            12000
                    ));
}