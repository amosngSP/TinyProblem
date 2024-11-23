package com.example.tinyproblem

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.tinyproblem.databinding.ActivityMainBluetoothBinding
import com.google.android.material.snackbar.Snackbar

@SuppressLint("MissingPermission")
class MainBluetooth : AppCompatActivity() {
    // BLE scan results
    private val scanResults = mutableListOf<ScanResult>()

    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var bluetoothLeScanner: BluetoothLeScanner

    private val scanSettings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .build()

    private lateinit var binding: ActivityMainBluetoothBinding

    private var isScanning = false
        set(value) {
            field = value
            runOnUiThread {binding.scanButton.text = if (value) "Stop Scan" else "Start Scan"}
        }

    private val bluetoothEnablingResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            logMessage("test")
        } else {
            promptEnableBluetooth()
        }
    }

    private lateinit var arrayAdapter: ArrayAdapter<*>

    private var bluetoothLeConnection: BluetoothLeConnection? = null

    private val serviceConnection: ServiceConnection = object: ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            bluetoothLeConnection = (service as BluetoothLeConnection.LocalBinder).getService()
            bluetoothLeConnection?.initialize()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bluetoothLeConnection = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // android's new viewbinding
        binding = ActivityMainBluetoothBinding.inflate(layoutInflater)

        enableEdgeToEdge()

        // the view
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        startBluetoothService(serviceConnection)

        // get required bluetooth manager, adapter, and BLE scanner.
        bluetoothManager = this.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner

        // scan button
        binding.scanButton.setOnClickListener{
            if (isScanning) stopBLEScan() else startBLEScan()
        }

        // listview show list of bluetooth devices
        arrayAdapter = CustomerArrayAdapter(this, scanResults)
        binding.deviceListView.adapter = arrayAdapter
        arrayAdapter.setNotifyOnChange(true)

        binding.deviceListView.setOnItemClickListener { parent, view, position, id ->
            // when user clicks on device
            stopBLEScan()

            val connected = bluetoothLeConnection!!.connect(scanResults[position].device.address.toString())

            if (connected) {
                logMessage("device connected")
                Intent(this, NameActivity::class.java).also {
                    startActivity(it)
                }
            }
        }

    }

    override fun onResume() {
        super.onResume()
        if (!bluetoothAdapter.isEnabled) {
            promptEnableBluetooth()
        }

        if (bluetoothLeConnection != null) {
            startBluetoothService(serviceConnection)
        }
    }

    private fun startBLEScan() {
        if (!hasRequiredBluetoothPermissions()) {
            requestBluetoothPermissions()
        }

        if (!hasRequiredBluetoothPermissions()) {
//            Snackbar.make(binding.root, "Failed to request for BLE", Snackbar.LENGTH_SHORT).show()
            return
        }

        isScanning = true
        scanResults.clear()
        arrayAdapter.notifyDataSetChanged()
        bluetoothLeScanner.startScan(null, scanSettings, scanCallback)
    }

    private fun stopBLEScan() {
        if (!hasRequiredBluetoothPermissions()) {
            return
        }

        isScanning = false
        bluetoothLeScanner.stopScan(scanCallback)
    }

    private fun promptEnableBluetooth() {
        if (hasRequiredBluetoothPermissions() && !bluetoothAdapter.isEnabled) {
            Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE).apply {
                bluetoothEnablingResult.launch(this)
            }
        }
    }

    private val scanCallback = object: ScanCallback() {
        @SuppressLint("MissingPermission") // checks are already in place. stop being annoying android studio
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)

            // here we check the device's name from the advertisement packet.
            // you could also check it's manufacturer, address, services, etc.

            val device = result!!.device

            with(device) {
                // filter results with hard coded TinyCircuit manufacturer
                // or custom address coded in
                val address = this!!.address

                if (address.endsWith("49:43:54"))
                {
                    logMessage("New device found, address: $address, name: $name")

                    val position = scanResults.indexOfFirst { it.device.address == address }

                    if (position != -1) {
                        scanResults[position] = result
                    } else {
                        scanResults.add(result)
                    }

                    arrayAdapter.notifyDataSetChanged()
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            logMessage("Scan failed")
        }
    }
}