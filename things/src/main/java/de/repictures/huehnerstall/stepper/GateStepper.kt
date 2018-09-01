package de.repictures.huehnerstall.stepper

import de.repictures.huehnerstall.MainActivity
import kotlinx.coroutines.experimental.launch

class GateStepper(private val activity: MainActivity) : Stepper(activity, Stepper.GATE_MODE){

    fun openGate(){
        launch {
            setSleep(false)
            turn(360)
            updateStatus(1)
        }
    }

    fun closeGate(){
        launch {
            setSleep(false)
            turn(360)
            updateStatus(3)
        }
    }
}