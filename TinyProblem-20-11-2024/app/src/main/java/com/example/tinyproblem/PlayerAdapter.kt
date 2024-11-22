package com.example.tinyproblem

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.tinyproblem.GameActivity.Player
import com.example.tinyproblem.databinding.ItemPlayerBinding



class PlayerAdapter(private val players: MutableList<GameActivity.Player>) : RecyclerView.Adapter<PlayerAdapter.PlayerViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlayerViewHolder {
        val binding = ItemPlayerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PlayerViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PlayerViewHolder, position: Int) {
        val player = players[position]
        holder.bind(player)  // This will bind Player's name and role
    }

    override fun getItemCount(): Int = players.size

    inner class PlayerViewHolder(private val binding: ItemPlayerBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(player: Player) {
            binding.playerNameTextView.text = player.playerName  // Binding Player's name
            binding.playerRoleTextView.text = player.role  // Binding Player's role
        }
    }
}
