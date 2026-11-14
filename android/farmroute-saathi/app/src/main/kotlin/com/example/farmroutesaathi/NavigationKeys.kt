package com.example.farmroutesaathi

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Login : NavKey
@Serializable data object HarvestQuestionnaire : NavKey
@Serializable data class MandiComparison(val harvestId: String = "") : NavKey
@Serializable data class ColdStorageDiscovery(
    val harvestId: String = "",
    val crop: String = "Produce",
    val quantityKg: Double = 200.0,
    val farmerLat: Double = 0.0,
    val farmerLon: Double = 0.0,
    val locationText: String = ""
) : NavKey
@Serializable data object Main : NavKey
