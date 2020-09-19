package com.example.roombackupapplication.worker

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.work.CoroutineWorker
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.roombackupapplication.DriveServiceHelper
import com.example.roombackupapplication.MyDb
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*
import java.util.concurrent.ExecutionException

class BackupWorker(private val context: Context, workerParameters: WorkerParameters) : CoroutineWorker(context, workerParameters){

    private lateinit var driveServiceHelper: DriveServiceHelper

    override suspend fun doWork(): Result {

        Log.e("WorkerRunning>>", "Yes <<<")

        val driveNew = silentSignInThenSync()

        driveServiceHelper= DriveServiceHelper(context, driveNew!!)
        val dbFile = context.getDatabasePath(MyDb.dbName)

        driveServiceHelper.createFile(dbFile.path)
            .addOnSuccessListener {
                Toast.makeText(context, "Uploaded successfully!!", Toast.LENGTH_LONG).show()
            }.addOnFailureListener{

                Toast.makeText(context, "Upload Failed!!", Toast.LENGTH_LONG).show()
            }



        return Result.success()

    }

    suspend fun silentSignInThenSync(): Drive? {
        return withContext(Dispatchers.IO){


            val googleSignInClient = buildGoogleSignInClient()
            val task = googleSignInClient.silentSignIn()
            if (task.isSuccessful) {
                try {
                    val googleSignInAccount = task.getResult(
                        ApiException::class.java
                    )
                    val credentials = GoogleAccountCredential.usingOAuth2(
                        context,
                        Collections.singleton(DriveScopes.DRIVE_FILE)
                    )
                    credentials.selectedAccount = googleSignInAccount?.account

                    return@withContext Drive.Builder(
                        AndroidHttp.newCompatibleTransport(),
                        GsonFactory(),
                        credentials
                    )
                        .setApplicationName("DB BackUp").build()

                } catch (e: ApiException) {
                    Log.e("APiExcepApplication>>", "${e.message} <<<")

                }

            } else {
                try {
                    Tasks.await(task)
                    try {
                        val googleSignInAccount = task.getResult(
                            ApiException::class.java
                        )
                        val credentials = GoogleAccountCredential.usingOAuth2(
                            context,
                            Collections.singleton(DriveScopes.DRIVE_FILE)
                        )
                        credentials.selectedAccount = googleSignInAccount?.account

                        return@withContext Drive.Builder(
                            AndroidHttp.newCompatibleTransport(),
                            GsonFactory(),
                            credentials
                        )
                            .setApplicationName("DB BackUp").build()

                    } catch (e: ApiException) {
                        Log.e("APiExcepApplication>>", "${e.message} <<<")

                    }
                } catch (e: ExecutionException) {
                    Log.e("ExectutionException>>", "", e)
                } catch (e: InterruptedException) {
                    Log.e("InterruptedException>>", "", e)
                }
            }
            return@withContext null
        }
    }

    fun buildGoogleSignInClient(): GoogleSignInClient {
        val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            .build()
        return GoogleSignIn.getClient(context, signInOptions)
    }


}