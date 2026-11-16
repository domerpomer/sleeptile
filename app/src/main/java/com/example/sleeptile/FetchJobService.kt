package com.example.sleeptile

import android.app.job.JobParameters
import android.app.job.JobService

class FetchJobService : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        val app = applicationContext
        Thread {
            try {
                SchoolFetcher.fetch(app)
                SchoolScheduler.tick(app)
            } finally {
                jobFinished(params, false)
            }
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = false
}
