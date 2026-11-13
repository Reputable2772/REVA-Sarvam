package com.example.farmroutesaathi

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.farmroutesaathi.data.UserSession
import com.example.farmroutesaathi.ui.auth.LoginScreen
import com.example.farmroutesaathi.ui.harvest.HarvestQuestionnaireScreen
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
            onHarvestSubmitted = { harvestId ->
              backStack.clear()
              backStack.add(Main)
            },
            onSkip = {
              backStack.clear()
              backStack.add(Main)
            }
          )
        }
        entry<Main> {
          MainScreen(
            onItemClick = { navKey -> backStack.add(navKey) },
            onDeclareHarvestClick = { backStack.add(HarvestQuestionnaire) },
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
