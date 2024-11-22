package com.example.tinyproblem

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.Firebase
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.firestore

object GameData {
    private var _gameModel: MutableLiveData<GameModel> = MutableLiveData()
    var gameModel: LiveData<GameModel> = _gameModel
    var myID = ""

    // Method to save the GameModel both locally and to Firestore
    fun saveGameModel(model: GameModel) {
        _gameModel.postValue(model) // Update local LiveData

        // Save to Firestore
        Firebase.firestore.collection("games")
            .document(model.gameId)
            .set(model)
    }

    // Method to update the game model for the lobby host's phone (Change screen to reflect appropriate layout)
    fun fetchGameModel(gameId: String) {
        // Check if we already have a game model in memory to avoid unnecessary fetch
        if (_gameModel.value?.gameId != gameId) {
            Firebase.firestore.collection("games")
                .document(gameId)
                .addSnapshotListener { value, error ->
                    if (error != null) {
                        // Handle errors if any
                        return@addSnapshotListener
                    }

                    val model = value?.toObject(GameModel::class.java)
                    if (model != null) {
                        _gameModel.postValue(model)  // Update LiveData with fetched game model
                    }
            }
        }
    }
}


