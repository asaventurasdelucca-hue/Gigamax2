package com.gigamax

import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerBossEvent
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.BossEvent
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin

/**
 * Gerencia os Pokemon gigantes no mundo (aura, barra de boss, spawn natural, vida compartilhada, recompensas).
 * A vida do gigante e um valor de 1.0 (cheio) a 0.0, reduzido pelo dano das batalhas dos jogadores (ver RaidBattles).
 */
object GiantManager {
    const val TAG = "gigamax"
    private const val BAR_RANGE = 64.0

    class Giant(val entity: PokemonEntity, val bar: ServerBossEvent, val spawnedAt: Long) {
        var hp: Double = 1.0
        /** jogador -> dano total causado (em fracao da vida do gigante) */
        val contributors = LinkedHashMap<UUID, Double>()
    }

    private val giants = HashMap<UUID, Giant>()

    fun isGiant(uuid: UUID) = giants.containsKey(uuid)

    /** Nome exibido (ex: "Dynamax Charizard"), configuravel em nameFormat. */
    fun displayName(species: String): String =
        GigamaxConfig.data.nameFormat.replace("{species}", species.replaceFirstChar { it.uppercase() })

    /** Cria o gigante. O registro na lista acontece no evento de entrada no mundo. */
    fun spawn(level: ServerLevel, species: String, pos: Vec3, scale: Float): PokemonEntity {
        val props = PokemonProperties.parse("$species level=${GigamaxConfig.data.pokemonLevel}")
        val entity = props.createEntity(level)
        entity.pokemon.scaleModifier = scale
        entity.addTag(TAG)
        entity.setPos(pos.x, pos.y, pos.z)
        level.addFreshEntity(entity)
        return entity
    }

    /** Aplica dano (fracao da vida total) ao gigante. Retorna a vida restante, ou null se ele nao existe mais. */
    fun applyDamage(giantUuid: UUID, player: ServerPlayer, fraction: Double): Double? {
        val g = giants[giantUuid] ?: return null
        if (g.hp <= 0.0) return 0.0
        g.hp = (g.hp - fraction).coerceAtLeast(0.0)
        g.contributors.merge(player.uuid, fraction, Double::plus)
        g.bar.progress = g.hp.toFloat()
        if (g.hp <= 0.0) defeat(g)
        return g.hp
    }

    private fun defeat(g: Giant) {
        val server = g.entity.server ?: return
        val name = displayName(g.entity.pokemon.species.name)
        server.playerList.broadcastSystemMessage(
            Component.literal("$name foi derrotado! ${g.contributors.size} treinador(es) participaram."),
            false
        )
        for (uuid in g.contributors.keys) {
            val p = server.playerList.getPlayer(uuid) ?: continue
            giveRewards(p)
        }
        g.entity.discard()
    }

    private fun giveRewards(player: ServerPlayer) {
        for ((id, count) in GigamaxConfig.data.rewards) {
            val item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(id)).orElse(null) ?: continue
            val stack = ItemStack(item, count)
            if (!player.addItem(stack)) player.drop(stack, false)
        }
        player.sendSystemMessage(Component.literal("Recompensa do gigante recebida!"))
    }

    fun onJoin(event: EntityJoinLevelEvent) {
        val entity = event.entity as? PokemonEntity ?: return
        val level = event.level as? ServerLevel ?: return
        if (!entity.tags.contains(TAG)) return

        val name = displayName(entity.pokemon.species.name)
        val bar = ServerBossEvent(
            Component.literal(name).withStyle(ChatFormatting.RED),
            BossEvent.BossBarColor.RED,
            BossEvent.BossBarOverlay.PROGRESS
        )
        bar.progress = 1.0f
        // o gigante nao pode ser morto por dano normal; so cai pela vida compartilhada
        entity.isInvulnerable = true
        // brilho vermelho (cor do time) no estilo Dynamax
        entity.setGlowingTag(true)
        val sb = level.server.scoreboard
        val team = sb.getPlayerTeam("gigamax") ?: sb.addPlayerTeam("gigamax")
        team.color = ChatFormatting.RED
        sb.addPlayerToTeam(entity.scoreboardName, team)
        giants[entity.uuid] = Giant(entity, bar, level.gameTime)
    }

    fun onLeave(event: EntityLeaveLevelEvent) {
        val giant = giants.remove(event.entity.uuid) ?: return
        giant.bar.removeAllPlayers()
        val sb = event.entity.server?.scoreboard
        val team = sb?.getPlayerTeam("gigamax")
        if (sb != null && team != null) sb.removePlayerFromTeam(event.entity.scoreboardName, team)
    }

    fun onServerTick(event: ServerTickEvent.Post) {
        val server = event.server
        val time = server.overworld().gameTime
        if (time % 10L == 0L) tickGiants(time)
        if (time % 100L == 0L) RaidBattles.sweep()
        val interval = (GigamaxConfig.data.intervalSeconds.coerceAtLeast(5) * 20L)
        if (time % interval == 0L) naturalSpawn(server)
    }

    private fun tickGiants(time: Long) {
        val lifetimeTicks = GigamaxConfig.data.lifetimeMinutes * 60L * 20L
        for (g in giants.values.toList()) {
            val e = g.entity
            val level = e.level() as? ServerLevel
            if (e.isRemoved || level == null) {
                g.bar.removeAllPlayers()
                giants.remove(e.uuid)
                continue
            }
            if (time - g.spawnedAt > lifetimeTicks) {
                e.discard()
                continue
            }
            for (p in level.players()) {
                if (p.distanceToSqr(e) <= BAR_RANGE * BAR_RANGE) g.bar.addPlayer(p) else g.bar.removePlayer(p)
            }
            level.sendParticles(
                ParticleTypes.DRAGON_BREATH,
                e.x, e.y + e.bbHeight / 2.0, e.z,
                8, e.bbWidth * 0.5, e.bbHeight * 0.4, e.bbWidth * 0.5, 0.02
            )
        }
    }

    private fun naturalSpawn(server: MinecraftServer) {
        val cfg = GigamaxConfig.data
        if (!cfg.enabled || cfg.species.isEmpty()) return

        for (player in server.playerList.players) {
            val level = player.serverLevel()
            val here = giants.values.filter { it.entity.level() == level }
            if (here.size >= cfg.maxGiantsPerLevel) continue
            if (here.any { it.entity.distanceTo(player) < cfg.maxDistance * 2 }) continue
            if (level.random.nextDouble() > cfg.chancePerPlayer) continue

            val angle = level.random.nextDouble() * Math.PI * 2
            val dist = cfg.minDistance + level.random.nextDouble() * (cfg.maxDistance - cfg.minDistance)
            val bp = BlockPos.containing(player.x + cos(angle) * dist, player.y, player.z + sin(angle) * dist)
            if (!level.hasChunkAt(bp)) continue

            val top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bp)
            if (!level.getFluidState(top.below()).isEmpty) continue

            val species = cfg.species.random()
            spawn(level, species, Vec3(top.x + 0.5, top.y.toDouble(), top.z + 0.5), cfg.scale)
            server.playerList.broadcastSystemMessage(
                Component.literal("${displayName(species)} apareceu perto de ${player.name.string}! Clique com a mao vazia nele para lutar."),
                false
            )
        }
    }
}
