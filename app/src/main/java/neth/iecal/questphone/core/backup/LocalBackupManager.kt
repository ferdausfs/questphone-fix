package neth.iecal.questphone.core.backup

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import neth.iecal.questphone.backed.repositories.QuestRepository
import neth.iecal.questphone.backed.repositories.StatsRepository
import neth.iecal.questphone.backed.repositories.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import neth.iecal.questphone.core.services.reloadServiceInfo
import neth.iecal.questphone.core.utils.reminder.NotificationScheduler
import neth.iecal.questphone.data.CommonQuestInfo
import neth.iecal.questphone.data.ReminderData
import neth.iecal.questphone.data.ReminderDatabaseProvider
import neth.iecal.questphone.data.StatsInfo
import nethical.questphone.data.UserInfo
import nethical.questphone.data.json
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class LocalBackupPayload(
    val type: String = PAYLOAD_TYPE,
    val version: Int = BACKUP_VERSION,
    val created_at: Long = 0L,
    val app_version: String = "",
    val user_info: UserInfo,
    val quests: List<CommonQuestInfo> = emptyList(),
    val stats: List<StatsInfo> = emptyList(),
    val reminders: List<ReminderData> = emptyList()
) {
    companion object {
        const val PAYLOAD_TYPE = "questphone-backup"
        const val BACKUP_VERSION = 1
    }
}

sealed class LocalBackupResult {
    data class Success(
        val fileName: String,
        val location: String,
        val questCount: Int,
        val statCount: Int
    ) : LocalBackupResult()

    data class Failure(val message: String) : LocalBackupResult()
}

/**
 * Fully offline backup system. Exports the entire on-device data set
 * (profile, quests, stats, reminders) to:
 *  1. Android 10+ : `Download/QuestPhone/` via MediaStore (no permission needed)
 *  2. Android 8/9 : the app-specific external directory (no permission needed)
 * plus a rolling internal copy used for in-app restore.
 */
@Singleton
class LocalBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userRepository: UserRepository,
    private val questRepository: QuestRepository,
    private val statsRepository: StatsRepository
) {

    private val reminderDao
        get() = ReminderDatabaseProvider.getInstance(context).reminderDao()

    suspend fun createBackup(): LocalBackupResult {
        return try {
            if (!isOnboarded()) {
                return LocalBackupResult.Failure("Onboarding not finished yet")
            }

            val quests = questRepository.getAllQuests().first()
            val stats = statsRepository.getAllStatsForUser().first()
            val reminders = reminderDao.getAll()

            // Encode a deep-copied snapshot: the live UserInfo HashMaps are mutated
            // on the main thread while this worker serializes them in the background
            // (ConcurrentModificationException = silently failed daily backups).
            val payload = LocalBackupPayload(
                created_at = System.currentTimeMillis(),
                app_version = appVersion(),
                user_info = userRepository.snapshotUserInfo(),
                quests = quests,
                stats = stats,
                reminders = reminders
            )

            val bytes = json.encodeToString(LocalBackupPayload.serializer(), payload)
                .toByteArray(Charsets.UTF_8)

            val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
            writeInternalBackup("questphone-backup-$stamp.json", bytes)

            val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val publicName = "questphone-backup-$day.json"
            val publicPath = writePublicBackup(publicName, bytes)
                ?: "Internal app storage (phone storage unavailable)"

            markBackedUp()
            Log.d(TAG, "Backup done: $publicPath (quests=${quests.size}, stats=${stats.size})")
            LocalBackupResult.Success(publicName, publicPath, quests.size, stats.size)
        } catch (e: Exception) {
            Log.e(TAG, "Backup failed", e)
            LocalBackupResult.Failure(e.message ?: "Unknown error")
        }
    }

    suspend fun restore(uri: Uri): LocalBackupResult {
        return try {
            val text = context.contentResolver.openInputStream(uri)?.use {
                it.readBytes().toString(Charsets.UTF_8)
            } ?: return LocalBackupResult.Failure("Could not read the selected file")

            val payload = try {
                json.decodeFromString(LocalBackupPayload.serializer(), text)
            } catch (e: Exception) {
                // DateTimeParseException & friends from custom serializers are NOT
                // SerializationException subclasses — catch everything.
                return LocalBackupResult.Failure("This is not a QuestPhone backup file")
            }

            if (payload.type != LocalBackupPayload.PAYLOAD_TYPE) {
                return LocalBackupResult.Failure("This is not a QuestPhone backup file")
            }

            // Sanitize BEFORE anything is written: a backup can contain garbage
            // dates (old locale-digit bug, hand-edited files) that would otherwise
            // crash unrelated screens days later.
            UserRepository.sanitizeUserInfo(payload.user_info, resetInvalidStreak = true)
            val sanitizedQuests = payload.quests.map { sanitizeQuest(it) }

            questRepository.clearAll()
            if (sanitizedQuests.isNotEmpty()) {
                questRepository.upsertAll(sanitizedQuests)
            }

            statsRepository.deleteAll()
            payload.stats.forEach { statsRepository.upsertStats(it) }

            reminderDao.deleteAll()
            if (payload.reminders.isNotEmpty()) {
                reminderDao.upsertAll(payload.reminders)
            }

            withContext(NonCancellable) {
                // Swap the live profile on the main thread; doing this from an IO
                // dispatcher races the UI (lost updates after restore).
                withContext(Dispatchers.Main) {
                    userRepository.replaceUserInfo(payload.user_info)
                }
            }

            // Re-schedule every reminder from the restored data and refresh the
            // app blocker service info so the restored block list applies at once.
            NotificationScheduler(context, questRepository).reloadAllReminders()
            reloadServiceInfo(context)

            Log.d(
                TAG,
                "Restore done: quests=${payload.quests.size}, stats=${payload.stats.size}"
            )
            LocalBackupResult.Success(
                "restore",
                "Restored on this device",
                payload.quests.size,
                payload.stats.size
            )
        } catch (e: Exception) {
            Log.e(TAG, "Restore failed", e)
            LocalBackupResult.Failure(e.message ?: "Unknown error")
        }
    }

    /** Repairs quest rows that would crash parsers later (time_range, dates). */
    private fun sanitizeQuest(quest: CommonQuestInfo): CommonQuestInfo {
        if (quest.time_range.size < 2) {
            quest.time_range = listOf(0, 24)
        }
        if (quest.auto_destruct.isNotBlank()) {
            val ok = runCatching {
                LocalDate.parse(quest.auto_destruct, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            }.isSuccess
            if (!ok) quest.auto_destruct = ""
        }
        return quest
    }

    fun getLastBackupSummary(): String {
        val t = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_BACKUP, 0L)
        if (t == 0L) return "Never"
        return SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US).format(Date(t))
    }

    fun isOnboarded(): Boolean {
        return context.getSharedPreferences("onboard", Context.MODE_PRIVATE)
            .getBoolean("onboard", false)
    }

    private fun markBackedUp() {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_BACKUP, System.currentTimeMillis())
            .apply()
    }

    private fun appVersion(): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun writeInternalBackup(fileName: String, bytes: ByteArray) {
        val dir = File(context.filesDir, INTERNAL_DIR).apply { mkdirs() }
        // Atomic write (temp file + rename): a process kill mid-write must never
        // leave a torn backup file behind.
        val tmp = File(dir, "$fileName.tmp")
        tmp.writeBytes(bytes)
        val dst = File(dir, fileName)
        if (!tmp.renameTo(dst)) {
            dst.writeBytes(bytes)
            tmp.delete()
        }
        val backups = dir.listFiles()
            ?.filter { it.name.startsWith("questphone-backup-") }
            ?.sortedBy { it.name }
            ?: return
        if (backups.size > KEEP_INTERNAL) {
            backups.take(backups.size - KEEP_INTERNAL).forEach { it.delete() }
        }
    }

    /**
     * Writes the daily backup into shared phone storage.
     * @return human readable location, or null when the write failed.
     */
    private fun writePublicBackup(fileName: String, bytes: ByteArray): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val projection = arrayOf(MediaStore.MediaColumns._ID)

                // Same-day file already exists and is owned by this app → overwrite it.
                val existing = resolver.query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    projection,
                    "${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
                    arrayOf(fileName),
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val id = cursor.getLong(
                            cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                        )
                        ContentUris.withAppendedId(
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI, id
                        )
                    } else null
                }

                val target = existing ?: run {
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                        put(
                            MediaStore.MediaColumns.RELATIVE_PATH,
                            Environment.DIRECTORY_DOWNLOADS + "/" + PUBLIC_DIR
                        )
                    }
                    resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                } ?: return null

                resolver.openOutputStream(target, "wt")?.use { it.write(bytes) }
                    ?: return null

                "Download/$PUBLIC_DIR/$fileName"
            } else {
                // Android 8/9: shared storage writes need a runtime permission, so the
                // public copy lands in the app-specific external directory instead.
                val dir = File(
                    context.getExternalFilesDir(null) ?: context.filesDir,
                    PUBLIC_DIR
                ).apply { mkdirs() }
                File(dir, fileName).writeBytes(bytes)
                "Phone storage/Android/data/${context.packageName}/files/$PUBLIC_DIR/$fileName"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Public backup write failed", e)
            null
        }
    }

    companion object {
        private const val TAG = "LocalBackupManager"
        private const val PREFS = "backup_prefs"
        private const val KEY_LAST_BACKUP = "last_backup_time"
        private const val INTERNAL_DIR = "backups"
        private const val PUBLIC_DIR = "QuestPhone"
        private const val KEEP_INTERNAL = 7
        private const val BACKUP_INTERVAL_MS = 24 * 60 * 60 * 1000L

        /** True when the user is onboarded and the last backup is older than 24h. */
        fun isBackupDue(context: Context): Boolean {
            val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val last = sp.getLong(KEY_LAST_BACKUP, 0L)
            val onboarded = context.getSharedPreferences("onboard", Context.MODE_PRIVATE)
                .getBoolean("onboard", false)
            if (!onboarded) return false
            return System.currentTimeMillis() - last > BACKUP_INTERVAL_MS
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface LocalBackupEntryPoint {
    fun localBackupManager(): LocalBackupManager
}

/**
 * Daily backup worker. Always returns success — a failed backup is simply
 * retried by the next day's run (or the catch-up job on next app start).
 */
class LocalBackupWorker(
    private val appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val manager = EntryPointAccessors.fromApplication(
            applicationContext,
            LocalBackupEntryPoint::class.java
        ).localBackupManager()

        when (val result = manager.createBackup()) {
            is LocalBackupResult.Success -> Log.d(
                "LocalBackupWorker", "Daily backup saved to ${result.location}"
            )
            is LocalBackupResult.Failure -> Log.w(
                "LocalBackupWorker", "Daily backup skipped: ${result.message}"
            )
        }
        return Result.success()
    }

    companion object {
        const val PERIODIC_NAME = "daily_local_backup"
        const val CATCH_UP_NAME = "daily_local_backup_catch_up"

        fun isBackupDue(context: Context): Boolean =
            LocalBackupManager.isBackupDue(context)
    }
}
