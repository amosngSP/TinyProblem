package com.example.tinyproblem

data class GameModel(
    val gameId: String = "",
    val gameStatus: GameStatus = GameStatus.CREATED,
    val hidingPhaseEndTime: Long? = null,
    val hidingTimerDuration: Long? = null, // Add this field
    val secondTimerEndTime: Long? = null,
    val secondTimerDuration: Long? = null,
    //val players: List<String> = listOf()
    val players: List<Player> = listOf()
)

enum class GameStatus{
    CREATED,
    JOINED,
    INPROGRESS,
    FINISHED
}

data class Player(
    val playerName: String = "",
    val role: String = "",
    val host: Int = 0
)