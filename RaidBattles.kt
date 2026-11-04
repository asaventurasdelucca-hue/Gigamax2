package com.gigamax

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.battles.BattleBuilder
import com.cobblemon.mod.common.battles.BattleRegistry
import com.cobblemon.mod.common.battles.SuccessfulBattleStart
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.network.chat.Component
import org.apache.logging.log4j.LogManager
import net.neoforged.neoforge.server.ServerLifecycleHooks
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent
import java.util.UUID

/**
 * Etapa 3: dano compartilhado.
 *
 * Cada jogador que clica no gigante (mao vazia) luta a PROPRIA batalha contra um clone invisivel
 * e nao capturavel do gigante. Ao fim da batalha, o dano causado ao clone (fracao da vida dele)
 * e descontado da vida compartilhada do gigante. Qualquer jogador pode entrar a qualquer momento.
 */
object RaidBattles {
    const val CLONE_TAG = "gigamax_clone"

    private val LOG = LogManager.getLogger("Gigamax")

    private class Link(val giantUuid: UUID, val clone: PokemonEntity, val playerUuid: UUID)

    private val links = HashMap<UUID, Link>()       // battleId -> link
    private val allowedClones = HashSet<UUID>()      // clones criados por nos nesta sessao

    /** Chamado quando o servidor inicia (Cobblemon ja carregado). */
    fun registerCobblemonEvents() {
        // Batalha "normal" contra o gigante real (jogador mandou o Pokemon dele): bloqueia,
        // pois ela nao conta para a vida compartilhada. O jogador deve clicar com a mao vazia.
        CobblemonEvents.BATTLE_STARTED_PRE.subscribe(Priority.HIGHEST) { event ->
            val battle = event.battle
            val hitsGiant = battle.actors.any { actor ->
                actor.pokemonList.any { bp -> bp.entity?.tags?.contains(GiantManager.TAG) == true }
            }
            if (hitsGiant) {
                event.cancel()
                LOG.info("Batalha normal contra gigante cancelada")
                battle.players.forEach {
                    it.sendSystemMessage(Component.literal("Para lutar contra o gigante, clique nele com a mao vazia!"))
                }
            }
        }
        CobblemonEvents.BATTLE_VICTORY.subscribe(Priority.NORMAL) { event ->
            LOG.info("BATTLE_VICTORY recebido: ${event.battle.battleId} (links=${links.size})")
            finish(event.battle.battleId)
        }
        CobblemonEvents.BATTLE_FLED.subscribe(Priority.NORMAL) { event ->
            LOG.info("BATTLE_FLED recebido: ${event.battle.battleId}")
            finish(event.battle.battleId)
        }
        LOG.info("Eventos do Cobblemon registrados")
    }

    fun onInteract(event: PlayerInteractEvent.EntityInteract) {
        val player = event.entity as? ServerPlayer ?: return
        if (event.hand != InteractionHand.MAIN_HAND) return
        val giant = event.target as? PokemonEntity ?: return
        if (!GiantManager.isGiant(giant.uuid)) return
        event.isCanceled = true
        event.cancellationResult = InteractionResult.SUCCESS
        startBattle(player, giant)
    }

    /** Remove clones "orfaos" (ex: sobraram de uma sessao anterior do servidor). */
    fun onJoin(event: EntityJoinLevelEvent) {
        val entity = event.entity as? PokemonEntity ?: return
        if (entity.tags.contains(CLONE_TAG) && !allowedClones.contains(entity.uuid)) {
            event.isCanceled = true
        }
    }

    private fun startBattle(player: ServerPlayer, giant: PokemonEntity) {
        val level = giant.level() as? ServerLevel ?: return

        val species = giant.pokemon.species.name
        val props = PokemonProperties.parse("$species level=${giant.pokemon.level} uncatchable=yes")
        val clone = props.createEntity(level)
        // minusculo: o Cobblemon desenha o modelo mesmo "invisivel", entao encolhemos o clone
        clone.pokemon.scaleModifier = GigamaxConfig.data.cloneScale
        clone.addTag(CLONE_TAG)
        clone.isInvisible = true
        clone.isInvulnerable = true
        clone.setNoAi(true)
        clone.setPos(giant.x, giant.y, giant.z)

        allowedClones.add(clone.uuid)
        level.addFreshEntity(clone)

        val result = BattleBuilder.pve(player, clone)
        LOG.info("pve(${player.name.string}) -> ${result::class.simpleName}")
        if (result is SuccessfulBattleStart) {
            links[result.battle.battleId] = Link(giant.uuid, clone, player.uuid)
        } else {
            clone.discard()
            allowedClones.remove(clone.uuid)
            player.sendSystemMessage(
                Component.literal("Nao foi possivel iniciar a batalha. Voce precisa de um Pokemon saudavel e nao pode ja estar em outra batalha.")
            )
        }
    }

    private fun finish(battleId: UUID) {
        val link = links.remove(battleId)
        if (link == null) {
            LOG.info("finish($battleId): batalha nao e do Gigamax (ou ja processada)")
            return
        }
        val clone = link.clone
        val pokemon = clone.pokemon
        val maxHp = pokemon.maxHealth.coerceAtLeast(1)
        val dealt = (1.0 - pokemon.currentHealth.toDouble() / maxHp).coerceIn(0.0, 1.0)

        clone.discard()
        allowedClones.remove(clone.uuid)

        val server = clone.server ?: ServerLifecycleHooks.getCurrentServer() ?: return
        val player = server.playerList.getPlayer(link.playerUuid) ?: return
        val fraction = dealt / GiantManager.battlesToKill(link.giantUuid).coerceAtLeast(1)
        val remaining = GiantManager.applyDamage(link.giantUuid, player, fraction)
        LOG.info("finish: dealt=$dealt fraction=$fraction restante=$remaining")
        if (remaining == null) {
            player.sendSystemMessage(Component.literal("O gigante ja sumiu, seu dano nao foi contado."))
            return
        }

        if (remaining > 0.0) {
            player.sendSystemMessage(
                Component.literal("Voce causou ${(fraction * 100).toInt()}% de dano! Vida restante do gigante: ${(remaining * 100).toInt()}%.")
            )
        }
    }

    /** Limpeza de seguranca: batalhas que acabaram sem disparar evento (ex: jogador deslogou). */
    fun sweep() {
        for (id in links.keys.toList()) {
            if (BattleRegistry.getBattle(id) == null) finish(id)
        }
    }
}
