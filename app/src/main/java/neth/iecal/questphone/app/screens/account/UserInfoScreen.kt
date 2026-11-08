package neth.iecal.questphone.app.screens.account

import android.app.Application
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.application
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import neth.iecal.questphone.R
import neth.iecal.questphone.app.navigation.RootRoute
import neth.iecal.questphone.app.screens.game.InventoryBox
import neth.iecal.questphone.app.screens.quest.stats.components.HeatMapChart
import neth.iecal.questphone.app.theme.LocalCustomTheme
import neth.iecal.questphone.backed.repositories.UserRepository
import neth.iecal.questphone.core.backup.LocalBackupManager
import neth.iecal.questphone.core.backup.LocalBackupResult
import nethical.questphone.backend.BuildConfig
import nethical.questphone.core.core.utils.formatNumber
import nethical.questphone.data.UserInfo
import nethical.questphone.data.game.InventoryItem
import nethical.questphone.data.xpToLevelUp
import java.io.File
import javax.inject.Inject


@HiltViewModel
class UserInfoViewModel @Inject constructor(
    application: Application,
    private val userRepository: UserRepository,
    private val localBackupManager: LocalBackupManager,
) : AndroidViewModel(application) {

    val userInfo: UserInfo = userRepository.userInfo
    val totalXpForNextLevel = xpToLevelUp(userInfo.level)

    val xpProgress = (userInfo.xp.toFloat() / totalXpForNextLevel )

    val profilePicLink = if (userInfo.has_profile){
        if(userInfo.isAnonymous){
            val profileFile = File(application.filesDir, "profile")
            profileFile.absolutePath
        }else{
            "${BuildConfig.SUPABASE_URL}/storage/v1/object/public/profile/${userRepository.getUserId()}/profile"
        }
    } else null

    val lastBackupSummary = MutableStateFlow(localBackupManager.getLastBackupSummary())
    val isBackupRunning = MutableStateFlow(false)
    val backupMessage = MutableStateFlow<String?>(null)

    /**
     * Exports profile + quests + stats + reminders to phone storage right now.
     */
    fun backupNow() {
        if (isBackupRunning.value) return
        viewModelScope.launch(Dispatchers.IO) {
            isBackupRunning.value = true
            backupMessage.value = null
            val result = localBackupManager.createBackup()
            lastBackupSummary.value = localBackupManager.getLastBackupSummary()
            backupMessage.value = when (result) {
                is LocalBackupResult.Success ->
                    "Saved ${result.questCount} quests & ${result.statCount} stat entries → ${result.location}"
                is LocalBackupResult.Failure ->
                    "Backup failed: ${result.message}"
            }
            isBackupRunning.value = false
        }
    }

    /**
     * Restores the on-device data set from a previously exported backup file.
     */
    fun restoreFromBackup(uri: android.net.Uri) {
        if (isBackupRunning.value) return
        viewModelScope.launch(Dispatchers.IO) {
            isBackupRunning.value = true
            backupMessage.value = null
            val result = localBackupManager.restore(uri)
            lastBackupSummary.value = localBackupManager.getLastBackupSummary()
            backupMessage.value = when (result) {
                is LocalBackupResult.Success ->
                    "Restored ${result.questCount} quests & ${result.statCount} stat entries ✓"
                is LocalBackupResult.Failure ->
                    "Restore failed: ${result.message}"
            }
            isBackupRunning.value = false
        }
    }

}


@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun UserInfoScreen(viewModel: UserInfoViewModel = hiltViewModel(),navController: NavController) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    Scaffold(containerColor = LocalCustomTheme.current.getRootColorScheme().surface,
        contentWindowInsets = WindowInsets(0),
        ) { innerPadding ->
        Box(Modifier
            .padding(innerPadding)

        ) {
            Box(Modifier.graphicsLayer {
                translationY = -scrollState.value * 0.5f
            }) {
                LocalCustomTheme.current.ThemeObjects(innerPadding)
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(scrollState)

                ) {
                Row(
                    modifier = Modifier.padding( WindowInsets.statusBarsIgnoringVisibility.asPaddingValues()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    // Profile Header
                    Text(
                        text = "Profile",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.weight(1f))

                    Icon(
                        painter = painterResource(R.drawable.outline_share_24),
                        contentDescription = "Share Profile",
                        modifier = Modifier.clickable(true, onClick = {
                            if(!viewModel.userInfo.isAnonymous) {
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        "https://questphone.app/@${viewModel.userInfo.username}"
                                    )
                                }
                                val shareIntent =
                                    Intent.createChooser(sendIntent, "Share Profile via")
                                context.startActivity(shareIntent)
                            }else{
                                Toast.makeText(context,"You're profile is local", Toast.LENGTH_SHORT).show()
                            }
                        })
                    )
                    Spacer(Modifier.size(4.dp))
                    Menu(navController)
                }
                Spacer(Modifier.size(32.dp))

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Avatar
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable{
                                navController.navigate(RootRoute.SetupProfile.route)
                            }
                    ) {
                        Image(
                            painter = rememberAsyncImagePainter(

                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(viewModel.profilePicLink)
                                    .crossfade(true)
                                    .error(R.drawable.baseline_person_24)
                                    .placeholder(R.drawable.baseline_person_24)
                                    .build(),
                            ),
                            contentDescription = "Avatar",
                            Modifier.fillMaxSize(),
                            colorFilter = if (viewModel.profilePicLink == null)
                                ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
                            else
                                null,
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        "@${viewModel.userInfo.username}",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Text(
                        viewModel.userInfo.username,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(24.dp))


                    // Level Progress Bar
                    Column(
                        modifier = Modifier.width(250.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Level ${viewModel.userInfo.level}",
                                color = MaterialTheme.colorScheme.outline,
                                fontSize = 12.sp
                            )
                            Text(
                                "XP: ${viewModel.userInfo.xp} / ${viewModel.totalXpForNextLevel}",
                                color = MaterialTheme.colorScheme.outline,
                                fontSize = 12.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(12.dp)
                                .align(Alignment.CenterHorizontally)
                        ) {
                            LinearProgressIndicator(
                                progress = { viewModel.xpProgress },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // Stats Box
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = LocalCustomTheme.current.getExtraColorScheme().toolBoxContainer,
                                shape = RoundedCornerShape(16.dp),
                            )
                            .alpha(0.7f),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            StatItem(
                                value = formatNumber(viewModel.userInfo.coins),
                                label = "coins"
                            )

                            StatItem(
                                value = "${formatNumber(viewModel.userInfo.streak.currentStreak)}d",
                                label = "Streak"
                            )

                            StatItem(
                                value = "${formatNumber(viewModel.userInfo.streak.longestStreak)}d",
                                label = "Top Streak"
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
                HeatMapChart(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                )
                Spacer(modifier = Modifier.height(32.dp))

                InventoryBox(navController)

                Spacer(modifier = Modifier.height(32.dp))

                BackupRestoreCard(viewModel)

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Menu(navController: NavController) {
    var expanded by remember { mutableStateOf(false) }

    val context = LocalContext.current
    IconButton(onClick = { expanded = true }) {
        Icon(
            imageVector = Icons.Default.MoreVert, // This is the 3-dot icon
            contentDescription = "More Options"
        )
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false }
    ) {
        DropdownMenuItem(
            text = { Text("Communicate with us") },
            onClick = {
                navController.navigate(RootRoute.ShowSocials.route)
            }
        )
        DropdownMenuItem(
            text = { Text("Donate") },
            onClick = {
                navController.navigate(RootRoute.ShowSocials.route)
            }
        )

        DropdownMenuItem(
            text = { Text("Open Tutorial") },
            onClick = {
                navController.navigate(RootRoute.ShowTutorials.route)
            }
        )
        DropdownMenuItem(
            text = { Text("Share Crash Log") },
            onClick = {
                shareCrashLog(context)
            }
        )
    }
}

/**
 * Backup & Restore card for the fully local build. Shows the time of the last
 * automatic daily backup and offers manual "Back up now" / "Restore from file"
 * actions. Everything happens on-device — no server is ever contacted.
 */
@Composable
private fun BackupRestoreCard(viewModel: UserInfoViewModel) {
    val lastBackup by viewModel.lastBackupSummary.collectAsState()
    val isRunning by viewModel.isBackupRunning.collectAsState()
    val message by viewModel.backupMessage.collectAsState()

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.restoreFromBackup(uri)
        }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = "Backup & Restore",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Your data lives on this phone only. A full copy " +
                    "(profile, quests, stats, reminders) is saved to your phone " +
                    "storage automatically every day.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 6.dp)
            )
            Text(
                text = "Last backup: $lastBackup",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 10.dp)
            )
            message?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            Row(
                modifier = Modifier.padding(top = 8.dp)
            ) {
                TextButton(
                    enabled = !isRunning,
                    onClick = { viewModel.backupNow() }
                ) {
                    Text(if (isRunning) "Backing up…" else "Back up now")
                }
                TextButton(
                    enabled = !isRunning,
                    onClick = {
                        filePicker.launch(
                            arrayOf(
                                "application/json",
                                "application/octet-stream",
                                "text/plain"
                            )
                        )
                    }
                ) {
                    Text("Restore from file")
                }
            }
        }
    }
}


@Composable
private fun StatItem(
    value: String,
    label: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineLarge
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,

        )
    }
}


@Composable
fun ActiveBoostsItem(
    item: InventoryItem,
    remaining: String,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Item preview/icon
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF2A2A2A)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(item.icon),
                    contentDescription = item.simpleName
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Item details
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = item.simpleName,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(R.drawable.baseline_timer_24),
                        contentDescription = "Remaining Time",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = remaining,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }


        }
    }
}


fun shareCrashLog(context: Context) {
    val logFile = File(context.filesDir, "crash_log.txt")
    if (!logFile.exists()) {
        Toast.makeText(context, "No crash logs found", Toast.LENGTH_SHORT).show()
        return
    }

    val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", logFile)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Crash Log")
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    context.startActivity(Intent.createChooser(intent, "Share Crash Log"))
}
