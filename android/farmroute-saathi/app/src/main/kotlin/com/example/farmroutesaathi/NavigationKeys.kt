package com.example.farmroutesaathi

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Login : NavKey
@Serializable data object HarvestQuestionnaire : NavKey
@Serializable data class MandiComparison(val harvestId: String = "FR-KLR-20261113-0001") : NavKey
@Serializable data class ColdStorageDiscovery(
    val harvestId: String = "FR-KLR-20261113-0001",
    val crop: String = "Tomato",
    val quantityKg: Double = 500.0,
    val farmerLat: Double = 13.133,
    val farmerLon: Double = 78.133,
    val locationText: String = "Kolar, Karnataka"
) : NavKey
@Serializable data object Main : NavKey
