package com.example.roombackupapplication

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class User(@PrimaryKey(autoGenerate = true)val id:Int, val name : String, val password:String)