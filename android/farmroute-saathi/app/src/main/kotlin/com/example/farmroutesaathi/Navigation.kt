package com.example.farmroutesaathi

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.farmroutesaathi.data.UserSession
import com.example.farmroutesaathi.ui.auth.LoginScreen
import com.example.farmroutesaathi.ui.harvest.ColdStorageDiscoveryScreen
import com.example.farmroutesaathi.ui.harvest.HarvestQuestionnaireScreen
import com.example.farmroutesaathi.ui.harvest.MandiComparisonScreen
import com.example.farmroutesaathi.ui.main.MainScreen

@Composable
fun MainNavigation() {
  val initialKey = if (UserSession.isLoggedIn) Main else Login
  val backStack = rememberNavBackStack(initialKey)

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider =
      entryProvider {
        entry<Login> {
          LoginScreen(
            onLoginSuccess = {
              backStack.clear()
              backStack.add(HarvestQuestionnaire)
            }
          )
        }
        entry<HarvestQuestionnaire> {
          HarvestQuestionnaireScreen(
            onHarvestSubmitted = { harvestId, needsStorage, crop, qty ->
              backStack.clear()
              backStack.add(Main)
              val district = UserSession.currentFarmer?.district ?: "Local"
              val state = UserSession.currentFarmer?.state ?: ""
              val locText = if (state.isNotBlank()) "$district, $state" else district
              val coords = UserSession.getDistrictCoordinates(district, state)
              val lat = UserSession.activeHarvest?.farmerLatitude ?: coords.first
              val lon = UserSession.activeHarvest?.farmerLongitude ?: coords.second
              if (needsStorage) {
                backStack.add(ColdStorageDiscovery(
                  harvestId = harvestId,
                  crop = crop,
                  quantityKg = qty,
                  farmerLat = lat,
                  farmerLon = lon,
                  locationText = locText
                ))
              } else {
                backStack.add(MandiComparison(harvestId))
              }
            },
            onSkip = {
              backStack.clear()
              backStack.add(Main)
            }
          )
        }
        entry<MandiComparison> { key ->
          val effectiveHarvestId = key.harvestId.ifBlank { UserSession.activeHarvestId }
          MandiComparisonScreen(
            harvestId = effectiveHarvestId,
            onBackClick = { backStack.removeLastOrNull() }
          )
        }
        entry<ColdStorageDiscovery> { key ->
          val district = UserSession.currentFarmer?.district ?: "Local"
          val state = UserSession.currentFarmer?.state ?: ""
          val defaultLoc = if (state.isNotBlank()) "$district, $state" else district
          val effectiveHarvestId = key.harvestId.ifBlank { UserSession.activeHarvestId }
          val effectiveCrop = key.crop.ifBlank { UserSession.activeHarvest?.crop ?: "Produce" }
          val coords = UserSession.getDistrictCoordinates(district, state)
          val effectiveLat = if (key.farmerLat != 0.0) key.farmerLat else (UserSession.activeHarvest?.farmerLatitude ?: coords.first)
          val effectiveLon = if (key.farmerLon != 0.0) key.farmerLon else (UserSession.activeHarvest?.farmerLongitude ?: coords.second)
          val effectiveLoc = key.locationText.ifBlank { defaultLoc }
          ColdStorageDiscoveryScreen(
            harvestId = effectiveHarvestId,
            crop = effectiveCrop,
            quantityKg = key.quantityKg,
            farmerLat = effectiveLat,
            farmerLon = effectiveLon,
            locationText = effectiveLoc,
            onBackClick = { backStack.removeLastOrNull() }
          )
        }
        entry<Main> {
          MainScreen(
            onItemClick = { navKey -> backStack.add(navKey) },
            onDeclareHarvestClick = { backStack.add(HarvestQuestionnaire) },
            onMandiComparisonClick = { hId -> backStack.add(MandiComparison(hId)) },
            onColdStorageClick = { hId, crop, qty ->
              val district = UserSession.currentFarmer?.district ?: "Local"
              val state = UserSession.currentFarmer?.state ?: ""
              val locText = if (state.isNotBlank()) "$district, $state" else district
              val coords = UserSession.getDistrictCoordinates(district, state)
              val lat = UserSession.activeHarvest?.farmerLatitude ?: coords.first
              val lon = UserSession.activeHarvest?.farmerLongitude ?: coords.second
              backStack.add(ColdStorageDiscovery(
                harvestId = hId,
                crop = crop,
                quantityKg = qty,
                farmerLat = lat,
                farmerLon = lon,
                locationText = locText
              ))
            },
            onLogout = {
              UserSession.logout()
              backStack.clear()
              backStack.add(Login)
            }
          )
        }
      },
  )
}
