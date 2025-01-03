package com.example.tinyproblem

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.tinyproblem.databinding.ActivityGameBinding
import com.google.firebase.firestore.FirebaseFirestore
import androidx.recyclerview.widget.GridLayoutManager
import android.os.CountDownTimer
import androidx.recyclerview.widget.LinearLayoutManager


class GameActivity : AppCompatActivity() {

    lateinit var binding: ActivityGameBinding
    private var countDownTimer: CountDownTimer? = null // Declare timer here

    private var gameModel: GameModel? = null
    private val firestore = FirebaseFirestore.getInstance() // Firestore instance
   //private val playersList = mutableListOf<Player>() // List of players in the lobby
    private var playerName: String? = null
    private var gameId: String? = null // To hold the game ID
    private var secondTimerDuration: Long? = null // Duration for the second timer
    private var secondCountDownTimer: CountDownTimer? = null
    val playersList: MutableList<Player> = mutableListOf()
    // Assuming it.players is a list of player names
    //val playerNames: List<String> = it.players
    //val playerObjects: List<Player> = playerNames.map { playerName -> Player(playerName, "someRole") }

    data class Player(
        val playerName: String,
        val role: String)

    // Ensure assignedPlayers is a list of Player objects
    val assignedPlayers: MutableList<Player> = mutableListOf(
        Player("Alice", "Hider"),
        Player("Bob", "Seeker"),
        Player("Charlie", "Hider")
    )



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize binding
        binding = ActivityGameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set up other functionality here...
        binding.setTimerBtn.setOnClickListener {
            if (playersList.isNotEmpty() && playersList[0].playerName == playerName) {
                val timerInput = binding.timerInput.text.toString()
                // Your code here
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
            if (playersList.isNotEmpty() && playersList[0].playerName == playerName) { // Check if host
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
            // Get the text from the EditTexts
            val hiderNumber = binding.hiderInput.text.toString().toIntOrNull()
            val seekerNumber = binding.seekerInput.text.toString().toIntOrNull()

            // Check if the numbers are valid
            if (hiderNumber != null && seekerNumber != null && hiderNumber + seekerNumber == playersList.size) {
                // Shuffle the players list to randomize assignment
                val shuffledPlayers = playersList.shuffled()

                // Assign the first hiderNumber players as hiders
                val hiders = shuffledPlayers.take(hiderNumber)

                // Assign the remaining players as seekers
                val seekers = shuffledPlayers.drop(hiderNumber).take(seekerNumber)

                // Create a combined list of players with roles
                // Add hiders to the list
                // Add seekers to the list
                val assignedPlayers = mutableListOf<Player>()
                assignedPlayers.addAll(hiders)
                assignedPlayers.addAll(seekers)



                // Initialize the RecyclerView with the player data
                val playerAdapter = PlayerAdapter(assignedPlayers)
                binding.playersRecyclerView.layoutManager = LinearLayoutManager(this)
                binding.playersRecyclerView.adapter = playerAdapter

                // Optionally show a toast or update UI as needed
                Toast.makeText(this, "Game Started!", Toast.LENGTH_SHORT).show()
            } else {
                // If input validation fails
                Toast.makeText(this, "Invalid number of hiders and seekers", Toast.LENGTH_SHORT).show()
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
                if (playersList.isNotEmpty() && playersList[0].playerName == playerName) { // Check if host
                    startGameForHost(gameId!!)
                }
                else {
                    Toast.makeText(this, "Only the host can start the game!", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "There must be more than one player to start the game.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.quitGame.setOnClickListener {
            quitGame()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
        secondCountDownTimer?.cancel()
    }

    fun startGameForHost(gameId: String) {
        gameModel?.apply {
            if (gameStatus == GameStatus.CREATED || gameStatus == GameStatus.JOINED) {
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
            val isHost = playersList.isNotEmpty() && playersList[0].playerName == playerName
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
                            removeAll { it.playerName == playerName }  // Remove the current player from the list based on the name
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
    fun updateGameData(gameModel: GameModel){
        GameData.saveGameModel(gameModel)
    }

    private fun listenForPlayers(gameId: String?) {
        if (gameId.isNullOrEmpty()) return

        firestore.collection("games").document(gameId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(
                        this,
                        "Error fetching players: ${error.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@addSnapshotListener
                }

                snapshot?.let {
                    val model = it.toObject(GameModel::class.java)

                    model?.let {
                        // Prevent players from joining if the game is in progress
                        if (it.gameStatus == GameStatus.INPROGRESS) {
                            Toast.makeText(
                                this,
                                "The game is already in progress. You cannot join now.",
                                Toast.LENGTH_SHORT
                            ).show()
                            return@let
                        }

                        playersList.clear()
                        playersList.addAll(it.players)

                        // Check if the player limit is reached
                        if (playersList.size >= 6) {
                            Toast.makeText(
                                this,
                                "Lobby is full. No new players can join.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }

                        updatePlayerListUI()
                        this.gameModel = model
                        setUI()
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

                    snapshot?.toObject(GameModel::class.java)?.let { model ->
                        this.gameModel = model // Update local gameModel for all players, including the host
                        setUI() // Refresh the UI based on the game status

                        val currentTime = System.currentTimeMillis()

                        // Update secondTimerDuration
                        secondTimerDuration = model.secondTimerDuration ?: 60000 // Default to 60 seconds if null

                        if (model.gameStatus == GameStatus.INPROGRESS) {
                            // Start the first timer (hiding phase)
                            val hidingTimeRemaining = model.hidingPhaseEndTime?.minus(currentTime)
                            if (hidingTimeRemaining != null && hidingTimeRemaining > 0) {
                                startCountdownTimer(hidingTimeRemaining)
                            } else {
                                // If the hiding phase is over, start the seeking timer
                                val seekingTimeRemaining = model.secondTimerEndTime?.minus(currentTime)
                                if (seekingTimeRemaining != null && seekingTimeRemaining > 0) {
                                    startSecondCountdownTimer(seekingTimeRemaining)
                                } else {
                                    // Both timers are finished
                                    binding.timerText.text = "Time's up! Hiders won!"
                                    countDownTimer?.cancel()
                                    secondCountDownTimer?.cancel()
                                }
                            }
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

    private fun startCountdownTimer(durationMillis: Long) {
        countDownTimer?.cancel() // Cancel any existing timer
        countDownTimer = object : CountDownTimer(durationMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsLeft = millisUntilFinished / 1000
                binding.timerText.text = "Hiding time left: $secondsLeft seconds"
            }

            override fun onFinish() {
                binding.timerText.text = "Hiding phase over! Starting second timer..."
                // Start the second timer
                val safeSecondDuration = secondTimerDuration ?: 60000 // Default to 60 seconds
                startSecondCountdownTimer(safeSecondDuration)
            }
        }
        countDownTimer?.start()
    }

    private fun startSecondCountdownTimer(durationMillis: Long) {
        secondCountDownTimer?.cancel() // Cancel any existing second timer
        secondCountDownTimer = object : CountDownTimer(durationMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsLeft = millisUntilFinished / 1000
                binding.timerText.text = "Seeking time left: $secondsLeft seconds"
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
            binding.playerListRecyclerView.adapter = PlayerAdapter(playersList)
        } else {
            (binding.playerListRecyclerView.adapter as PlayerAdapter).notifyDataSetChanged()
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

