package com.gigamax

import net.minecraft.ChatFormatting
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
 * Janela de captura (menu de bau de 5 linhas, so no servidor: nao precisa de codigo no cliente).
 *
 *  borda colorida pela dificuldade
 *  linha 2: [Pontos Dynamax]  [Especie/estrelas]  [Recompensas]
 *  linha 3:        [ BOLA DE CAPTURA ]  <- clique para tentar
 *  linha 4:   indicadores de tentativas (verde = disponivel, vermelho = usada)
 *  linha 5:        [ relogio ]
 */
class CaptureMenu(id: Int, inv: Inventory, private val box: SimpleContainer) :
    ChestMenu(MenuType.GENERIC_9x5, id, inv, box, 5) {

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
        const val SPECIES_SLOT = 13
        const val REWARD_SLOT = 15
        const val BUTTON_SLOT = 22
        const val ATTEMPTS_ROW_START = 27
        const val TIME_SLOT = 40

        private val PLAIN = Style.EMPTY.withItalic(false)

        fun open(player: ServerPlayer) {
            val info = RaidRewards.info(player) ?: return
            val box = SimpleContainer(45)
            fill(box, info)
            val title = Component.literal("Captura Dynamax ${"★".repeat(info.stars)}").withStyle(ChatFormatting.DARK_PURPLE)
            player.openMenu(SimpleMenuProvider({ id, inv, _ -> CaptureMenu(id, inv, box) }, title))
        }

        private fun stack(item: Item, name: String, lore: List<String> = emptyList()): ItemStack {
            val s = ItemStack(item)
            s.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(PLAIN))
            if (lore.isNotEmpty()) {
                s.set(DataComponents.LORE, ItemLore(lore.map { Component.literal(it).withStyle(PLAIN) }))
            }
            return s
        }

        private fun borderFor(stars: Int): Item = when (stars) {
            1 -> Items.LIGHT_GRAY_STAINED_GLASS_PANE
            2 -> Items.LIME_STAINED_GLASS_PANE
            3 -> Items.LIGHT_BLUE_STAINED_GLASS_PANE
            4 -> Items.PURPLE_STAINED_GLASS_PANE
            else -> Items.ORANGE_STAINED_GLASS_PANE
        }

        private fun fill(box: Container, info: RaidRewards.TicketInfo) {
            val border = borderFor(info.stars)
            for (i in 0 until box.containerSize) {
                val row = i / 9
                val col = i % 9
                val isBorder = row == 0 || row == 4 || col == 0 || col == 8
                box.setItem(i, stack(if (isBorder) border else Items.BLACK_STAINED_GLASS_PANE, " "))
            }

            val species = info.species.replaceFirstChar { it.uppercase() }
            val stars = "★".repeat(info.stars)

            box.setItem(
                INFO_SLOT,
                stack(Items.PAPER, "Pontos Dynamax: ${info.points}", listOf("Mais pontos = mais tentativas."))
            )
            box.setItem(
                SPECIES_SLOT,
                stack(Items.NAME_TAG, "$species $stars", listOf("Dificuldade: $stars"))
            )
            val rewardLines = info.rewards.map { (id, n) -> "${id.substringAfter(':')} x$n" }
            box.setItem(REWARD_SLOT, stack(Items.CHEST, "Recompensas recebidas", rewardLines))

            val ball = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse("cobblemon:ultra_ball")).orElse(Items.ENDER_PEARL)
            box.setItem(
                BUTTON_SLOT,
                stack(
                    ball, "CAPTURAR $species",
                    listOf(
                        "Chance por tentativa: ${(info.chance * 100).toInt()}%",
                        "Clique para tentar!"
                    )
                )
            )

            // indicadores de tentativas (ate 7), centralizados na linha 4
            val n = info.totalAttempts.coerceIn(1, 7)
            val used = (info.totalAttempts - info.attempts).coerceIn(0, n)
            val startCol = (7 - n) / 2 + 1
            for (i in 0 until n) {
                val slot = ATTEMPTS_ROW_START + startCol + i
                if (i < used) {
                    box.setItem(slot, stack(Items.RED_STAINED_GLASS_PANE, "Tentativa usada"))
                } else {
                    box.setItem(slot, stack(Items.LIME_STAINED_GLASS_PANE, "Tentativa disponivel"))
                }
            }

            box.setItem(TIME_SLOT, stack(Items.CLOCK, "Tempo restante: ${info.secondsLeft}s"))
        }
    }
}
