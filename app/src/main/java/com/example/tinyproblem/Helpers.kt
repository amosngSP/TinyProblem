package com.example.tinyproblem

import android.annotation.SuppressLint
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
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
// "{\"game_action\":\"start\",\"hiding_time\":30,\"seeker_time\":60,\"hiding_players\":5,\"player_type\":\"hider\"}"
@Serializable
data class GamePayload(var game_action: String, var hiding_time: Int, var seeker_time: Int, var hiding_players: Int, var player_type: String)
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

class CustomerArrayAdapter(private val context: Context, private val items: MutableList<ScanResult>): ArrayAdapter<ScanResult>(context, 0, items) {
    @SuppressLint("MissingPermission")
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view: View = convertView ?: LayoutInflater.from(context).inflate(android.R.layout.simple_list_item_1, parent, false)

        val textView = view.findViewById<TextView>(android.R.id.text1)

        val currentItem = items[position]

        textView.text = currentItem.device.name.toString()

        return view
    }
}
