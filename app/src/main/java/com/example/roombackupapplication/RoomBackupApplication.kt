package com.example.roombackupapplication

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.work.Configuration
import com.example.roombackupapplication.worker.BackupWorkerFactory
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import java.util.concurrent.ExecutionException


class RoomBackupApplication : Application()//, Configuration.Provider
{

    private lateinit var context: Context
    private var driveInstance: Drive?=null
    override fun onCreate() {
        super.onCreate()
        context = applicationContext

       /* CoroutineScope(Dispatchers.Main).launch {
            driveInstance = silentSignInThenSync()
        }
*/
    }

   /* override fun getWorkManagerConfiguration(): Configuration = Configuration.Builder()
        .setWorkerFactory(BackupWorkerFactory(driveInstance!!)).build()
*/

    fun buildGoogleSignInClient(): GoogleSignInClient {
        val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            .build()
        return GoogleSignIn.getClient(context, signInOptions)
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

}
