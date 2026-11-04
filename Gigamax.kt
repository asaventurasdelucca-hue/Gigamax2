package com.gigamax

import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent
import net.neoforged.neoforge.event.server.ServerStartingEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent

@Mod(Gigamax.MOD_ID)
object Gigamax {
    const val MOD_ID = "gigamax"

    init {
        GigamaxConfig.load()
        val bus = NeoForge.EVENT_BUS
        bus.addListener<RegisterCommandsEvent> { GigamaxCommand.register(it.dispatcher) }
        bus.addListener<ServerStartingEvent> { RaidBattles.registerCobblemonEvents() }
        bus.addListener<EntityJoinLevelEvent> {
            GiantManager.onJoin(it)
            RaidBattles.onJoin(it)
        }
        bus.addListener<EntityLeaveLevelEvent> { GiantManager.onLeave(it) }
        bus.addListener<PlayerInteractEvent.EntityInteract> { RaidBattles.onInteract(it) }
        bus.addListener<ServerTickEvent.Post> { GiantManager.onServerTick(it) }
        // o gigante (e o clone da batalha) nao recebem dano normal, nem de jogadores no criativo
        bus.addListener<LivingIncomingDamageEvent> {
            val tags = it.entity.tags
            if (tags.contains(GiantManager.TAG) || tags.contains(RaidBattles.CLONE_TAG)) it.isCanceled = true
        }
    }
}
