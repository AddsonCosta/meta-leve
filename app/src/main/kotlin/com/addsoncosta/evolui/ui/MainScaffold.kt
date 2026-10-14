package com.addsoncosta.evolui.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.addsoncosta.evolui.ui.exercise.ExerciseScreen
import com.addsoncosta.evolui.ui.food.FoodScreen
import com.addsoncosta.evolui.ui.history.HistoryScreen
import com.addsoncosta.evolui.ui.settings.SettingsScreen
import com.addsoncosta.evolui.ui.today.TodayScreen

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    TODAY("hoje", "Hoje", Icons.Filled.Today),
    FOOD("alimentos", "Alimentos", Icons.Filled.Restaurant),
    EXERCISE("exercicios", "Exercícios", Icons.AutoMirrored.Filled.DirectionsRun),
    HISTORY("historico", "Histórico", Icons.Filled.ShowChart),
    PROFILE("perfil", "Perfil", Icons.Filled.Person),
}

@Composable
fun MainScaffold() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route

    fun go(route: String) = nav.navigate(route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = current == tab.route,
                        onClick = { go(tab.route) },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label, maxLines = 1) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Tab.TODAY.route, modifier = Modifier.padding(padding)) {
            composable(Tab.TODAY.route) {
                TodayScreen(onAddFood = { go(Tab.FOOD.route) }, onAddExercise = { go(Tab.EXERCISE.route) })
            }
            composable(Tab.FOOD.route) { FoodScreen() }
            composable(Tab.EXERCISE.route) { ExerciseScreen() }
            composable(Tab.HISTORY.route) { HistoryScreen() }
            composable(Tab.PROFILE.route) { SettingsScreen() }
        }
    }
}
