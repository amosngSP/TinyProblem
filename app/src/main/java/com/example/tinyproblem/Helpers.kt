package com.example.tinyproblem

import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.util.Log
import kotlinx.serialization.Serializable

// Logs message using Log.d
fun logMessage(message: String) {
    Log.d("DEBUG", message)
}

fun isHost(playerName: String?, playersList: List<Player>): Boolean{

    return playerName == playersList[0].playerName
}

fun Context.startBluetoothService(serviceConnection: ServiceConnection) {
    val bleServiceIntent = Intent(this, BluetoothLeConnection::class.java)

    // connect to device
    if (bindService(bleServiceIntent, serviceConnection, Context.BIND_AUTO_CREATE)) {
        logMessage("service is bounded")
    } else {
        logMessage("something went wrong")
    }
}

fun currentEpochTime(): Long{
    return System.currentTimeMillis() / 1000
}
// "{\"game_action\":\"start\",\"hiding_time\":30,\"seeker_time\":60,\"hiding_players\":5,\"player_type\":\"hider\"}"

@Serializable
data class GamePayload(
    var game_action: String,
    var current_time: Long = 0,
    var hiding_time: Long = 0,
    var seeker_time: Long = 0,
    var hiding_players: Int,
    var player_type: String)

interface NotificationListener {
    fun onNotificationReceived(message: String)
}