package com.example.tinyproblem

import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.util.Log

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