package com.aaritlabs.funtoza.model

enum class GameType(
    val title: String,
    val description: String,
    val iconEmoji: String,
    val primaryColorHex: String
) {
    TANK_BATTLE(
        "Tank Battle",
        "Ricochet shells, maneuver tanks, blow up opponents!",
        "🚀",
        "#FF3B30"
    ),
    FINGER_SOCCER(
        "Finger Soccer",
        "Fast-paced hockey arcade with responsive paddles and explosive goals!",
        "⚽",
        "#007AFF"
    ),
    NEON_SNAKE(
        "Neon Snake",
        "Tron-style light trails! Trap opponents and don't hit walls!",
        "🐍",
        "#34C759"
    ),
    SUMO_BUMPER(
        "Sumo Bumper",
        "High-energy bumper arena! Knock enemies off the floating ring!",
        "💥",
        "#FFCC00"
    ),
    TARGET_BLITZ(
        "Target Blitz",
        "Throw daggers into the spinning timber! Avoid colliding knives!",
        "🎯",
        "#FF2A6D"
    );

    companion object {
        fun getAllGames(): List<GameType> = values().toList()
    }
}

