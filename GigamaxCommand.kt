package com.gigamax

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component

/**
 * /gigante <especie> [estrelas 1-5]  -> spawna um gigante na sua posicao (OP)
 * /gigante recarregar                -> recarrega config/gigamax.json (OP)
 * /capturargigante                   -> abre a janela de captura apos uma raid (qualquer jogador)
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
                        .executes { spawn(it, StringArgumentType.getString(it, "especie"), 3) }
                        .then(
                            Commands.argument("estrelas", IntegerArgumentType.integer(1, 5))
                                .executes {
                                    spawn(
                                        it,
                                        StringArgumentType.getString(it, "especie"),
                                        IntegerArgumentType.getInteger(it, "estrelas")
                                    )
                                }
                        )
                )
        )

        dispatcher.register(
            Commands.literal("capturargigante").executes {
                val player = it.source.playerOrException
                if (RaidRewards.info(player) == null) {
                    player.sendSystemMessage(Component.literal("Voce nao tem nenhuma captura de gigante pendente."))
                } else {
                    CaptureMenu.open(player)
                }
                1
            }
        )
    }

    private fun spawn(ctx: CommandContext<CommandSourceStack>, species: String, stars: Int): Int {
        val src = ctx.source
        val tier = GigamaxConfig.tier(stars)
        GiantManager.spawn(src.level, species, src.position, tier)
        src.sendSuccess({ Component.literal("${GiantManager.displayName(species, tier.stars)} apareceu!") }, true)
        return 1
    }
}
