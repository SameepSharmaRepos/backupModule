package com.example.roombackupapplication

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import androidx.work.*
import com.example.roombackupapplication.worker.BackupWorker
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.*
import java.util.concurrent.TimeUnit


class MainActivity : AppCompatActivity(), View.OnClickListener, OnFileDownaloadedListener {

    companion object {
        const val WORKER_TAG = "BackupWorker"
        const val SIGNEDIN = "signedIn"
        const val BACKUP_FILE_NAME = "MyBackup"
        const val SQLITE_TABLE_NAME = "data_backup"
        const val BACKUP_DB_FILE_PATH = "data_backup"
    }

    private lateinit var myDb: MyDb
    private var nameFromDb: String? = null


    //SharedPref
    private lateinit var pref: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor


    //backup
    val BACKUP_REQUEST_CODE = 1001
    private lateinit var driveServiceHelper: DriveServiceHelper
    private lateinit var workManager: WorkManager
    private var isSignedInToGoogle = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        verifyStoragePermissions(this)
        pref = getSharedPreferences(DriveServiceHelper.PREF_NAME, Context.MODE_PRIVATE)
        editor = pref.edit()
        isSignedInToGoogle = pref.getBoolean(SIGNEDIN, false)
        Log.e("ISSIGNEDIN>>", "$isSignedInToGoogle <<<")
        setUpViews()
        if (!isSignedInToGoogle)
            requestSignIn()
//        myDb = MyDb.getDatabase(this, CoroutineScope(Dispatchers.IO))
        //getDataFromDb()
        btnSave.setOnClickListener(this)

    }

    private fun setUpViews() {

        if (!isSignedInToGoogle) {
            tv_sign_in.visibility = View.VISIBLE
            tv_sign_in.setOnClickListener(this)
        } else
            tv_sign_in.visibility = View.GONE

        ArrayAdapter.createFromResource(
            this,
            R.array.backup_options,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            // Specify the layout to use when the list of choices appears
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            // Apply the adapter to the spinner
            spinner_backup_frequency.adapter = adapter
        }

    }

    private fun requestSignIn() {


        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            .build()

        val client = GoogleSignIn.getClient(this, gso)

        startActivityForResult(client.signInIntent, 1001)

    }

    private fun getDataFromDb() {

        lifecycleScope.launch(Dispatchers.IO) {
            myDb = MyDb.getDatabase(this@MainActivity, CoroutineScope(Dispatchers.IO))
            myDb.userDao().getUser().forEach { user ->
                nameFromDb = user.name
                Log.e("NmaeFromDb>>", "$nameFromDb <<<")
                MyDb.closeDataBase()
            }

            withContext(Dispatchers.Main) {
                if (nameFromDb != null)
                    etName.setText(nameFromDb)
                else
                    Toast.makeText(this@MainActivity, "Name is Null!!", Toast.LENGTH_LONG).show()
            }
        }

    }

    fun verifyStoragePermissions(activity: Activity?) {
        // Check if we have write permission
        val permission = ActivityCompat.checkSelfPermission(
            activity!!,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        val permissionRead = ActivityCompat.checkSelfPermission(
            activity!!,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
        if (permission != PackageManager.PERMISSION_GRANTED && permissionRead != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ),
                1002
            )
        } else
            Toast.makeText(this, "Permission Granted!!", Toast.LENGTH_LONG).show()
    }

    override fun onClick(p0: View?) {

        when (p0!!.id) {
            R.id.btnSave -> {

                val name = etName.text.toString()
                val pass = etPass.text.toString()

                if (name.isNotEmpty() && pass.isNotEmpty()) {


                    val backupDbFile = File(
                        Environment.getExternalStorageDirectory().absolutePath,
                        BACKUP_FILE_NAME
                    )
                    if (!backupDbFile.exists())
                        backupDbFile.createNewFile()


                    lifecycleScope.launch(Dispatchers.IO) {


                        val sqLiteDb = SQLiteDatabase.openOrCreateDatabase(backupDbFile, null)
                        sqLiteDb.disableWriteAheadLogging()

                        sqLiteDb.execSQL("CREATE TABLE IF NOT EXISTS $SQLITE_TABLE_NAME (id INT, name TEXT, password TEXT);")


                        myDb = MyDb.getDatabase(this@MainActivity, CoroutineScope(Dispatchers.IO))
                        myDb.userDao()
                            .insertUser(User(0, etName.text.toString(), etPass.text.toString()))

                        val dataFromDb = myDb.userDao().getUser()

                        dataFromDb.forEach { user ->
                            sqLiteDb.execSQL("INSERT INTO $SQLITE_TABLE_NAME VALUES ('${user.id}', '${user.name}', '${user.password}');")
                        }
                        sqLiteDb.rawQuery("PRAGMA journal_mode=DELETE;", null)
                        sqLiteDb.close()
                        editor.putString(BACKUP_DB_FILE_PATH, backupDbFile.absolutePath)
                        editor.commit()

                        //Log.e("NameInDB>>", "${myDb.userDao().getUser().name} <<")
                        withContext(Dispatchers.Main)
                        {
                            Toast.makeText(this@MainActivity, "Data Saved!!", Toast.LENGTH_LONG)
                                .show()
                            //onLocalBackupRequested()
                        }
                        Log.e("SelectedItem>>", "${spinner_backup_frequency.selectedItem} <<<")

                        val constraints =
                            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED)
                                .build()

                        val recurringTask = when (spinner_backup_frequency.selectedItem) {
                            "Daily" -> {

                                Log.e("Daily!5Min>>", "Yes<<")

                                PeriodicWorkRequest.Builder(
                                    BackupWorker::class.java,
                                    15,
                                    TimeUnit.MINUTES
                                )
                                    .addTag(WORKER_TAG)
                                    .setConstraints(constraints)
                                    .setBackoffCriteria(
                                        BackoffPolicy.LINEAR,
                                        PeriodicWorkRequest.MIN_BACKOFF_MILLIS,
                                        TimeUnit.MILLISECONDS
                                    )
                                    .build()

                            }
                            "Weekly" -> PeriodicWorkRequest.Builder(
                                BackupWorker::class.java,
                                7,
                                TimeUnit.DAYS
                            )
                                .addTag(WORKER_TAG)
                                .setConstraints(constraints)
                                .setBackoffCriteria(
                                    BackoffPolicy.LINEAR,
                                    PeriodicWorkRequest.MIN_BACKOFF_MILLIS,
                                    TimeUnit.MILLISECONDS
                                )
                                .build()
                            else -> PeriodicWorkRequest.Builder(
                                BackupWorker::class.java,
                                30,
                                TimeUnit.DAYS
                            )
                                .addTag(WORKER_TAG)
                                .setConstraints(constraints)
                                .setBackoffCriteria(
                                    BackoffPolicy.LINEAR,
                                    PeriodicWorkRequest.MIN_BACKOFF_MILLIS,
                                    TimeUnit.MILLISECONDS
                                )
                                .build()


                        }
                        workManager = WorkManager.getInstance(this@MainActivity)
                        workManager.enqueue(recurringTask)


                    }
                } else
                    Toast.makeText(this, "Enter valid Name & Password!!", Toast.LENGTH_LONG).show()


            }

            R.id.tv_sign_in -> requestSignIn()

        }
    }


    fun onLocalBackupRequested() {


        /* val dbFile = activity.getDatabasePath("user_db")

         driveServiceHelper.createFile(dbFile.path)
             .addOnSuccessListener {
                 progressDialog.dismiss()
             Toast.makeText(this, "Uploaded successfully!!", Toast.LENGTH_LONG).show()
             }.addOnFailureListener{
                 progressDialog.dismiss()
                 Toast.makeText(this, "Upload Failed!!", Toast.LENGTH_LONG).show()
             }*/
    }


    // replace this with your db name
    //val mimeTypes = arrayOf("*/*")
    /* val fn = "MyBackup"
     val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
         .addCategory(Intent.CATEGORY_OPENABLE)
         .putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
         .putExtra(
             Intent.EXTRA_TITLE, fn
         )
     activity.startActivityForResult(intent, BACKUP_REQUEST_CODE)*/

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultIntent: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultIntent)
        /*if (resultCode == Activity.RESULT_OK && resultIntent != null && requestCode == BACKUP_REQUEST_CODE) {
            lifecycleScope.launch {
                val uri = resultIntent.data!!
                val pfd = contentResolver.openFileDescriptor(uri, "w")
                pfd?.use {
                    FileOutputStream(pfd.fileDescriptor).use { outputStream ->
                        val file = packZipFileForBackup(this@MainActivity)
                        try {
                            file?.inputStream()?.use { input ->
                                input.copyTo(outputStream)
                            }

                        } finally {
                            if (file?.exists() == true) {
                                file.delete()
                            }
                        }
                    }
                }
            }
        } else
        */    if (resultCode == Activity.RESULT_OK && resultIntent != null && requestCode == 1001) {
            handleSignInIntent(resultIntent)
        } else if (resultCode == Activity.RESULT_OK && requestCode == 1002) {
            //handleSignInIntent(resultIntent)
            Toast.makeText(this, "PermissionFranted!!", Toast.LENGTH_LONG).show()
        }

    }

    private fun handleSignInIntent(resultIntent: Intent) {

        GoogleSignIn.getSignedInAccountFromIntent(resultIntent)
            .addOnSuccessListener {

                tv_sign_in.visibility = View.GONE

                editor.putBoolean(SIGNEDIN, true)
                editor.commit()

                Toast.makeText(this, "Sign-in Successful!!", Toast.LENGTH_LONG).show()

                val credentials = GoogleAccountCredential.usingOAuth2(
                    this,
                    Collections.singleton(DriveScopes.DRIVE_FILE)
                )
                credentials.selectedAccount = it.account
                val drive =
                    Drive.Builder(AndroidHttp.newCompatibleTransport(), GsonFactory(), credentials)
                        .setApplicationName("DB BackUp").build()

                driveServiceHelper = DriveServiceHelper(this, drive, this)

                driveServiceHelper.getBackedUpData().addOnSuccessListener {

                }

            }
            .addOnFailureListener {
                Toast.makeText(this, "Sign-in Failed!!", Toast.LENGTH_LONG).show()
            }

    }

    override fun onFileDownloaded() {

        Log.e("FileDownaloaded>>", "Yes<<<")
        CoroutineScope(Dispatchers.IO).launch {
            val dbSqLite = SQLiteDatabase.openDatabase(
                    Environment.getExternalStorageDirectory().absolutePath+File.separator+
                    MainActivity.BACKUP_FILE_NAME,
                null,
                SQLiteDatabase.CREATE_IF_NECESSARY
            )
            dbSqLite.disableWriteAheadLogging()
            val cursor = dbSqLite.rawQuery("SELECT * FROM $SQLITE_TABLE_NAME", null)
            if (cursor.moveToFirst()) {
                do {
                    Log.e("NAme>>", "${cursor.getString(1)} <<<")
                    myDb.userDao().insertUser(
                        User(
                            cursor.getInt(0),
                            cursor.getString(1),
                            cursor.getString(2)
                        )
                    )
                } while (cursor.moveToNext())
            }
            cursor.close()
            dbSqLite.close()

            val name = myDb.userDao().getUser()[0].name
            Log.e("NameFromBackup>>", "$name <<<")
            withContext(Dispatchers.Main){
                etName.setText(name)
            }

        }


        //getDataFromDb()


    }
}

/*
    suspend fun packZipFileForBackup(context: Context): File? {
        val listOfFolders = mutableListOf<File>()
        return withContext(Dispatchers.IO) {
            listOfFolders.add(dbFile)

            val dbParentDirectory = dbFile.parentFile
            listOfFolders.add(dbParentDirectory)

            val dataDir = context.filesDir.parentFile
            if (dataDir != null) {
                val sharedPrefDirectoryPath = dataDir.absolutePath + "/shared_prefs"
                listOfFolders.add(File(sharedPrefDirectoryPath))
                1
            }
            return@withContext dbFile
        }
    }

}*/
