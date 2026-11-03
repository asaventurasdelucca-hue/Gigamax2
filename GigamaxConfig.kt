package com.gigamax

import com.google.gson.GsonBuilder
import net.minecraft.util.RandomSource
import net.neoforged.fml.loading.FMLPaths
import java.nio.file.Files

/** Um nivel de dificuldade (estrelas) de gigante. */
class TierData {
    var stars: Int = 1
    /** Peso no sorteio do spawn natural (maior = mais comum). */
    var weight: Int = 10
    var scale: Float = 3.0f
    var pokemonLevel: Int = 40
    /** Quantas "barras de vida" de um Pokemon normal o gigante tem. */
    var battlesToKill: Int = 3
    /** Chance de CADA tentativa de captura dar certo (0.0 a 1.0). */
    var catchChance: Double = 0.5
    /** Maximo de tentativas de captura que um jogador pode ganhar. */
    var maxAttempts: Int = 3
    /** Recompensa (id do item -> quantidade) para quem causou dano. */
    var rewards: MutableMap<String, Int> = mutableMapOf()
    /** Especies que podem aparecer nesta dificuldade. */
    var species: MutableList<String> = mutableListOf()
}

private fun tier(
    stars: Int, weight: Int, scale: Float, level: Int, battlesToKill: Int,
    catchChance: Double, maxAttempts: Int,
    rewards: Map<String, Int>, species: List<String>
): TierData {
    val t = TierData()
    t.stars = stars; t.weight = weight; t.scale = scale; t.pokemonLevel = level
    t.battlesToKill = battlesToKill; t.catchChance = catchChance; t.maxAttempts = maxAttempts
    t.rewards = rewards.toMutableMap(); t.species = species.toMutableList()
    return t
}

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
    /** Tempo que o gigante fica no mundo antes de sumir. */
    var lifetimeMinutes: Int = 15
    /** Como o gigante aparece nas mensagens e na barra. {stars} = estrelas, {species} = nome do Pokemon. */
    var nameFormat: String = "{stars} Dynamax {species}"
    /** Escala do clone usado nas batalhas (quase invisivel, para nao aparecer um segundo Pokemon). */
    var cloneScale: Float = 0.01f

    /** Pontos Dynamax = % do dano total do gigante que o jogador causou (0 a 100). */
    var minPointsToCapture: Int = 5
    /** Cada X pontos Dynamax dao +1 tentativa de captura (alem da 1a). O jogador que mais causou dano ganha +1. */
    var pointsPerExtraAttempt: Int = 20
    /** Tempo para usar as tentativas de captura depois que o gigante cai. */
    var captureWindowSeconds: Int = 120

    var tiers: MutableList<TierData> = mutableListOf(
        tier(1, 45, 3.0f, 35, 3, 0.60, 3,
            mapOf("cobblemon:rare_candy" to 1, "cobblemon:ultra_ball" to 3),
            listOf("garbodor", "copperajah", "corviknight", "machamp", "lapras")),
        tier(2, 30, 3.5f, 45, 4, 0.45, 3,
            mapOf("cobblemon:rare_candy" to 2, "cobblemon:ultra_ball" to 5),
            listOf("charizard", "gyarados", "snorlax", "lapras", "machamp", "corviknight")),
        tier(3, 15, 4.0f, 55, 5, 0.35, 4,
            mapOf("cobblemon:rare_candy" to 3, "cobblemon:exp_candy_xl" to 3, "cobblemon:ultra_ball" to 5),
            listOf("dragapult", "duraludon", "charizard", "garchomp", "tyranitar")),
        tier(4, 8, 5.0f, 65, 6, 0.25, 4,
            mapOf("cobblemon:rare_candy" to 5, "cobblemon:exp_candy_xl" to 5, "cobblemon:ability_capsule" to 1),
            listOf("articuno", "zapdos", "moltres", "dragapult", "salamence")),
        tier(5, 2, 6.0f, 75, 8, 0.15, 5,
            mapOf("cobblemon:rare_candy" to 8, "cobblemon:exp_candy_xl" to 8, "cobblemon:ability_capsule" to 1),
            listOf("zacian", "zamazenta", "eternatus"))
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
        if (data.tiers.isEmpty()) data = GigamaxConfigData()
    }

    fun tier(stars: Int): TierData = data.tiers.firstOrNull { it.stars == stars } ?: data.tiers.first()

    /** Sorteia uma dificuldade pelos pesos. */
    fun rollTier(random: RandomSource): TierData {
        val candidates = data.tiers.filter { it.species.isNotEmpty() && it.weight > 0 }
        if (candidates.isEmpty()) return data.tiers.first()
        var roll = random.nextInt(candidates.sumOf { it.weight })
        for (t in candidates) {
            roll -= t.weight
            if (roll < 0) return t
        }
        return candidates.last()
    }
}
