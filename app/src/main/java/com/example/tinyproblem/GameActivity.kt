package com.example.tinyproblem

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.tinyproblem.databinding.ActivityGameBinding
import com.google.firebase.firestore.FirebaseFirestore
import androidx.recyclerview.widget.GridLayoutManager
import android.os.CountDownTimer
import android.os.IBinder
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.math.BigDecimal
import kotlin.math.floor

class GameActivity : AppCompatActivity(), NotificationListener {

    private lateinit var binding: ActivityGameBinding
    private var countDownTimer: CountDownTimer? = null // Declare timer here

    private var gameModel: GameModel? = null
    private val firestore = FirebaseFirestore.getInstance() // Firestore instance
    private val playersList = mutableListOf<Player>() // List of players in the lobby
    private var playerName: String? = null
    private var gameId: String? = null // To hold the game ID
    private var secondTimerDuration: Long? = null // Duration for the second timer
    private var secondCountDownTimer: CountDownTimer? = null
    private var start_signal: Boolean = false

    private var playersCaught = 0;

    private var bluetoothLeConnection: BluetoothLeConnection? = null

    private val serviceConnection: ServiceConnection = object: ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            bluetoothLeConnection = (service as BluetoothLeConnection.LocalBinder).getService()
            val status = bluetoothLeConnection?.initialize()!!
            if (status) {
                bluetoothLeConnection?.setNotificationListener(this@GameActivity)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bluetoothLeConnection = null
        }
    }

    override fun onNotificationReceived(message: String) {
        logMessage("in GameActivity thread $message")

        val playerPos = playersList.indexOfFirst { it.playerName == playerName }
        playersList[playerPos].found = true

        gameId?.let { id ->
            firestore.collection("games").document(id)
                .update("players", playersList)
                .addOnSuccessListener {
                    logMessage("onNotificationReceived updated playersList")
                }
                .addOnFailureListener {
                    logMessage("onNotificationReceived error updating playersList")
                }
        }
    }

    private fun notifyPlayerCaught() {
        logMessage("notifying player caught")
        val caughtPayload = "{\"game_action\": \"caught\"}"
        bluetoothLeConnection?.writePayload(caughtPayload.toByteArray())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize binding
        binding = ActivityGameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set up other functionality here...
        binding.setTimerBtn.setOnClickListener {
            if (playersList.isNotEmpty() && isHost(playerName,playersList)) {
                val timerInput = binding.timerInput.text.toString()
                if (timerInput.isNotEmpty()) {
                    val timerDuration = timerInput.toLongOrNull()
                    if (timerDuration != null && timerDuration > 0) {
                        // Save the timer duration instead of the end time
                        gameId?.let { id ->
                            firestore.collection("games").document(id)
                                .update("hidingTimerDuration", timerDuration * 1000) // Save in milliseconds
                                .addOnSuccessListener {
                                    Log.d("GameActivity", "Timer duration saved: $timerDuration seconds.")
                                    Toast.makeText(this, "Timer set for $timerDuration seconds", Toast.LENGTH_SHORT).show()
                                }
                                .addOnFailureListener { e ->
                                    Log.e("GameActivity", "Error setting timer: ${e.message}")
                                    Toast.makeText(this, "Failed to set timer", Toast.LENGTH_SHORT).show()
                                }
                        }
                    } else {
                        Toast.makeText(this, "Enter a valid duration in seconds", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Timer duration cannot be empty", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Only the host can set the timer!", Toast.LENGTH_SHORT).show()
            }
        }

        binding.setSecondTimerBtn.setOnClickListener {
            if (playersList.isNotEmpty() && isHost(playerName,playersList)) { // Check if host
                val secondTimerInput = binding.secondTimerInput.text.toString()
                if (secondTimerInput.isNotEmpty()) {
                    val duration = secondTimerInput.toLongOrNull()
                    if (duration != null && duration > 0) {
                        gameId?.let { id ->
                            firestore.collection("games").document(id)
                                .update("secondTimerDuration", duration * 1000) // Save duration in milliseconds
                                .addOnSuccessListener {
                                    Log.d("GameActivity", "Second timer set: $duration seconds.")
                                    Toast.makeText(this, "Second timer set for $duration seconds", Toast.LENGTH_SHORT).show()
                                }
                                .addOnFailureListener { e ->
                                    Log.e("GameActivity", "Error setting second timer: ${e.message}")
                                    Toast.makeText(this, "Failed to set second timer", Toast.LENGTH_SHORT).show()
                                }
                        }
                    } else {
                        Toast.makeText(this, "Enter a valid duration in seconds", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Second timer duration cannot be empty", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Only the host can set the second timer!", Toast.LENGTH_SHORT).show()
            }
        }

        // Set the click listener on the button for hider and seeker
        binding.setHiderSeekerBtn.setOnClickListener {
            if (playersList.isNotEmpty() && isHost(playerName,playersList)) { // Ensure only the host can assign roles
                val hiderText = binding.hiderInput.text.toString()
                val seekerText = binding.seekerInput.text.toString()

                if (hiderText.isNotEmpty() && seekerText.isNotEmpty()) {
                    val hiderCount = hiderText.toIntOrNull()
                    val seekerCount = seekerText.toIntOrNull()

                    if (hiderCount == null || seekerCount == null || hiderCount + seekerCount != playersList.size) {
                        Toast.makeText(
                            this,
                            "The total of Hiders and Seekers must equal the number of players (${playersList.size}).",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@setOnClickListener
                    }

                    // Randomize roles
                    val shuffledPlayers = playersList.shuffled()
                    val hiders = shuffledPlayers.take(hiderCount)
                    val seekers = shuffledPlayers.drop(hiderCount)

                    // Map roles to players
                    val updatedPlayers = playersList.map { playerName ->
                        Player(
                            playerName = playerName.playerName,
                            role = if (hiders.contains(playerName)) "hider" else "seeker",
                            host = playerName.host
                        )
                    }


                    // Update Firestore
                    gameModel?.let { model ->
                        val updatedGameModel = model.copy(players = updatedPlayers, hiders = hiderCount)
                        firestore.collection("games").document(gameId!!)
                            .set(updatedGameModel)
                            .addOnSuccessListener {
                                Toast.makeText(this, "Roles assigned successfully!", Toast.LENGTH_SHORT).show()
                                gameModel = updatedGameModel
                                updatePlayerListUI()
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(this, "Failed to assign roles: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                    }
                } else {
                    Toast.makeText(this, "Please enter valid numbers for Hiders and Seekers.", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Only the host can assign roles.", Toast.LENGTH_SHORT).show()
            }
        }

        // Retrieve the gameId from the Intent passed from MainActivity
        gameId = intent.getStringExtra("gameId")
        playerName = intent.getStringExtra("keyName")

        // Fetch the game data based on the gameId
        gameId?.let {
            GameData.fetchGameModel(it)  // Pass the gameId to fetch the correct game data
        }

        // Initialize RecyclerView with GridLayoutManager for 2 columns
        binding.playerListRecyclerView.layoutManager = GridLayoutManager(this, 2)


        binding.playerListRecyclerView.adapter = PlayerAdapter(playersList)

        // Observe game model changes and update the UI accordingly
        GameData.gameModel.observe(this) { gameModel ->
            this.gameModel = gameModel
            setUI()
            listenForPlayers(gameId)  // Update players list when the lobby changes
            listenForTimers()         // Synchronize timer across all clients
        }

        binding.startGameBtn.setOnClickListener {
            if (playersList.size > 1) {
                if (isHost(playerName,playersList)) {
                    startGameForHost(gameId!!)
                } else {
                    Toast.makeText(this, "Only the host can start the game!", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "There must be more than one player to start the game.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.quitGame.setOnClickListener {
            quitGame()
        }

        startBluetoothService(serviceConnection)
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
        secondCountDownTimer?.cancel()
        bluetoothLeConnection?.close()
    }

    fun startGameForHost(gameId: String) {
        gameModel?.apply {
            if (gameStatus == GameStatus.CREATED || gameStatus == GameStatus.JOINED) {
                // Validate Hiders and Seekers input
                val hiderInput = binding.hiderInput.text.toString()
                val seekerInput = binding.seekerInput.text.toString()

                val hiderCount = hiderInput.toIntOrNull()
                val seekerCount = seekerInput.toIntOrNull()

                if (hiderInput.isEmpty() || seekerInput.isEmpty() || hiderCount == null || seekerCount == null) {
                    Toast.makeText(
                        this@GameActivity,
                        "Please provide valid numbers for Hiders and Seekers.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return
                }

                if (hiderCount + seekerCount != playersList.size) {
                    Toast.makeText(
                        this@GameActivity,
                        "The total of Hiders and Seekers must equal the number of players (${playersList.size}).",
                        Toast.LENGTH_SHORT
                    ).show()
                    return
                }

                // Proceed with starting the game
                val currentTime = System.currentTimeMillis()
                val hidingTimerDuration = this.hidingTimerDuration ?: 60000 // Default to 60 seconds
                val secondTimerDuration = this.secondTimerDuration ?: 60000 // Default to 60 seconds

                // Calculate end times using epoch time
                val hidingPhaseEndTime = currentTime + hidingTimerDuration
                val secondTimerEndTime = hidingPhaseEndTime + secondTimerDuration

                // Update game model
                val updatedGameModel = this.copy(
                    gameStatus = GameStatus.INPROGRESS,
                    hidingPhaseEndTime = hidingPhaseEndTime,
                    secondTimerEndTime = secondTimerEndTime
                )

                listenForCaughtHiders(gameId)

                // Save to Firestore
                firestore.collection("games").document(gameId)
                    .set(updatedGameModel)
                    .addOnSuccessListener {
                        Log.d("GameActivity", "Game started with synchronized timers.")
                        Toast.makeText(this@GameActivity, "Game started!", Toast.LENGTH_SHORT).show()

                        // Update local gameModel and UI for the host
                        this@GameActivity.gameModel = updatedGameModel
                        setUI() // Update the UI after the game state changes
                    }
                    .addOnFailureListener { e ->
                        Log.e("GameActivity", "Error starting game: ${e.message}")
                        Toast.makeText(this@GameActivity, "Failed to start game.", Toast.LENGTH_SHORT).show()
                    }
            } else {
                Toast.makeText(this@GameActivity, "Game cannot be started in the current state.", Toast.LENGTH_SHORT).show()
            }
        } ?: run {
            Toast.makeText(this, "Game data is not available.", Toast.LENGTH_SHORT).show()
        }
    }



    // Set UI for displaying the game state and lobby details
    fun setUI() {
        gameModel?.apply {
            // Check if the current player is the host
            val isHost = playersList.isNotEmpty() && isHost(playerName,playersList)
            val isGameInProgress = gameStatus == GameStatus.INPROGRESS
            val showTimerControls = gameStatus in listOf(GameStatus.CREATED, GameStatus.JOINED)

            // Show or hide elements based on the game status and whether the player is the host
            binding.startGameBtn.visibility = if (showTimerControls && isHost) View.VISIBLE else View.GONE
            binding.setTimerBtn.visibility = if (showTimerControls && isHost) View.VISIBLE else View.GONE
            binding.timerInput.visibility = if (showTimerControls && isHost) View.VISIBLE else View.GONE
            binding.setSecondTimerBtn.visibility = if (showTimerControls && isHost) View.VISIBLE else View.GONE
            binding.secondTimerInput.visibility = if (showTimerControls && isHost) View.VISIBLE else View.GONE
            binding.setHiderSeekerBtn.visibility = if (showTimerControls && isHost) View.VISIBLE else View.GONE
            binding.hiderInput.visibility = if (showTimerControls && isHost) View.VISIBLE else View.GONE
            binding.seekerInput.visibility = if (showTimerControls && isHost) View.VISIBLE else View.GONE

            // Update the timer text
            binding.timerText.text = when (gameStatus) {
                GameStatus.CREATED, GameStatus.JOINED -> "Waiting to start..."
                GameStatus.INPROGRESS -> {
                    val remainingTime = hidingPhaseEndTime?.let { (it - System.currentTimeMillis()) / 1000 }
                    if (remainingTime != null && remainingTime > 0) {
                        "Hiding time left: $remainingTime seconds"
                    } else {
                        "Hiding phase over! Seeking begins."
                    }
                }
                GameStatus.FINISHED -> "Game Over"
                else -> ""
            }

            // Update the game status text
            binding.gameStatusText.text = when (gameStatus) {
                GameStatus.CREATED -> "Lobby Code: $gameId\nPlayers: ${playersList.size}/6\nWaiting to start..."
                GameStatus.JOINED -> "Lobby Code: $gameId\nWaiting for players (${playersList.size}/6)..."
                GameStatus.INPROGRESS -> "Game in progress (${playersList.size}/6)"
                GameStatus.FINISHED -> "Game Over"
            }
        }
    }

    private fun setLobbyTimer(timerDuration: Long) {
        gameId?.let { id ->
            firestore.collection("games").document(id)
                .update("hidingPhaseEndTime", System.currentTimeMillis() + timerDuration * 1000)
                .addOnSuccessListener {
                    Log.d("GameActivity", "Timer set for $timerDuration seconds.")
                }
                .addOnFailureListener { Log.e("GameActivity", "Failed to set timer: ${it.message}") }
        }
    }

    fun startGame() {
        gameModel?.apply {
            // Update the game status to INPROGRESS
            val updatedGameModel = this.copy(
                gameStatus = GameStatus.INPROGRESS
            )
            updateGameData(updatedGameModel)
        }
    }

    // Quit the game by removing the player from the list and updating the game status
    fun quitGame() {
        gameId?.let {
            firestore.collection("games").document(it)
                .get()
                .addOnSuccessListener { documentSnapshot ->
                    val gameModel = documentSnapshot.toObject(GameModel::class.java)
                    gameModel?.apply {
                        // Remove the player from the game
                        val updatedPlayersList = playersList.toMutableList().apply {
                            removeIf { it.playerName == playerName } // Remove the Player object where playerName matches
                        }
                        val updatedGameModel = this.copy(players = updatedPlayersList)

                        // Save the updated game model (after removing the player)
                        updateGameData(updatedGameModel)

                        // Optionally: If no players are left, delete the game
                        if (updatedPlayersList.isEmpty()) {
                            FirebaseFirestore.getInstance().collection("games")
                                .document(gameId)
                                .delete()
                                .addOnSuccessListener {
                                    Log.d("GameActivity", "Game deleted as no players are left.")
                                    // Optionally show a message to the user
                                    Toast.makeText(this@GameActivity, "Game is deleted as all players left.", Toast.LENGTH_SHORT).show()
                                }
                                .addOnFailureListener { error ->
                                    Log.e("GameActivity", "Error deleting game: ${error.message}")
                                }
                        } else {
                            // If there are still players, update the game data without deleting the game
                            updateGameData(updatedGameModel)
                        }

                        // Close the activity (optional, depends on your design)
                        finish()  // This will close the activity and return to the previous screen
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e("GameActivity", "Error fetching game data: ${exception.message}")
                    Toast.makeText(this, "Error quitting the game", Toast.LENGTH_SHORT).show()
                }
        }
    }

    // Save updated game data to Firebase
    fun updateGameData(model : GameModel){
        GameData.saveGameModel(model)
    }

    private fun listenForPlayers(gameId: String?) {
        if (gameId.isNullOrEmpty()) return

        firestore.collection("games").document(gameId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("GameActivity", "Error listening for players: ${error.message}")
                    return@addSnapshotListener
                }

                snapshot?.toObject(GameModel::class.java)?.let { updatedGameModel ->
                    playersList.clear()
                    playersList.addAll(updatedGameModel.players) // Update with latest players and roles
                    gameModel = updatedGameModel
                    updatePlayerListUI()
                }
            }
    }

    private fun listenForCaughtHiders(gameId: String?) {
        if (gameId.isNullOrEmpty()) return

        firestore.collection("games").document(gameId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("GameActivity", "Error listening for caught hiders: ${error.message}")
                    return@addSnapshotListener
                }

                val model = snapshot?.toObject(GameModel::class.java)
                if (model != null) {
                    this.gameModel = model
                    val updatedPlayers = model.players
                    if (updatedPlayers.isNotEmpty()) {
                        for (player in updatedPlayers) {
                            val localPlayer = playersList.find { it.playerName == player.playerName }
                            if (localPlayer != null && localPlayer.found != player.found) {
                                notifyPlayerCaught()
                            }
                        }
                        playersList.clear()
                        playersList.addAll(updatedPlayers)
                        updatePlayerListUI()
                    }
                }
            }
    }

    private fun listenForTimers() {
        gameId?.let { id ->
            firestore.collection("games").document(id)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e("GameActivity", "Error listening for timers: ${error.message}")
                        return@addSnapshotListener
                    }

                    // Ensure snapshot is not null
                    val model = snapshot?.toObject(GameModel::class.java)
                    if (model != null) {
                        this.gameModel = model // Update local gameModel for all players, including the host
                        setUI() // Refresh the UI based on the game status

                        val currentTime = System.currentTimeMillis()

                        // Update secondTimerDuration
                        secondTimerDuration = model.secondTimerEndTime ?: 60000 // Default to 60 seconds if null

                        if (model.gameStatus == GameStatus.INPROGRESS) {
                            //send the start signal
                            if(!start_signal)
                            {
                                val noOfHiders = model.hiders;
                                val playerList = model.players;
                                val hidingDuration = if (model.hidingPhaseEndTime != null) model.hidingPhaseEndTime / 1000 else 0
                                val seekingDuration = if (model.secondTimerEndTime != null) model.secondTimerEndTime / 1000 else 0
                                val currentEpochTimeInSeconds = currentEpochTime()
                                val role = playersList.find { it.playerName == playerName }?.role ?: "hider"
                                val gPayload = GamePayload(
                                    "start",
                                    currentEpochTimeInSeconds,
                                    hidingDuration,
                                    seekingDuration,
                                    noOfHiders,
                                    role
                                )

                                val json = Json {ignoreUnknownKeys = true}
                                val payloadStr = json.encodeToString(gPayload)

                                bluetoothLeConnection?.writePayload(payloadStr.toByteArray())

                                start_signal=true
                            }

                            val hidingTimeRemaining = model.hidingPhaseEndTime?.minus(currentTime) ?: 0
                            val seekingTimeRemaining = model.secondTimerEndTime?.minus(currentTime) ?: 0

                            if (hidingTimeRemaining > 0) {
                                startHidingTimer(hidingTimeRemaining)
                            } else if (seekingTimeRemaining > 0) {
                                startSeekingTimer(seekingTimeRemaining)
                            } else {
                                // Game over
                                binding.timerText.text = "Time's up! Hiders won!"
                                countDownTimer?.cancel()
                                secondCountDownTimer?.cancel()
                            }


                            // gemini said no
//                            // Start the first timer (hiding phase)
//                            val hidingTimeRemaining = model.hidingPhaseEndTime
//                            if (hidingTimeRemaining != null && hidingTimeRemaining > 0) {
//                                startCountdownTimer(hidingTimeRemaining)
//                            } else {
//                                // If the hiding phase is over, start the seeking timer
//                                val seekingTimeRemaining = model.secondTimerEndTime
//                                if (seekingTimeRemaining != null && seekingTimeRemaining > 0) {
//                                    startSecondCountdownTimer(seekingTimeRemaining)
//                                } else {
//                                    // Both timers are finished
//                                    binding.timerText.text = "Time's up! Hiders won!"
//                                    countDownTimer?.cancel()
//                                    secondCountDownTimer?.cancel()
//                                }
//                            }
                        } else {
                            // If the game is not in progress, reset the UI and stop timers
                            binding.timerText.text = "Waiting to start..."
                            countDownTimer?.cancel()
                            secondCountDownTimer?.cancel()
                        }
                    }
                }
        }
    }

    private fun startHidingTimer(durationInMillis: Long) {
        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(durationInMillis, 1000) {
            override fun onTick(millisUntilFinished:
                                Long) {
                val seconds = millisUntilFinished / 1000
                val minutes = seconds / 60
                val remainingSeconds = seconds % 60
                binding.timerText.text = "Hiding time left: $minutes mins $remainingSeconds secs"
            }

            override fun onFinish() {
                binding.timerText.text = "Hiding phase over! Seeking begins."
                // You might want to trigger a function here to start the seeking phase
                // or handle any logic related to the transition between phases.
            }
        }.start()
    }

    private fun startSeekingTimer(durationInMillis: Long) {
        secondCountDownTimer?.cancel()
        secondCountDownTimer = object : CountDownTimer(durationInMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = millisUntilFinished / 1000
                val minutes = seconds / 60
                val remainingSeconds = seconds % 60
                binding.timerText.text = "Seeking time left: $minutes mins $remainingSeconds secs"
            }

            override fun onFinish() {
                binding.timerText.text = "Time's up! Hiders won!"
            }
        }.start()
    }

    private fun startCountdownTimer(i: Long) {
        countDownTimer?.cancel() // Cancel any existing timer
        countDownTimer = object : CountDownTimer((currentEpochTime() - i) + 1, 1000) {
            override fun onTick(millisUntilFinished: Long) {
//                val currentMillis = currentEpochTime()
//                val seconds = (i/1000) - currentMillis

//                // If time is up, stop the timer
//                if (seconds <= 0) {
//                    finish()
//                    cancel()
//                }

                val seconds = millisUntilFinished / 1000
                val minutes = seconds / 60
                val remainingSeconds = seconds % 60

                binding.timerText.text = "Hiding time left: $minutes mins $remainingSeconds secs"
            }

            override fun onFinish() {
                binding.timerText.text = "Hiding phase over! Starting second timer..."
                // Start the second timer
                val safeSecondDuration = secondTimerDuration ?: 60000 // Default to 60 seconds
                startSecondCountdownTimer(safeSecondDuration)
            }
        }.start()
    }

    private fun startSecondCountdownTimer(i: Long) {
        countDownTimer?.cancel() // Cancel any existing timer
        countDownTimer = object : CountDownTimer(Long.MAX_VALUE, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val currentMillis = currentEpochTime()
                val seconds = (i/1000) - currentMillis

                // If time is up, stop the timer
                if (seconds <= 0) {
                    onFinish()
                    cancel()
                }
                val minutes = seconds / 60
                val remainingSeconds = seconds % 60

                binding.timerText.text = "Seeking time left: $minutes mins $remainingSeconds secs"
            }

            override fun onFinish() {
                binding.timerText.text = "Time's up! Hiders won!"
            }
        }
        secondCountDownTimer?.start()
    }

    private fun updatePlayerListUI() {
        Log.d("GameActivity", "Updating player list: $playersList")
        if (binding.playerListRecyclerView.adapter == null) {
            binding.playerListRecyclerView.adapter = PlayerAdapter(playersList.toMutableList())

        } else {
            (binding.playerListRecyclerView.adapter as PlayerAdapter).apply {
                updatePlayers(playersList.map { player -> Player(player.playerName, player.role, player.host) })
            }
        }
    }

    fun finishGame() {
        gameModel?.apply {
            // Update game status to FINISHED
            val updatedGameModel = this.copy(
                gameStatus = GameStatus.FINISHED
            )

            // Save the updated game status
            updateGameData(updatedGameModel)

            // Delete the game from Firestore after the game ends
            FirebaseFirestore.getInstance().collection("games")
                .document(gameId)
                .delete()
                .addOnSuccessListener {
                    // Optionally, handle success, e.g., show a message
                    Log.d("GameData", "Game data deleted successfully")
                }
                .addOnFailureListener { error ->
                    // Optionally, handle failure
                    Log.e("GameData", "Error deleting game data: ${error.message}")
                }
        }
    }
}