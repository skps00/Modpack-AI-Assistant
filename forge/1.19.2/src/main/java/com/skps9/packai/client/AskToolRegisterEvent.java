package com.skps9.packai.client;

import com.skps9.packai.api.AskToolRegistration;

import net.minecraftforge.eventbus.api.Event;

/** Posted on the shared Forge game bus by third-party mods to register an AskTool (Scope Y). */
public final class AskToolRegisterEvent extends Event {
    private final AskToolRegistration registration;

    public AskToolRegisterEvent(AskToolRegistration registration) {
        this.registration = registration;
    }

    public AskToolRegistration registration() {
        return registration;
    }
}
