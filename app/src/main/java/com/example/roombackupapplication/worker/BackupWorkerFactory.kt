package com.example.roombackupapplication.worker

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.google.api.services.drive.Drive

class BackupWorkerFactory(private val drive: Drive): WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? {

        return when(workerClassName){
            BackupWorker::class.java.name-> BackupWorker(appContext, workerParameters)//, drive)
            else->null
        }

    }
}