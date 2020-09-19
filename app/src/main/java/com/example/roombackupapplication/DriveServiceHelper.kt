package com.example.roombackupapplication

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.api.client.http.FileContent
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.Callable
import java.util.concurrent.Executors

class DriveServiceHelper(private val context: Context, private val driveService: Drive) {

    companion object {
        const val PREF_NAME = "myPref"
        const val PREF_DRIVE_FILE_ID = "fileId"
    }

    private val executor = Executors.newSingleThreadExecutor()

    fun createFile(filePath: String): Task<String> {

        return Tasks.call(executor, object : Callable<String> {
            override fun call(): String {

                val fileMetadata = File()
                fileMetadata.name = MyDb.dbName

                val fileToUpload = java.io.File(filePath)

                val mediaContent = FileContent("*/*", fileToUpload)

                var myFile = File()
                try {
                    val pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

                    val prevId = pref.getString(PREF_DRIVE_FILE_ID, null)
                    Log.e("PrevId>>", "$prevId <<<")
                    if (prevId != null)
                        driveService.files().delete(prevId).execute()

                    myFile = driveService.files().create(fileMetadata, mediaContent).execute()
                    val fileId = myFile.id
                    val editor = pref.edit()
                    editor.putString(PREF_DRIVE_FILE_ID, fileId)
                    editor.commit()
                } catch (e: Exception) {
                    Log.e("DriveException>>", "${e.message} <<<")
                }

                if (myFile == null)
                    throw IOException("Null file received on Creation")

                return myFile.id

            }

        })

    }

    fun getBakedUpData() {
        Tasks.call(executor, object : Callable<Unit> {
            override fun call() {

                try {
                    val pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

                    val prevId = pref.getString(PREF_DRIVE_FILE_ID, null)
                    val outputStream = ByteArrayOutputStream()
                    driveService.files().get(prevId).executeAndDownloadTo(outputStream)

                    val fileOut = FileOutputStream(MyDb.dbName)
                    outputStream.writeTo(fileOut)

                    val fileInDbFolder = java.io.File(context.getDatabasePath(MyDb.dbName).toURI())
                    if (fileInDbFolder.exists())
                        fileInDbFolder.delete()
                    fileInDbFolder.createNewFile()

                    val fOut = FileOutputStream(fileInDbFolder)
                    outputStream.writeTo(fOut)

                    outputStream.flush()
                    outputStream.close()

                    fileOut.flush()
                    fileOut.close()

                    fOut.flush()
                    fOut.close()

                } catch (e: Exception) {
                    Log.e("DriveException>>", "${e.message} <<<")
                }
            }

        })


    }

}