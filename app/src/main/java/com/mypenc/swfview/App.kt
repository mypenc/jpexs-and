package com.mypenc.swfview

import android.app.Application

class App:Application(){
    override fun onCreate(){
        super.onCreate()
        System.setProperty("user.home",filesDir.path) //ffdec config dir
        System.setProperty("java.awt.headless","true")
    }
}
