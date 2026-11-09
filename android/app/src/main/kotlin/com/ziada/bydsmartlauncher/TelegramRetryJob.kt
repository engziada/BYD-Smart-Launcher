package com.ziada.bydsmartlauncher

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import java.util.concurrent.Executors

/** Network-constrained delivery of previously approved outbox entries. */
class TelegramRetryJob : JobService() {
    private val executor = Executors.newSingleThreadExecutor()
    @Volatile private var running: JobParameters? = null

    override fun onStartJob(params: JobParameters): Boolean {
        running = params
        executor.execute {
            val retry = TelegramBotService.shared(this).retryAutomatically { running === params }
            mainExecutor.execute {
                if (running === params) {
                    running = null
                    jobFinished(params, retry)
                }
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        if (running === params) running = null
        return true
    }

    override fun onDestroy() {
        running = null
        executor.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val JOB_ID = 202601

        internal fun sync(context: Context, enabled: Boolean): Boolean = runCatching {
            val scheduler = context.getSystemService(JobScheduler::class.java) ?: return false
            if (!enabled) {
                scheduler.cancel(JOB_ID)
                return true
            }
            if (scheduler.getPendingJob(JOB_ID) != null) return true
            val job = JobInfo.Builder(JOB_ID, ComponentName(context, TelegramRetryJob::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setBackoffCriteria(15_000, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                .setPersisted(true)
                .build()
            scheduler.schedule(job) == JobScheduler.RESULT_SUCCESS
        }.getOrDefault(false)
    }
}
