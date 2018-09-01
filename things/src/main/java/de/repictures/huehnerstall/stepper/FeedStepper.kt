package de.repictures.huehnerstall.stepper

import android.util.Log
import de.repictures.huehnerstall.MainActivity
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import java.util.concurrent.TimeUnit

class FeederStepper(private val activity: MainActivity) : Stepper(activity, Stepper.FEEDER_MODE){

    fun feed(){
        if (!isCurrentlyWorking) {
            launch {
                setSleep(false)
                turn(180)
                delay(10, TimeUnit.SECONDS)
                turn(-180)
                setSleep(true)
                updateStatus(1)
            }
        } else {
            Log.e(TAG, "Futteranlage arbeitet bereits")
        }
    }

}