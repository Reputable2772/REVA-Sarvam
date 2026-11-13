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

data class HarvestDeclaration(
    val harvestId: String = "FR-KLR-20261113-0001",
    val crop: String = "Tomato",
    val harvestDate: String = "2026-11-13",
    val harvestTime: String = "07:00 AM",
    val expectedDateTime: String = "2026-11-14 09:00 AM",
    val sellImmediately: Boolean = true,
    val quantityKg: Double = 200.0,
    val qualityGrade: String = "Grade A",
    val hasStorageAccess: Boolean = true,
    val storageRequirementNeeded: Boolean = false,
    val farmerLatitude: Double = 13.133,
    val farmerLongitude: Double = 78.133,
    val locationSource: String = "GPS (Verified)"
)

object UserSession {
    var currentFarmer by mutableStateOf<FarmerProfile?>(
        FarmerProfile() // Pre-seeded with demo farmer, can be logged out
    )

    var activeHarvest by mutableStateOf<HarvestDeclaration?>(
        HarvestDeclaration() // Pre-seeded default harvest lot
    )

    val activeHarvestId: String
        get() = activeHarvest?.harvestId ?: "FR-KLR-20261113-0001"

    val isLoggedIn: Boolean
        get() = currentFarmer != null

    var isKannadaPreferred by mutableStateOf(true)

    fun login(farmer: FarmerProfile) {
        currentFarmer = farmer
    }

    fun setHarvest(declaration: HarvestDeclaration) {
        activeHarvest = declaration
    }

    fun logout() {
        currentFarmer = null
        activeHarvest = null
    }
}
