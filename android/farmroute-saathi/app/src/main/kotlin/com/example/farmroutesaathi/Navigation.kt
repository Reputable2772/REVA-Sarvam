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
              if (needsStorage) {
                backStack.add(ColdStorageDiscovery(harvestId = harvestId, crop = crop, quantityKg = qty))
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
          MandiComparisonScreen(
            harvestId = key.harvestId,
            onBackClick = { backStack.removeLastOrNull() }
          )
        }
        entry<ColdStorageDiscovery> { key ->
          ColdStorageDiscoveryScreen(
            harvestId = key.harvestId,
            crop = key.crop,
            quantityKg = key.quantityKg,
            farmerLat = key.farmerLat,
            farmerLon = key.farmerLon,
            locationText = key.locationText,
            onBackClick = { backStack.removeLastOrNull() }
          )
        }
        entry<Main> {
          MainScreen(
            onItemClick = { navKey -> backStack.add(navKey) },
            onDeclareHarvestClick = { backStack.add(HarvestQuestionnaire) },
            onMandiComparisonClick = { hId -> backStack.add(MandiComparison(hId)) },
            onColdStorageClick = { hId, crop, qty ->
              backStack.add(ColdStorageDiscovery(harvestId = hId, crop = crop, quantityKg = qty))
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
