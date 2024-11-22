package com.example.tinyproblem

import android.util.Log

// Logs message using Log.d
fun logMessage(message: String) {
    Log.d("DEBUG", message)
}

fun isHost(playerName: String?, playersList: List<Player>): Boolean{
    val hostValue = playersList.find { it.playerName == playerName }?.host
    return (hostValue == 1)
}