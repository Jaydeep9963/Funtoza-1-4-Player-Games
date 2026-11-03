package com.aaritlabs.funtoza.model

import java.io.Serializable

data class TournamentState(
    val totalPlayers: Int,
    val totalRounds: Int = 5,
    var currentRound: Int = 1,
    val scores: MutableMap<PlayerId, Int> = mutableMapOf(),
    val gamePlaylist: List<GameType> = GameType.getAllGames().shuffled()
) : Serializable {

    init {
        for (i in 1..totalPlayers) {
            scores[PlayerId.fromId(i)] = 0
        }
    }

    fun getCurrentGame(): GameType {
        val index = (currentRound - 1) % gamePlaylist.size
        return gamePlaylist[index]
    }

    fun addPoints(playerId: PlayerId, points: Int = 1) {
        scores[playerId] = (scores[playerId] ?: 0) + points
    }

    fun isGameOver(): Boolean = currentRound > totalRounds

    fun getLeaderboard(): List<Pair<PlayerId, Int>> {
        return scores.toList().sortedByDescending { it.second }
    }
}

