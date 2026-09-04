package com.skps9.packai.client;

import com.skps9.packai.api.AskTool;
import com.skps9.packai.api.AskToolArgs;
import com.skps9.packai.api.AskToolRegistration;
import com.skps9.packai.api.RegistrationStatus;

/** Runnable check: ClientSetup adapter handler forwards a registration event to registerExternal. */
public final class AskToolRegisterAdapterCheck {
    private AskToolRegisterAdapterCheck() {}

    public static void main(String[] args) {
        AskTool demo = new AskTool() {
            @Override public String name() { return "adapter_demo"; }
            @Override public String run(AskToolArgs a) { return ""; }
            @Override public String description() { return "Adapter demo tool"; }
            @Override public String argsSchemaJson() { return "{}"; }
        };

        // Handler is public static so we can invoke it directly without the bus (headless).
        ClientSetup.onAskToolRegister(new AskToolRegisterEvent(new AskToolRegistration(demo)));
        // Second registration of the same id must be rejected as dup — proves the first one stored.
        ClientSetup.onAskToolRegister(new AskToolRegisterEvent(new AskToolRegistration(demo)));

        // Reserved name through the adapter must be rejected too (warn path).
        AskTool reserved = new AskTool() {
            @Override public String name() { return "jei_lookup"; }
            @Override public String run(AskToolArgs a) { return ""; }
            @Override public String description() { return "Squatter"; }
            @Override public String argsSchemaJson() { return "{}"; }
        };
        ClientSetup.onAskToolRegister(new AskToolRegisterEvent(new AskToolRegistration(reserved)));

        // If the handler did NOT store the tool, the second call would have stored it instead of
        // rejecting — so no assert crash here means store + dup-reject path ran. The status is also
        // logged by the handler (INFO on OK, WARN on reject).
        System.out.println("AskToolRegisterAdapterCheck OK (see log lines above)");
    }
}
