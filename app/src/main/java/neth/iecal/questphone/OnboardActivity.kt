package neth.iecal.questphone

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import neth.iecal.questphone.app.navigation.RootRoute
import neth.iecal.questphone.app.screens.onboard.OnBoarderView
import neth.iecal.questphone.app.screens.onboard.subscreens.TermsScreen
import neth.iecal.questphone.app.theme.LauncherTheme
import neth.iecal.questphone.app.theme.customThemes.PitchBlackTheme


@AndroidEntryPoint(ComponentActivity::class)
class OnboardActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        val pitchBlackTheme = PitchBlackTheme()
        setContent {
            val data = getSharedPreferences("onboard", MODE_PRIVATE)
            val isUserOnboarded = remember {mutableStateOf(true)}
            isUserOnboarded.value = data.getBoolean("onboard",false)
            Log.d("onboard", isUserOnboarded.value.toString())

            if(isUserOnboarded.value) {
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
                finish()
            }

            val context = LocalContext.current

            val isTosAccepted = remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                val tosp = context.getSharedPreferences("terms", MODE_PRIVATE)
                isTosAccepted.value = tosp.getBoolean("isAccepted",false)
            }

            val startDestination = if (!isTosAccepted.value) RootRoute.TermsScreen.route
            else RootRoute.OnBoard.route

            LauncherTheme(pitchBlackTheme) {
                Surface {
                    val navController = rememberNavController()

                    NavHost(
                        navController = navController,
                        startDestination = startDestination
                    ) {

                        composable(RootRoute.OnBoard.route) {
                            OnBoarderView(navController)
                        }

                        composable(RootRoute.TermsScreen.route) {
                            TermsScreen(isTosAccepted)
                        }
                    }
                }
            }
        }
    }
}
