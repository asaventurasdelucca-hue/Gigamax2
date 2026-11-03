package com.gigamax

import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent
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
    }
}
