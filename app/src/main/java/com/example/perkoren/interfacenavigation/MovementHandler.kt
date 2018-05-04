package com.example.perkoren.interfacenavigation

import android.os.Handler
import android.os.Message
import android.support.v4.app.FragmentActivity
import android.util.ArrayMap
import android.util.Log
import android.view.View
import android.widget.ImageView
import java.util.*

class MovementHandler : Handler {

    private val activity: FragmentActivity
    private var currentPlacement: Int = R.id.imageView1

    companion object {
        private val TAG = "InterfaceNavigation"
        private val ALLOWED_MOVES: ArrayMap<Int, Set<NextPosition>> = ArrayMap()

        init {
            ALLOWED_MOVES[R.id.imageView1] = HashSet<NextPosition>(Arrays.asList(NextPosition(R.id.imageView2, CameraFragment.Movement.RIGHT),
                    NextPosition(R.id.imageView4, CameraFragment.Movement.DOWN)))
            ALLOWED_MOVES[R.id.imageView2] = HashSet<NextPosition>(Arrays.asList(NextPosition(R.id.imageView1, CameraFragment.Movement.LEFT),
                    NextPosition(R.id.imageView5, CameraFragment.Movement.DOWN),
                    NextPosition(R.id.imageView3, CameraFragment.Movement.RIGHT)))
            ALLOWED_MOVES[R.id.imageView3] = HashSet<NextPosition>(Arrays.asList(NextPosition(R.id.imageView2, CameraFragment.Movement.LEFT),
                    NextPosition(R.id.imageView6, CameraFragment.Movement.DOWN)))
            ALLOWED_MOVES[R.id.imageView4] = HashSet<NextPosition>(Arrays.asList(NextPosition(R.id.imageView1, CameraFragment.Movement.UP),
                    NextPosition(R.id.imageView5, CameraFragment.Movement.RIGHT),
                    NextPosition(R.id.imageView7, CameraFragment.Movement.DOWN)))
            ALLOWED_MOVES[R.id.imageView5] = HashSet<NextPosition>(Arrays.asList(NextPosition(R.id.imageView2, CameraFragment.Movement.UP),
                    NextPosition(R.id.imageView6, CameraFragment.Movement.RIGHT),
                    NextPosition(R.id.imageView8, CameraFragment.Movement.DOWN),
                    NextPosition(R.id.imageView4, CameraFragment.Movement.LEFT)))
            ALLOWED_MOVES[R.id.imageView6] = HashSet<NextPosition>(Arrays.asList(NextPosition(R.id.imageView3, CameraFragment.Movement.UP),
                    NextPosition(R.id.imageView9, CameraFragment.Movement.DOWN),
                    NextPosition(R.id.imageView5, CameraFragment.Movement.LEFT)))
            ALLOWED_MOVES[R.id.imageView7] = HashSet<NextPosition>(Arrays.asList(NextPosition(R.id.imageView4, CameraFragment.Movement.UP),
                    NextPosition(R.id.imageView8, CameraFragment.Movement.RIGHT)))
            ALLOWED_MOVES[R.id.imageView8] = HashSet<NextPosition>(Arrays.asList(NextPosition(R.id.imageView5, CameraFragment.Movement.UP),
                    NextPosition(R.id.imageView7, CameraFragment.Movement.LEFT),
                    NextPosition(R.id.imageView9, CameraFragment.Movement.RIGHT)))
            ALLOWED_MOVES[R.id.imageView9] = HashSet<NextPosition>(Arrays.asList(NextPosition(R.id.imageView6, CameraFragment.Movement.UP),
                    NextPosition(R.id.imageView8, CameraFragment.Movement.LEFT)))
        }
    }

    constructor(act:FragmentActivity): super(){
        this.activity = act
    }

    override fun handleMessage(message: Message){
        if(activity.findViewById<View>(currentPlacement) == null) {
            Log.i(TAG,"No view with id $currentPlacement is found. R.id.imageView1: ${R.id.imageView1}")
            return
        }

        val nextMove: CameraFragment.Movement = message.obj as CameraFragment.Movement

        if(nextMove == CameraFragment.Movement.CENTER){
            val currentImageView = activity.findViewById<View>(currentPlacement) as ImageView
            currentImageView.setImageResource(R.drawable.p1_center)
            return
        }
        val nextPosition = ALLOWED_MOVES[currentPlacement]!!.find { it.movement == nextMove }?.position?:currentPlacement

        val currentImageView = activity.findViewById<View>(currentPlacement) as ImageView
        currentImageView.setImageResource(R.drawable.p1)
        val nextImageView = activity.findViewById<View>(nextPosition) as ImageView
        nextImageView.setImageResource(R.drawable.p1_pressed)
        currentPlacement = nextPosition
    }
}