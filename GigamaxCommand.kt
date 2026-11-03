package com.gigamax

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.FloatArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component

/**
 * /gigante <especie> [escala]  -> spawna um gigante na sua posicao (OP)
 * /gigante recarregar          -> recarrega config/gigamax.json
 */
object GigamaxCommand {
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("gigante")
                .requires { it.hasPermission(2) }
                .then(
                    Commands.literal("recarregar").executes {
                        GigamaxConfig.load()
                        it.source.sendSuccess({ Component.literal("Config do Gigamax recarregada.") }, true)
                        1
                    }
                )
                .then(
                    Commands.argument("especie", StringArgumentType.word())
                        .executes { spawn(it, StringArgumentType.getString(it, "especie"), GigamaxConfig.data.scale) }
                        .then(
                            Commands.argument("escala", FloatArgumentType.floatArg(1f, 10f))
                                .executes {
                                    spawn(
                                        it,
                                        StringArgumentType.getString(it, "especie"),
                                        FloatArgumentType.getFloat(it, "escala")
                                    )
                                }
                        )
                )
        )
    }

    private fun spawn(ctx: CommandContext<CommandSourceStack>, species: String, scale: Float): Int {
        val src = ctx.source
        GiantManager.spawn(src.level, species, src.position, scale)
        src.sendSuccess({ Component.literal("Um $species gigante (x$scale) apareceu!") }, true)
        return 1
    }
}
