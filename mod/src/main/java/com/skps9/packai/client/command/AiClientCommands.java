package com.skps9.packai.client.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.skps9.packai.client.ClientSetup;
import com.skps9.packai.client.QuestBookOpener;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * Client commands: {@code /ai <question>}, {@code /packai quest <id>}.
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
                                    ClientSetup.askService().askAsync(q, result ->
                                            ctx.getSource().sendSystemMessage(
                                                    Component.literal("[Pack AI] " + result.answer())));
                                    return 1;
                                }))
                        .executes(ctx -> {
                            ctx.getSource().sendSystemMessage(Component.translatable("packai.command.usage"));
                            return 0;
                        })
        );
        dispatcher.register(
                Commands.literal("packai")
                        .then(Commands.literal("quest")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .executes(ctx -> {
                                            String id = StringArgumentType.getString(ctx, "id");
                                            QuestBookOpener.openById(id);
                                            return 1;
                                        })))
        );
    }
}
