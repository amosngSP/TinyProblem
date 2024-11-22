package com.example.tinyproblem

import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import kotlinx.coroutines.delay
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

class BluetoothLeConnection : Service() {
    private val writeCharacteristicUuid: String = "6e400002-b5a3-f393-e0a9-e50e24dcca9e"
    private val readCharacteristicUuid: String = "6e400003-b5a3-f393-e0a9-e50e24dcca9e"
    private val serviceUuid: String = "6e400001-b5a3-f393-e0a9-e50e24dcca9e"

    private var bluetoothManager: BluetoothManager? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var bluetoothDeviceAddress: String? = null
    private var connectionState = STATE_DISCONNECTED
    private var bluetoothServices: List<BluetoothGattService>? = null

    private var bluetoothService: BluetoothGattService? = null

    private var writeQueue: ConcurrentLinkedQueue<ByteArray> = ConcurrentLinkedQueue()

    private val binder = LocalBinder()

    companion object {
        private const val STATE_DISCONNECTED = 0
        private const val STATE_CONNECTING = 1
        private const val STATE_CONNECTED = 2
    }

    private val bluetoothGattCallback = object: BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    // successfully connected to GATT server
                    logMessage("gatt connected")
                    connectionState = STATE_CONNECTED

                    logMessage("attempting to discover services")
                    gatt?.discoverServices()
                }

                BluetoothProfile.STATE_CONNECTING -> {
                    logMessage("gatt connecting")
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    // disconnected from GATT server
                    logMessage("gatt disconnected")
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            logMessage("onServicesDiscovered")

            bluetoothServices = gatt?.services

            logMessage("bluetoothServices: ${bluetoothServices?.size.toString()}")

            val pos = gatt?.services?.indexOfFirst { it.uuid.toString() == serviceUuid }

            if (pos != -1) {
                bluetoothService = gatt?.services?.get(pos!!)
                logMessage("bluetoothService c size: ${bluetoothService?.characteristics?.size.toString()}")
            }

            setNotification()

            super.onServicesDiscovered(gatt, status)
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            logMessage("onCharacteristicRead ${value.decodeToString()}")

            if (value.contentEquals(writeQueue.peek())) {
                logMessage("hmm good")
            } else {
                logMessage("bad")
            }

            super.onCharacteristicRead(gatt, characteristic, value, status)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            logMessage("onCharacteristicChanged ${value.decodeToString()}")
            super.onCharacteristicChanged(gatt, characteristic, value)
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            logMessage("onCharacteristicWrite called!!")

            if (status == BluetoothGatt.GATT_SUCCESS) {
                gatt!!.readCharacteristic(characteristic)
            }

//            bluetoothGatt!!.executeReliableWrite()
//            characteristic.describeContents()

            // for the queue
//            if (writeQueue.peek() != null) {
//                writeNextPayload()
//            }

            super.onCharacteristicWrite(gatt, characteristic, status)
        }

        override fun onReliableWriteCompleted(gatt: BluetoothGatt?, status: Int) {
            logMessage("onReliableWriteCompleted")

            super.onReliableWriteCompleted(gatt, status)
        }
    }

    fun initialize(): Boolean {
        if (bluetoothManager == null) {
            bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            if (bluetoothManager == null) {
                logMessage("Unable to initialize BluetoothManager.")
                return false
            }
        }

        bluetoothAdapter = bluetoothManager!!.adapter
        if (bluetoothAdapter == null) {
            logMessage("Unable to obtain a BluetoothAdapter.")
            return false
        }

        return true
    }

    private fun getCharacteristic(characteristicUuid: String): BluetoothGattCharacteristic? {
        if (bluetoothGatt == null)
            return null

        val pos = bluetoothService!!.characteristics.indexOfFirst { it.uuid.toString() == characteristicUuid }

        if (pos == -1) {
            return null
        } else {
            val characteristic = bluetoothService!!.characteristics[pos]

            return characteristic
        }
    }

    @SuppressLint("MissingPermission")
    private fun setNotification(): Boolean {
        logMessage("enabling notifications")
        val characteristic = getCharacteristic(readCharacteristicUuid)
        bluetoothGatt!!.setCharacteristicNotification(characteristic, true)

        val descriptor = characteristic!!.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
        bluetoothGatt!!.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)

        logMessage("enabled notifications $readCharacteristicUuid")
        return true
    }

    @SuppressLint("NewApi", "MissingPermission")
    fun writeNextPayload() {
        val payload = writeQueue.poll()

        val characteristic = getCharacteristic(writeCharacteristicUuid)!!

        val writeType = when {
            (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0 -> BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0 -> BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            else -> {
                return
            }
        }

        logMessage("writeType: $writeType, writePayload: ${payload!!.decodeToString()}")

        bluetoothGatt!!.writeCharacteristic(
            characteristic,
            payload,
            writeType
        )
    }

    @SuppressLint("NewApi", "MissingPermission")
    fun writePayload(payload: ByteArray): Boolean {
        if (bluetoothGatt == null)
            return false

        // do we need reliableWrite?
//        if (!bluetoothGatt!!.beginReliableWrite()) {
//            logMessage("unable to beginReliableWrite")
//        }

        val pos = bluetoothService!!.characteristics.indexOfFirst { it.uuid.toString() == writeCharacteristicUuid }

        if (pos == -1) {
            return false
        } else {
            if (bluetoothGatt!!.beginReliableWrite()) {
                logMessage("begin reliable write")
            }

            val characteristic = bluetoothService!!.characteristics[pos]

            val writeType = when {
                (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0 -> BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0 -> BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                else -> {
                    return false
                }
            }

            if (payload.size > 20) {
                writeQueue.add("START".toByteArray())

                val chunks = payload.toList().chunked(15)

                for (chunk in chunks) {
                    writeQueue.add(chunk.toByteArray())
                }

                writeQueue.add("END".toByteArray())

                val writePayload = writeQueue.peek()

                logMessage("writePayload - ${writePayload!!.decodeToString()}")

                bluetoothGatt!!.writeCharacteristic(
                    characteristic,
                    writePayload,
                    writeType
                )
            } else {
                writeQueue.add("START".toByteArray())
                writeQueue.add(payload)
                writeQueue.add("END".toByteArray())

                val writePayload = writeQueue.poll()
                bluetoothGatt!!.writeCharacteristic(
                    characteristic,
                    writePayload,
                    writeType
                )
            }

            logMessage("sending payload to characteristic ")

            return true
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(address: String?): Boolean {
        if (bluetoothAdapter == null && address == null) {
            logMessage("mBluetoothAdapter or address is null")
            return false
        }

        if (
            bluetoothDeviceAddress != null &&
            address == bluetoothDeviceAddress &&
            bluetoothGatt != null) {
            return if (bluetoothGatt!!.connect()) {
                connectionState = STATE_CONNECTING
                true
            } else {
                false
            }
        }

        val device = bluetoothAdapter!!.getRemoteDevice(address)

        if (device == null) {
            logMessage("Device not found. Unable to connect.")
            return false
        }

        bluetoothGatt = device.connectGatt(this, false, bluetoothGattCallback)
        logMessage("Trying to create a new connection")
        connectionState = STATE_CONNECTING
        return true
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        if (bluetoothAdapter == null || bluetoothGatt == null) {
            return
        }
        bluetoothGatt!!.disconnect()
    }

    @SuppressLint("MissingPermission")
    fun close() {
        if (bluetoothGatt == null) {
            return
        }

        bluetoothGatt!!.close()
        bluetoothGatt = null
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        return super.onUnbind(intent)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return super.onStartCommand(intent, flags, startId)
    }

    inner class LocalBinder: Binder() {
        fun getService(): BluetoothLeConnection {
            return this@BluetoothLeConnection
        }
    }
}