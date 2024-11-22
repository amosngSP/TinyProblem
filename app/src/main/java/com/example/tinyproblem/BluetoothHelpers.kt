package com.example.tinyproblem

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import com.google.android.material.snackbar.Snackbar

@Synchronized
fun getServiceCharacteristic(bluetoothGattService: BluetoothGattService, uuid: String): Result<BluetoothGattCharacteristic> {
    val writeCharacteristicIndex = bluetoothGattService.characteristics.indexOfFirst {it.uuid.toString() == uuid}

    // find "6e400003-b5a3-f393-e0a9-e50e24dcca9e" write no response
    // find "6e400002-b5a3-f393-e0a9-e50e24dcca9e"

    if (writeCharacteristicIndex == -1) {
        logMessage("characteristic $uuid not found")
        return Result.failure(NoSuchElementException("Test"))
    } else {
        val characteristic: BluetoothGattCharacteristic = bluetoothGattService.characteristics[writeCharacteristicIndex]
        logMessage("found characteristic: ${characteristic.uuid}")
        return Result.success(characteristic)
    }
}

@SuppressLint("NewApi", "MissingPermission")
private fun sendPayloadToCharacteristic(
    bluetoothGatt: BluetoothGatt,
    characteristic: BluetoothGattCharacteristic,
    payload: ByteArray
) {
    val writeType = when {
        (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0 -> BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0 -> BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        else -> {
            return
        }
    }

    bluetoothGatt.writeCharacteristic(
        characteristic,
        payload,
        writeType
    )
}

fun Context.hasRequiredBluetoothPermissions(): Boolean {
    return (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) && (checkSelfPermission(
        Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED)
}

fun Activity.requestBluetoothPermissions() {
    ActivityCompat.requestPermissions(
        this,
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        ),
        1
    )
}