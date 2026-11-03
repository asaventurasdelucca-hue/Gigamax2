package com.gigamax

import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.Container
import net.minecraft.world.SimpleContainer
import net.minecraft.world.SimpleMenuProvider
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ItemLore

/**
 * Janela de captura (menu de bau, so no servidor: nao precisa de codigo no cliente).
 * Centro: botao [CAPTURAR]. Esquerda: Pontos Dynamax. Direita: recompensas recebidas.
 */
class CaptureMenu(id: Int, inv: Inventory, private val box: SimpleContainer) :
    ChestMenu(MenuType.GENERIC_9x3, id, inv, box, 3) {

    // Bloqueia qualquer movimentacao de itens; so o clique no botao faz algo.
    override fun clicked(slotId: Int, button: Int, clickType: ClickType, player: Player) {
        if (player is ServerPlayer && slotId == BUTTON_SLOT && clickType == ClickType.PICKUP) {
            onButton(player)
        }
        carried = ItemStack.EMPTY
        broadcastFullState()
    }

    override fun quickMoveStack(player: Player, index: Int): ItemStack = ItemStack.EMPTY

    private fun onButton(player: ServerPlayer) {
        when (RaidRewards.attempt(player)) {
            RaidRewards.Result.SUCCESS -> {
                player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1f, 1f)
                player.closeContainer()
            }
            RaidRewards.Result.ESCAPED -> {
                player.sendSystemMessage(Component.literal("O Pokemon escapou! Acabaram as tentativas."))
                player.closeContainer()
            }
            RaidRewards.Result.EXPIRED, RaidRewards.Result.NO_TICKET -> {
                player.sendSystemMessage(Component.literal("O tempo para capturar acabou. O Pokemon fugiu!"))
                player.closeContainer()
            }
            RaidRewards.Result.FAIL -> {
                player.playNotifySound(SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 1f, 1f)
                player.displayClientMessage(Component.literal("Quase! Ele escapou da bola..."), true)
                val info = RaidRewards.info(player)
                if (info != null) fill(box, info) else player.closeContainer()
            }
        }
    }

    companion object {
        const val INFO_SLOT = 11
        const val BUTTON_SLOT = 13
        const val REWARD_SLOT = 15

        private val PLAIN = Style.EMPTY.withItalic(false)

        fun open(player: ServerPlayer) {
            val info = RaidRewards.info(player) ?: return
            val box = SimpleContainer(27)
            fill(box, info)
            player.openMenu(
                SimpleMenuProvider({ id, inv, _ -> CaptureMenu(id, inv, box) }, Component.literal("Captura Dynamax"))
            )
        }

        private fun stack(item: Item, name: String, lore: List<String> = emptyList()): ItemStack {
            val s = ItemStack(item)
            s.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(PLAIN))
            if (lore.isNotEmpty()) {
                s.set(DataComponents.LORE, ItemLore(lore.map { Component.literal(it).withStyle(PLAIN) }))
            }
            return s
        }

        private fun fill(box: Container, info: RaidRewards.TicketInfo) {
            for (i in 0 until box.containerSize) box.setItem(i, stack(Items.BLACK_STAINED_GLASS_PANE, " "))

            val species = info.species.replaceFirstChar { it.uppercase() }
            box.setItem(
                INFO_SLOT,
                stack(
                    Items.PAPER, "Pontos Dynamax: ${info.points}",
                    listOf(
                        "$species ${"★".repeat(info.stars)}",
                        "Mais pontos = mais tentativas.",
                        "Tempo restante: ${info.secondsLeft}s"
                    )
                )
            )

            val ball = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse("cobblemon:ultra_ball")).orElse(Items.ENDER_PEARL)
            box.setItem(
                BUTTON_SLOT,
                stack(
                    ball, "CAPTURAR $species",
                    listOf(
                        "Tentativas restantes: ${info.attempts}",
                        "Chance por tentativa: ${(info.chance * 100).toInt()}%",
                        "Clique para tentar!"
                    )
                )
            )

            val rewardLines = info.rewards.map { (id, n) -> "${id.substringAfter(':')} x$n" }
            box.setItem(REWARD_SLOT, stack(Items.CHEST, "Recompensas recebidas", rewardLines))
        }
    }
}
