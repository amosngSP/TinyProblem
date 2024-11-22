package com.example.tinyproblem

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.tinyproblem.databinding.ActivityMainBinding
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.random.Random
import kotlin.random.nextInt

class MainActivity : AppCompatActivity() {

    lateinit var binding: ActivityMainBinding

    override fun onResume() {
        super.onResume()
        binding.joinOnlineGameBtn.isEnabled = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        enableEdgeToEdge()
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val playerName = intent.getStringExtra("keyName")

        if (playerName.isNullOrEmpty()) {
            // Show an error message or handle it
            Toast.makeText(this, "Player name is required", Toast.LENGTH_SHORT).show()
            return
        }

        binding.createOnlineGameBtn.setOnClickListener {
            createOnlineGame(playerName)
        }

        binding.joinOnlineGameBtn.setOnClickListener {
            val gameId =
                binding.gameIdInput.text.toString() // Get the game ID from an EditText field
            val playerName = intent.getStringExtra("keyName")
                ?: "DefaultPlayerName"  // Default player name if null

            if (gameId.isNotEmpty()) {
                // Disable the button while the game join process is ongoing
                binding.joinOnlineGameBtn.isEnabled = false
                joinOnlineGame(gameId, playerName)
            } else {
                Toast.makeText(this, "Please enter a game ID", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Create Online Game (for host)
    fun createOnlineGame(playerName: String) {
        GameData.myID = playerName
        val gameId = Random.nextInt(1000..9999).toString()

        // Save the initial game state with an empty list of players (only host is added initially)
        val gameModel = GameModel(
            gameStatus = GameStatus.CREATED,
            gameId = gameId,
            players = mutableListOf(GameActivity.Player(playerName, "Hider")) // Create a Player object
        )

        // Save the game to Firestore
        GameData.saveGameModel(gameModel)

        // Start the game (this will transition to the next activity)
        startGame(gameId)
    }

    // Start the game for all players
    fun startGame(gameId: String) {
        val intent = Intent(this, GameActivity::class.java)
        intent.putExtra("gameId", gameId)  // Pass gameId to GameActivity
        intent.putExtra("keyName", GameData.myID)  // Pass playerName
        startActivity(intent)
    }

    fun joinOnlineGame(gameId: String, playerName: String) {
        FirebaseFirestore.getInstance().collection("games")
            .document(gameId)
            .get()
            .addOnSuccessListener { documentSnapshot ->
                val game = documentSnapshot.toObject(GameModel::class.java)

                if (game != null) {
                    // Prevent joining if the game is already in progress
                    if (game.gameStatus == GameStatus.INPROGRESS) {
                        Toast.makeText(this, "The game is already in progress. You cannot join now.", Toast.LENGTH_SHORT).show()
                        binding.joinOnlineGameBtn.isEnabled = true
                        return@addOnSuccessListener
                    }

                    if (game.players.size >= 6) {
                        // Show a message if the lobby is full
                        Toast.makeText(this, "Lobby is full. Cannot join this game.", Toast.LENGTH_SHORT).show()
                        binding.joinOnlineGameBtn.isEnabled = true
                        return@addOnSuccessListener
                    }

                    // Create a mutable copy of the players list
                    val updatedPlayers = game.players.toMutableList().apply {
                        if (!any { it.playerName == playerName }) {  // Check if playerName exists in the list
                            add(GameActivity.Player(playerName, "Hider")) // Assuming "Hider" is the default role
                        }
                    }

                    // Update the players list and gameStatus
                    val updatedGame = game.copy(
                        players = updatedPlayers,
                        gameStatus = GameStatus.JOINED
                    )

                    // Save the updated game to Firestore
                    FirebaseFirestore.getInstance().collection("games")
                        .document(gameId)
                        .set(updatedGame)
                        .addOnSuccessListener {
                            val intent = Intent(this, GameActivity::class.java)
                            intent.putExtra("gameId", gameId)
                            intent.putExtra("keyName", playerName)
                            startActivity(intent)
                        }
                        .addOnFailureListener { exception ->
                            Toast.makeText(this, "Failed to join game: ${exception.message}", Toast.LENGTH_SHORT).show()
                            binding.joinOnlineGameBtn.isEnabled = true
                        }
                } else {
                    Toast.makeText(this, "Game not found", Toast.LENGTH_SHORT).show()
                    binding.joinOnlineGameBtn.isEnabled = true
                }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Failed to fetch game: ${exception.message}", Toast.LENGTH_SHORT).show()
                binding.joinOnlineGameBtn.isEnabled = true
            }
    }
}