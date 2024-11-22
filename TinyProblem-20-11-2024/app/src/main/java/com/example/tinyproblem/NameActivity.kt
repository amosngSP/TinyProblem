package com.example.tinyproblem

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.tinyproblem.databinding.ActivityNameBinding
import com.google.firebase.firestore.FirebaseFirestore

class NameActivity : AppCompatActivity() {

    lateinit var binding: ActivityNameBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityNameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Handle "next" button click
        binding.nextBtn.setOnClickListener {
            val playerName = binding.playerNameInput.text.toString() // Get the player name

            // Validate if the name is empty
            if (playerName.isEmpty()) {
                binding.playerNameInput.error = "Please enter your name"
                return@setOnClickListener
            }

            // Save the name to Firebase and proceed if successful
            saveNameToFirebase(playerName)
        }
    }

    private fun saveNameToFirebase(playerName: String) {
        val firestore = FirebaseFirestore.getInstance()
        val playerData = hashMapOf("name" to playerName)

        // Add player data to Firestore
        firestore.collection("players")
            .add(playerData)
            .addOnSuccessListener {
                // Show success message and proceed to the next activity
                Toast.makeText(this, "Player name saved successfully!", Toast.LENGTH_SHORT).show()
                nextPage(playerName) // Proceed to MainActivity after saving the name
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to save player name: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun nextPage(playerName: String) {
        val intent = Intent(this, MainActivity::class.java)
        intent.putExtra("keyName", playerName) // Pass the player name to MainActivity
        startActivity(intent)
        finish() // Optional: finish NameActivity to prevent returning here
    }
}
