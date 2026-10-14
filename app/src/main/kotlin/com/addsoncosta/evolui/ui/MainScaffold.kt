package com.addsoncosta.evolui.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.addsoncosta.evolui.ui.badges.BadgesScreen
import com.addsoncosta.evolui.ui.exercise.ExerciseScreen
import com.addsoncosta.evolui.ui.fasting.FastingScreen
import com.addsoncosta.evolui.ui.food.FoodScreen
import com.addsoncosta.evolui.ui.health.HealthScreen
import com.addsoncosta.evolui.ui.progress.ProgressScreen
import com.addsoncosta.evolui.ui.settings.SettingsScreen
import com.addsoncosta.evolui.ui.today.TodayScreen

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    TODAY("hoje", "Hoje", Icons.Filled.Today),
    FOOD("alimentos", "Comer", Icons.Filled.Restaurant),
    EXERCISE("exercicios", "Treino", Icons.AutoMirrored.Filled.DirectionsRun),
    FASTING("jejum", "Jejum", Icons.Filled.Timer),
    HEALTH("saude", "Saúde", Icons.Filled.Favorite),
}

/** Telas abertas por cima das abas (sem a barra de baixo). */
private object Routes {
    const val SETTINGS = "configuracoes"
    const val PROGRESS = "progresso"
    const val BADGES = "conquistas"
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
    fun open(route: String) = nav.navigate(route) { launchSingleTop = true }
    val back: () -> Unit = { nav.popBackStack() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (Tab.entries.any { it.route == current }) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = current == tab.route,
                            onClick = { go(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                indicatorColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Tab.TODAY.route, modifier = Modifier.padding(padding)) {
            composable(Tab.TODAY.route) {
                TodayScreen(
                    onAddFood = { go(Tab.FOOD.route) },
                    onAddExercise = { go(Tab.EXERCISE.route) },
                    onOpenSettings = { open(Routes.SETTINGS) },
                    onOpenProgress = { open(Routes.PROGRESS) },
                    onOpenBadges = { open(Routes.BADGES) },
                    onOpenFasting = { go(Tab.FASTING.route) },
                    onOpenHealth = { go(Tab.HEALTH.route) },
                )
            }
            composable(Tab.FOOD.route) { FoodScreen() }
            composable(Tab.EXERCISE.route) { ExerciseScreen() }
            composable(Tab.FASTING.route) { FastingScreen() }
            composable(Tab.HEALTH.route) {
                HealthScreen(onOpenProgress = { open(Routes.PROGRESS) }, onOpenBadges = { open(Routes.BADGES) })
            }
            composable(Routes.PROGRESS) { ProgressScreen(onBack = back) }
            composable(Routes.BADGES) { BadgesScreen(onBack = back) }
            composable(Routes.SETTINGS) { SettingsScreen(onBack = back) }
        }
    }
}
