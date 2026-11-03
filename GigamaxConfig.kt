package com.gigamax

import com.google.gson.GsonBuilder
import net.neoforged.fml.loading.FMLPaths
import java.nio.file.Files

/** Arquivo gerado em config/gigamax.json na primeira execucao. */
class GigamaxConfigData {
    var enabled: Boolean = true
    /** A cada quantos segundos o servidor tenta spawnar um gigante perto de cada jogador. */
    var intervalSeconds: Int = 300
    /** Chance (0.0 a 1.0) por tentativa, por jogador. */
    var chancePerPlayer: Double = 0.25
    var maxGiantsPerLevel: Int = 3
    var minDistance: Int = 40
    var maxDistance: Int = 80
    var scale: Float = 3.0f
    var pokemonLevel: Int = 60
    /** Tempo que o gigante fica no mundo antes de sumir. */
    var lifetimeMinutes: Int = 15
    /** Quantas "barras de vida" de um Pokemon normal o gigante tem. Maior = precisa de mais jogadores/batalhas. */
    var battlesToKill: Int = 4
    /** Recompensa (id do item -> quantidade) entregue a quem causou dano quando o gigante cai. */
    var rewards: MutableMap<String, Int> = mutableMapOf(
        "cobblemon:rare_candy" to 3,
        "cobblemon:ultra_ball" to 5
    )
    var species: MutableList<String> = mutableListOf(
        "charizard", "gyarados", "lapras", "snorlax", "machamp", "corviknight",
        "dragapult", "duraludon", "copperajah", "garbodor",
        "articuno", "zapdos", "moltres", "zacian", "zamazenta", "eternatus"
    )
}

object GigamaxConfig {
    var data = GigamaxConfigData()
    private val gson = GsonBuilder().setPrettyPrinting().create()

    fun load() {
        val file = FMLPaths.CONFIGDIR.get().resolve("gigamax.json")
        try {
            if (Files.exists(file)) {
                data = gson.fromJson(Files.readString(file), GigamaxConfigData::class.java) ?: data
            } else {
                Files.writeString(file, gson.toJson(data))
            }
        } catch (e: Exception) {
            System.err.println("[Gigamax] Erro lendo config, usando padrao: $e")
        }
    }
}
