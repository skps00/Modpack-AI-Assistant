package com.skps9.packai.client.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.skps9.packai.client.ClientSetup;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * Backup trigger: client-side /ai &lt;question&gt;.
 */
public final class AiClientCommands {
    private AiClientCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("ai")
                        .then(Commands.argument("question", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    String q = StringArgumentType.getString(ctx, "question");
                                    ctx.getSource().sendSystemMessage(Component.literal("[Pack AI] …"));
                                    ClientSetup.askService().askAsync(q, answer ->
                                            ctx.getSource().sendSystemMessage(Component.literal("[Pack AI] " + answer)));
                                    return 1;
                                }))
                        .executes(ctx -> {
                            ctx.getSource().sendSystemMessage(Component.translatable("packai.command.usage"));
                            return 0;
                        })
        );
    }
}
