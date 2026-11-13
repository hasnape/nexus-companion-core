package fr.nexuscompanion.android

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import java.util.concurrent.Executors
import java.util.concurrent.Future

/** Bounded local consolidation while charging. No network, recording, or original-note writes. */
class LocalLearningJobService : JobService() {
    companion object {
        private const val JOB_ID = 423
        fun enabled(context: Context) = context.getSharedPreferences("nexus_learning_preferences", Context.MODE_PRIVATE)
            .getBoolean("local_consolidation_enabled", true)
        fun schedule(context: Context) {
            val scheduler = context.getSystemService(JobScheduler::class.java)
            if (!enabled(context)) { scheduler.cancel(JOB_ID); return }
            if (scheduler.getPendingJob(JOB_ID) != null) return
            scheduler.schedule(JobInfo.Builder(JOB_ID, ComponentName(context, LocalLearningJobService::class.java))
                .setRequiresCharging(true).setRequiresBatteryNotLow(true).setRequiresStorageNotLow(true)
                .setPeriodic(15 * 60_000L).build())
        }
    }
    private val worker = Executors.newSingleThreadExecutor()
    private var task: Future<*>? = null
    @Volatile private var stopped = false
    override fun onStartJob(params: JobParameters): Boolean {
        if (!enabled(this)) return false
        stopped = false
        task = worker.submit {
            try {
                NativeBrainService(applicationContext, LocalKnowledgeLibrary(applicationContext)).use { brain ->
                    brain.consolidate()
                }
            } catch (_: Exception) { /* Rebuildable cache; never reset the primary library. */ }
            if (!stopped) jobFinished(params, false)
        }
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean { stopped = true; task?.cancel(true); return enabled(this) }
    override fun onDestroy() { stopped = true; task?.cancel(true); worker.shutdownNow(); super.onDestroy() }
}
