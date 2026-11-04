package com.aaritlabs.funtoza.model

enum class GameType(
    val title: String,
    val description: String,
    val iconEmoji: String,
    val primaryColorHex: String,
    val maxPlayers: Int = 4
) {
    TANK_BATTLE(
        "Tank Battle",
        "Ricochet shells, maneuver tanks, blow up opponents!",
        "🚀",
        "#FF3B30",
        4
    ),
    FINGER_SOCCER(
        "Finger Soccer",
        "Fast-paced hockey arcade with responsive paddles and explosive goals!",
        "⚽",
        "#007AFF",
        4
    ),
    NEON_SNAKE(
        "Neon Snake",
        "Tron-style light trails! Trap opponents and don't hit walls!",
        "🐍",
        "#34C759",
        4
    ),
    SNAKES_AND_LADDERS(
        "Snakes & Ladders",
        "Classic board duel! Climb ladders, dodge snakes, and race to 100!",
        "🪜",
        "#FFCC00",
        4
    ),
    TARGET_BLITZ(
        "Target Blitz",
        "Throw daggers into the spinning timber! Avoid colliding knives!",
        "🎯",
        "#FF2A6D",
        4
    ),
    TIC_TAC_TOE(
        "Tic Tac Toe",
        "Neon cyber duel! Line up 3 in a row to defeat your opponent!",
        "❌",
        "#00E5FF",
        2
    );

    companion object {
        fun getAllGames(): List<GameType> = values().toList()
        fun getGamesForPlayerCount(count: Int): List<GameType> = values().filter { count <= it.maxPlayers }
    }
}
