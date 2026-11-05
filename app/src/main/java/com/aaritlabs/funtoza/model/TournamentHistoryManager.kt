package com.aaritlabs.funtoza.model

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SavedTournament(
    val id: String,
    val title: String,
    val dateStr: String,
    val totalPlayers: Int,
    val currentRound: Int,
    val totalRounds: Int,
    val playerNames: List<String>,
    val scores: Map<Int, Int>, // PlayerId.id -> score
    val playlistGameNames: List<String>, // GameType.name
    val isCompleted: Boolean = false,
    val winnerName: String? = null
) : Serializable

object TournamentHistoryManager {

    private const val PREFS_NAME = "funtoza_tournament_prefs"
    private const val KEY_SAVED_LIST = "saved_tournaments_json"
    private const val KEY_LAST_ACTIVE_ID = "last_active_tournament_id"

    fun saveTournament(
        context: Context,
        state: TournamentState,
        playerNames: List<String>,
        isCompleted: Boolean = false,
        winnerName: String? = null
    ): SavedTournament {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val list = getSavedTournaments(context).toMutableList()

        val id = state.id.ifEmpty { System.currentTimeMillis().toString() }
        val dateStr = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date())
        val title = if (isCompleted) {
            "🏆 Cup Won by ${winnerName ?: "Player"}"
        } else {
            "🏆 Cup (Round ${state.currentRound} of ${state.totalRounds})"
        }

        val scoresMap = mutableMapOf<Int, Int>()
        state.scores.forEach { (playerId, score) ->
            scoresMap[playerId.id] = score
        }

        val gameNames = state.gamePlaylist.map { it.name }

        val saved = SavedTournament(
            id = id,
            title = title,
            dateStr = dateStr,
            totalPlayers = state.totalPlayers,
            currentRound = state.currentRound,
            totalRounds = state.totalRounds,
            playerNames = playerNames,
            scores = scoresMap,
            playlistGameNames = gameNames,
            isCompleted = isCompleted,
            winnerName = winnerName
        )

        // Remove existing item with same id if updating
        list.removeAll { it.id == id }
        // Insert at beginning of list
        list.add(0, saved)

        // Keep maximum 15 tournaments in history
        val trimmed = if (list.size > 15) list.take(15) else list

        val jsonArray = JSONArray()
        trimmed.forEach { item ->
            val obj = JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("dateStr", item.dateStr)
                put("totalPlayers", item.totalPlayers)
                put("currentRound", item.currentRound)
                put("totalRounds", item.totalRounds)
                put("isCompleted", item.isCompleted)
                put("winnerName", item.winnerName ?: "")

                val namesArr = JSONArray()
                item.playerNames.forEach { namesArr.put(it) }
                put("playerNames", namesArr)

                val scoresObj = JSONObject()
                item.scores.forEach { (k, v) -> scoresObj.put(k.toString(), v) }
                put("scores", scoresObj)

                val gamesArr = JSONArray()
                item.playlistGameNames.forEach { gamesArr.put(it) }
                put("playlistGameNames", gamesArr)
            }
            jsonArray.put(obj)
        }

        prefs.edit().apply {
            putString(KEY_SAVED_LIST, jsonArray.toString())
            if (!isCompleted) {
                putString(KEY_LAST_ACTIVE_ID, id)
            } else if (prefs.getString(KEY_LAST_ACTIVE_ID, "") == id) {
                remove(KEY_LAST_ACTIVE_ID)
            }
            apply()
        }

        return saved
    }

    fun getSavedTournaments(context: Context): List<SavedTournament> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_SAVED_LIST, null) ?: return emptyList()

        val list = mutableListOf<SavedTournament>()
        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.getString("id")
                val title = obj.optString("title", "Cup Tournament")
                val dateStr = obj.optString("dateStr", "")
                val totalPlayers = obj.optInt("totalPlayers", 2)
                val currentRound = obj.optInt("currentRound", 1)
                val totalRounds = obj.optInt("totalRounds", 5)
                val isCompleted = obj.optBoolean("isCompleted", false)
                val winnerName = obj.optString("winnerName").ifEmpty { null }

                val namesList = mutableListOf<String>()
                val namesArr = obj.optJSONArray("playerNames")
                if (namesArr != null) {
                    for (j in 0 until namesArr.length()) {
                        namesList.add(namesArr.getString(j))
                    }
                }

                val scoresMap = mutableMapOf<Int, Int>()
                val scoresObj = obj.optJSONObject("scores")
                if (scoresObj != null) {
                    val keys = scoresObj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        scoresMap[key.toInt()] = scoresObj.getInt(key)
                    }
                }

                val gamesList = mutableListOf<String>()
                val gamesArr = obj.optJSONArray("playlistGameNames")
                if (gamesArr != null) {
                    for (j in 0 until gamesArr.length()) {
                        gamesList.add(gamesArr.getString(j))
                    }
                }

                list.add(
                    SavedTournament(
                        id = id,
                        title = title,
                        dateStr = dateStr,
                        totalPlayers = totalPlayers,
                        currentRound = currentRound,
                        totalRounds = totalRounds,
                        playerNames = namesList,
                        scores = scoresMap,
                        playlistGameNames = gamesList,
                        isCompleted = isCompleted,
                        winnerName = winnerName
                    )
                )
            }
        } catch (_: Exception) {}

        return list
    }

    fun getActiveSavedTournament(context: Context): SavedTournament? {
        val list = getSavedTournaments(context)
        return list.firstOrNull { !it.isCompleted }
    }

    fun deleteTournament(context: Context, id: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val list = getSavedTournaments(context).filter { it.id != id }

        val jsonArray = JSONArray()
        list.forEach { item ->
            val obj = JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("dateStr", item.dateStr)
                put("totalPlayers", item.totalPlayers)
                put("currentRound", item.currentRound)
                put("totalRounds", item.totalRounds)
                put("isCompleted", item.isCompleted)
                put("winnerName", item.winnerName ?: "")

                val namesArr = JSONArray()
                item.playerNames.forEach { namesArr.put(it) }
                put("playerNames", namesArr)

                val scoresObj = JSONObject()
                item.scores.forEach { (k, v) -> scoresObj.put(k.toString(), v) }
                put("scores", scoresObj)

                val gamesArr = JSONArray()
                item.playlistGameNames.forEach { gamesArr.put(it) }
                put("playlistGameNames", gamesArr)
            }
            jsonArray.put(obj)
        }

        prefs.edit().apply {
            putString(KEY_SAVED_LIST, jsonArray.toString())
            if (prefs.getString(KEY_LAST_ACTIVE_ID, "") == id) {
                remove(KEY_LAST_ACTIVE_ID)
            }
            apply()
        }
    }

    fun toTournamentState(saved: SavedTournament): TournamentState {
        val playlist = saved.playlistGameNames.mapNotNull { name ->
            try { GameType.valueOf(name) } catch (_: Exception) { null }
        }.ifEmpty { GameType.getGamesForPlayerCount(saved.totalPlayers).take(5) }

        val state = TournamentState(
            totalPlayers = saved.totalPlayers,
            totalRounds = saved.totalRounds,
            currentRound = saved.currentRound,
            gamePlaylist = playlist,
            id = saved.id
        )

        saved.scores.forEach { (pIdInt, score) ->
            state.scores[PlayerId.fromId(pIdInt)] = score
        }

        return state
    }

    fun toReplayTournamentState(saved: SavedTournament): TournamentState {
        val playlist = saved.playlistGameNames.mapNotNull { name ->
            try { GameType.valueOf(name) } catch (_: Exception) { null }
        }.ifEmpty { GameType.getGamesForPlayerCount(saved.totalPlayers).take(5) }

        return TournamentState(
            totalPlayers = saved.totalPlayers,
            totalRounds = saved.totalRounds,
            currentRound = 1,
            gamePlaylist = playlist,
            id = System.currentTimeMillis().toString()
        )
    }
}

