package com.example.perkoren.interfacenavigation

import android.os.Message
import android.support.v4.app.FragmentActivity
import android.view.View
import android.widget.ImageView
import com.nhaarman.mockito_kotlin.*
import org.junit.Test


class MovementHandlerTest {


    @Test
    fun shouldMoveDown() {

        val currentPos: ImageView = mock()
        val nextPosImageView: ImageView = mock()

        val mockFragmentActivity = mock<FragmentActivity>{

            on{findViewById<View>(R.id.imageView1)}.doReturn(currentPos)
            on{findViewById<View>(R.id.imageView4)}.doReturn(nextPosImageView)
        }

        val movementHandler = MovementHandler(mockFragmentActivity)
        val nextMove: CameraFragment.Movement = CameraFragment.Movement.DOWN

        val message:Message = mock()
        message.obj = nextMove

        movementHandler.handleMessage(message)

        verify(currentPos).setImageResource(R.drawable.p1)
        verify(nextPosImageView).setImageResource(R.drawable.p1_pressed)
    }

    @Test
    fun shouldMoveCenter() {

        val imageView: ImageView = mock()

        val mockFragmentActivity = mock<FragmentActivity>{

            on{findViewById<View>(R.id.imageView1)}.doReturn(imageView)

        }

        val movementHandler = MovementHandler(mockFragmentActivity)

        val nextMove: CameraFragment.Movement = CameraFragment.Movement.CENTER
        val message:Message = mock()
        message.obj = nextMove

        movementHandler.handleMessage(message)

        verify(imageView).setImageResource(R.drawable.p1_center)
    }


    @Test
    fun shouldMoveRight() {

        val currentPos: ImageView = mock()
        val nextPosImageView: ImageView = mock()

        val mockFragmentActivity = mock<FragmentActivity>{

            on{findViewById<View>(R.id.imageView1)}.doReturn(currentPos)
            on{findViewById<View>(R.id.imageView2)}.doReturn(nextPosImageView)
        }

        val movementHandler = MovementHandler(mockFragmentActivity)

        val nextMove: CameraFragment.Movement = CameraFragment.Movement.RIGHT
        val message:Message = mock()
        message.obj = nextMove

        movementHandler.handleMessage(message)

        verify(currentPos).setImageResource(R.drawable.p1)
        verify(nextPosImageView).setImageResource(R.drawable.p1_pressed)
    }
}
