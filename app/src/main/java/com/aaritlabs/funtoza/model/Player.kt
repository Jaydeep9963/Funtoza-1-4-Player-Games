package com.aaritlabs.funtoza.model

import android.graphics.Color

enum class PlayerId(
    val id: Int,
    val displayName: String,
    val color: Int,
    val darkColor: Int
) {
    PLAYER_1(1, "Player 1", Color.parseColor("#FF3B30"), Color.parseColor("#B3261E")), // Vivid Red
    PLAYER_2(2, "Player 2", Color.parseColor("#007AFF"), Color.parseColor("#0051A8")), // Electric Blue
    PLAYER_3(3, "Player 3", Color.parseColor("#34C759"), Color.parseColor("#248A3D")), // Neon Green
    PLAYER_4(4, "Player 4", Color.parseColor("#FFCC00"), Color.parseColor("#B28E00")); // Sunny Gold

    companion object {
        fun fromId(id: Int): PlayerId = values().firstOrNull { it.id == id } ?: PLAYER_1
    }
}

data class Player(
    val id: PlayerId,
    var isBot: Boolean = false,
    var score: Int = 0,
    var isAlive: Boolean = true,
    var isTouching: Boolean = false,
    var touchX: Float = 0f,
    var touchY: Float = 0f
)

