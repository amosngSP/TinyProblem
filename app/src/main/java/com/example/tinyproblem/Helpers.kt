package com.example.tinyproblem

import android.util.Log

// Logs message using Log.d
fun logMessage(message: String) {
    Log.d("DEBUG", message)
}

fun isHost(playerName: String?, playersList: List<Player>): Boolean{
    return playerName == playersList[0].playerName
}