package com.example.roombackupapplication

import android.content.Context
import android.os.Environment
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.api.client.http.FileContent
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import java.io.*
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

interface OnFileDownaloadedListener {
    fun onFileDownloaded()
}

class DriveServiceHelper(
    private val context: Context,
    private val driveService: Drive,
    private val listerner: OnFileDownaloadedListener?
) {

    companion object {
        const val PREF_NAME = "myPref"
        const val PREF_DRIVE_FILE_ID = "fileId"
    }

    private val executor = Executors.newSingleThreadExecutor()

    fun uploadBackupFileToDrive(): Task<String> {

        return Tasks.call(executor, object : Callable<String> {
            override fun call(): String {

                val fileMetadata = File()
                fileMetadata.name = MyDb.dbName

                val pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                val filePath=pref.getString(MainActivity.BACKUP_DB_FILE_PATH, null)

                val fileToUpload = java.io.File(filePath!!)

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
                    Log.e("DriveOrNullException>>", "${e.message} <<<")
                }

                if (myFile == null)
                    throw IOException("Null file received on Creation")

                return myFile.id

            }

        })

    }

    fun getBackedUpData():Task<String> {
        return Tasks.call(executor, {
            val pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

            try {

                val outputStream = ByteArrayOutputStream()

                val listReq = driveService.files().list()
                //                    listReq.setQ("mimeType='image/jpeg'")
                listReq.setQ("name='user_db'")

                val qList = listReq.execute()
                if (qList.size > 0) {
                    Log.e(
                        "QList>>",
                        "${qList.size}, ${qList[0].toString()}, ${qList.files.size} <<<"
                    )

                    val idFileFromDrive = qList.files[0].id
                    driveService.files().get(idFileFromDrive).executeAndDownloadTo(outputStream)


                    //val fileOut = FileOutputStream(context.getDatabasePath(MyDb.dbName))
                    //outputStream.writeTo(fileOut)

                    val fileInDbFolder =File(
                        Environment.getExternalStorageDirectory().absolutePath,
                        MainActivity.BACKUP_FILE_NAME
                    )

                    //java.io.File(context.getDatabasePath(MyDb.dbName).toURI())
                    if (fileInDbFolder.exists()) {
                        Log.e("Deleting>>", "Yes<<<")
                        fileInDbFolder.delete()
                    }
                    fileInDbFolder.createNewFile()
                    Log.e("NewFileExisis>>", "${fileInDbFolder.exists()} <<")

                    val fOut = FileOutputStream(fileInDbFolder)
                    outputStream.writeTo(fOut)

                    outputStream.flush()
                    outputStream.close()

                    // fileOut.flush()
                    //fileOut.close()

                    fOut.flush()
                    fOut.close()



                    if (listerner != null)
                        listerner.onFileDownloaded()


                }


                //driveService.files().get(prevId).executeAndDownloadTo(outputStream)

            } catch (e: Exception) {
                Log.e("DriveExceptionTwo>>", "${e.message} ${e.cause}<<<")
            }
            pref.getString(MainActivity.BACKUP_DB_FILE_PATH, null)!!
        })
    }
    private val BUFFER = 80000

    fun zip(_files: ArrayList<String>, zipFileName: String): String? {
        try {
            var origin: BufferedInputStream? = null
            val dest = FileOutputStream(zipFileName)
            val out = ZipOutputStream(
                BufferedOutputStream(
                    dest
                )
            )
            val data = ByteArray(BUFFER)
            for (i in _files.indices) {
                Log.e("Compress", "Adding: " + _files[i])
                val fi = FileInputStream(_files[i])
                origin = BufferedInputStream(fi, BUFFER)
                val entry = ZipEntry(_files[i].substring(_files[i].lastIndexOf("/") + 1))
                out.putNextEntry(entry)
                var count: Int
                while (origin.read(data, 0, BUFFER).also { count = it } != -1) {
                    out.write(data, 0, count)
                }
                origin.close()

                return zipFileName
            }
            out.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }


}