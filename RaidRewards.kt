package com.gigamax

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import net.minecraft.ChatFormatting
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import java.util.UUID

/**
 * Recompensas por dificuldade e captura do gigante derrotado com Pontos Dynamax.
 *
 * Pontos Dynamax = % do dano do gigante que o jogador causou. Mais pontos = mais tentativas de captura.
 * As tentativas sao usadas na janela de captura (CaptureMenu), com tempo limite.
 */
object RaidRewards {
    enum class Result { NO_TICKET, EXPIRED, SUCCESS, FAIL, ESCAPED }

    class TicketInfo(
        val species: String,
        val attempts: Int,
        val chance: Double,
        val points: Int,
        val stars: Int,
        val rewards: Map<String, Int>,
        val secondsLeft: Long,
        val totalAttempts: Int
    )

    private class Ticket(
        val species: String, val speciesName: String, val level: Int, var attempts: Int, val chance: Double,
        val points: Int, val stars: Int, val rewards: Map<String, Int>, val expiresAt: Long,
        val total: Int = attempts
    )

    private val tickets = HashMap<UUID, Ticket>()

    private fun openButton(): Component {
        val style = Style.EMPTY
            .withColor(ChatFormatting.GREEN)
            .withBold(true)
            .withClickEvent(ClickEvent(ClickEvent.Action.RUN_COMMAND, "/capturargigante"))
        return Component.literal("[ABRIR CAPTURA]").withStyle(style)
    }

    fun grant(player: ServerPlayer, tier: TierData, species: String, speciesName: String, points: Int, attempts: Int, mvp: Boolean, gameTime: Long) {
        val cfg = GigamaxConfig.data
        giveItems(player, tier)
        val mvpText = if (mvp) " (MVP: +1 tentativa!)" else ""
        player.sendSystemMessage(
            Component.literal("Recompensa ${"★".repeat(tier.stars)} recebida! Voce ganhou $points Pontos Dynamax$mvpText.")
        )

        if (points < cfg.minPointsToCapture) {
            player.sendSystemMessage(
                Component.literal("Voce precisa de pelo menos ${cfg.minPointsToCapture} Pontos Dynamax para tentar capturar.")
            )
            return
        }
        tickets[player.uuid] = Ticket(
            species, speciesName, tier.pokemonLevel, attempts, tier.catchChance, points, tier.stars,
            tier.rewards.toMap(), gameTime + cfg.captureWindowSeconds * 20L
        )
        player.sendSystemMessage(Component.literal("Janela de captura aberta! ").append(openButton()))
        CaptureMenu.open(player)
    }

    private fun giveItems(player: ServerPlayer, tier: TierData) {
        for ((id, count) in tier.rewards) {
            val item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(id)).orElse(null) ?: continue
            val stack = ItemStack(item, count)
            if (!player.addItem(stack)) player.drop(stack, false)
        }
    }

    /** Estado atual da captura pendente do jogador (null se nao ha ou se expirou). */
    fun info(player: ServerPlayer): TicketInfo? {
        val t = tickets[player.uuid] ?: return null
        val now = player.serverLevel().gameTime
        if (now > t.expiresAt) {
            tickets.remove(player.uuid)
            return null
        }
        return TicketInfo(
            t.speciesName, t.attempts, t.chance, t.points, t.stars, t.rewards,
            ((t.expiresAt - now) / 20L).coerceAtLeast(0L),
            t.total
        )
    }

    /** Usa uma tentativa de captura. */
    fun attempt(player: ServerPlayer): Result {
        val ticket = tickets[player.uuid] ?: return Result.NO_TICKET
        val now = player.serverLevel().gameTime
        if (now > ticket.expiresAt) {
            tickets.remove(player.uuid)
            return Result.EXPIRED
        }

        ticket.attempts -= 1
        if (player.serverLevel().random.nextDouble() < ticket.chance) {
            tickets.remove(player.uuid)
            val pokemon = PokemonProperties.parse("${ticket.species} level=${ticket.level}").create()
            val party = Cobblemon.storage.getParty(player)
            if (!party.add(pokemon)) {
                Cobblemon.storage.getPC(player).add(pokemon)
            }
            player.server.playerList.broadcastSystemMessage(
                Component.literal("${player.name.string} capturou um ${ticket.speciesName} Dynamax!"),
                false
            )
            return Result.SUCCESS
        }
        if (ticket.attempts <= 0) {
            tickets.remove(player.uuid)
            return Result.ESCAPED
        }
        return Result.FAIL
    }
}
