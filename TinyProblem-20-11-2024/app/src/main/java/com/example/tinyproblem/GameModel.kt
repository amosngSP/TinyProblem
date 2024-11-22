package com.example.tinyproblem

data class GameModel(
    val gameId: String = "",
    val gameStatus: GameStatus = GameStatus.CREATED,
    val hidingPhaseEndTime: Long? = null,
    val hidingTimerDuration: Long? = null,
    val secondTimerEndTime: Long? = null,
    val secondTimerDuration: Long? = null,
    val players: MutableList<GameActivity.Player> = mutableListOf()  // Correct initialization
)

enum class GameStatus{
    CREATED,
    JOINED,
    INPROGRESS,
    FINISHED
}