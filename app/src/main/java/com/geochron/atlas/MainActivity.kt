package com.geochron.atlas

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.geochron.atlas.ui.BookmarksScreen
import com.geochron.atlas.ui.ExploreScreen
import com.geochron.atlas.ui.GeoChronTheme
import com.geochron.atlas.ui.HomeScreen
import com.geochron.atlas.ui.NotesScreen
import com.geochron.atlas.ui.QuizScreen
import com.geochron.atlas.ui.SettingsScreen
import com.geochron.atlas.ui.TimelineScreen
import com.geochron.atlas.ui.t

/**
 * GeoChron — Interactive Geography & History Atlas.
 * Single-activity Compose app: Home / Explore / Timeline / Quiz / Bookmarks.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: AppViewModel = viewModel()
            val dark by vm.dark.collectAsState()
            GeoChronTheme(dark = dark) {
                GeoChronApp(vm)
            }
        }
    }
}

@Composable
private fun GeoChronApp(vm: AppViewModel) {
    val nav = rememberNavController()
    val hindi by vm.hindi.collectAsState()
    val isMapFullScreen by vm.isMapFullScreen.collectAsState()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route ?: "home"
    val showBottomBar = route != "settings" && !isMapFullScreen

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    BottomItem(nav, route, "home", Icons.Filled.Home, t(hindi, "होम", "Home"))
                    BottomItem(nav, route, "explore", Icons.Filled.Explore, t(hindi, "एटलस", "Explore"))
                    BottomItem(nav, route, "timeline", Icons.Filled.Timeline, t(hindi, "टाइमलाइन", "Timeline"))
                    BottomItem(nav, route, "quiz", Icons.Filled.EmojiEvents, t(hindi, "क्विज़", "Quiz"))
                    BottomItem(nav, route, "bookmarks", Icons.Filled.Bookmark, t(hindi, "बुकमार्क", "Saved"))
                }
            }
        }
    ) { padding ->
        NavHost(navController = nav, startDestination = "home", modifier = Modifier.padding(padding)) {
            composable("home") { HomeScreen(nav, vm) }
            composable("explore") { ExploreScreen(nav, vm) }
            composable("timeline") { TimelineScreen(nav, vm) }
            composable("quiz") { QuizScreen(nav, vm) }
            composable("bookmarks") { BookmarksScreen(nav, vm) }
            composable("notes") { NotesScreen(nav, vm) }
            composable("settings") { SettingsScreen(nav, vm) }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.BottomItem(
    nav: NavController, route: String, dest: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector, label: String
) {
    NavigationBarItem(
        selected = route == dest,
        onClick = {
            nav.navigate(dest) {
                popUpTo("home") { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        },
        icon = { Icon(icon, contentDescription = label) },
        label = { Text(label, maxLines = 1) }
    )
}
