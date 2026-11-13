package com.example.farmroutesaathi

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Login : NavKey
@Serializable data object HarvestQuestionnaire : NavKey
@Serializable data class MandiComparison(val harvestId: String = "FR-KLR-20261113-0001") : NavKey
@Serializable data object Main : NavKey
