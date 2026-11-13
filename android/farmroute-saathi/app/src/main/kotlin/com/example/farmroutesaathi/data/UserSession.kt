package com.example.farmroutesaathi.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

data class FarmerProfile(
    val phone: String = "9845012345",
    val name: String = "Manjunatha Gowda",
    val state: String = "Karnataka",
    val district: String = "Kolar",
    val pincode: String = "563101",
    val token: String = "sim-token-001"
)

object UserSession {
    var currentFarmer by mutableStateOf<FarmerProfile?>(
        FarmerProfile() // Pre-seeded with demo farmer, can be logged out
    )
    
    val isLoggedIn: Boolean
        get() = currentFarmer != null

    fun login(farmer: FarmerProfile) {
        currentFarmer = farmer
    }

    fun logout() {
        currentFarmer = null
    }
}
