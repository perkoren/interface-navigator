package com.example.perkoren.interfacenavigation

/*
Licensed under the Apache License, Version 2.0 (the "License");
 */

import android.os.Bundle
import android.support.v7.app.AppCompatActivity


class MainActivity : AppCompatActivity(){


    override fun onCreate(savedInstanceState: Bundle?) {

            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_main)
            savedInstanceState ?: supportFragmentManager.beginTransaction()
                    .add(R.id.fragment,CameraFragment())
                    .commit()
    }


}
