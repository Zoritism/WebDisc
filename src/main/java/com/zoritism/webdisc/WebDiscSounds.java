package com.zoritism.webdisc;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Звуки подсистемы WebDisc, зарегистрированные под модом webdisc.
 */
public final class WebDiscSounds {

    private WebDiscSounds() {}

    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, WebDiscMod.MODID);

    public static final RegistryObject<SoundEvent> PLACEHOLDER_SOUND =
            SOUND_EVENTS.register("webdisc_placeholder",
                    () -> SoundEvent.createVariableRangeEvent(
                            new ResourceLocation(WebDiscMod.MODID, "webdisc_placeholder")
                    ));
}