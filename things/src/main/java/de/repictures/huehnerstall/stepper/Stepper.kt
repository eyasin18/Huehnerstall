package de.repictures.huehnerstall.stepper

import android.os.Handler
import com.google.android.things.pio.Gpio
import com.google.android.things.pio.GpioCallback
import com.google.android.things.pio.PeripheralManager
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import de.repictures.huehnerstall.MainActivity
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.runBlocking
import java.util.concurrent.TimeUnit
import kotlin.math.abs

interface StatusChangedListener{
    fun onStatusChanged(stepperMode : Int, status : Long)
}

open class Stepper(private val activity: MainActivity, private val mode: Int){
    companion object {
        const val GATE_MODE = 1
        const val FEEDER_MODE = 2
    }

    var status : Long = 0
    var isCurrentlyWorking : Boolean = false

    private val service = PeripheralManager.getInstance()
    val TAG = Stepper::class.java.simpleName!!
    private var buttonHandler : Handler = Handler()

    private lateinit var statusListener: StatusChangedListener

    private lateinit var directionPin : Gpio
    private lateinit var stepPin : Gpio
    private lateinit var sleepPin : Gpio
    private lateinit var buttonPin : Gpio

    private lateinit var statusRef: DatabaseReference

    fun registerStepper(directionPinName : String, stepPinName : String, sleepPinName : String){
        directionPin = service.openGpio(directionPinName)
        directionPin.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW)
        stepPin = service.openGpio(stepPinName)
        stepPin.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW)
        sleepPin = service.openGpio(sleepPinName)
        sleepPin.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW)
    }

    fun registerDatabase(statusRef: DatabaseReference){
        this.statusRef = statusRef
        this.statusRef.addValueEventListener(object : ValueEventListener {
            override fun onCancelled(p0: DatabaseError) {}

            override fun onDataChange(dataSnapshot: DataSnapshot) {
                if (dataSnapshot.value != null && dataSnapshot.value is Long && dataSnapshot.value as Long != status){
                    status = dataSnapshot.value as Long
                    activity.runOnUiThread {
                        statusListener.onStatusChanged(mode, status)
                    }
                }
            }
        })
    }

    fun registerButton(buttonPinName: String){
        buttonPin = service.openGpio(buttonPinName)
        buttonPin.setDirection(Gpio.DIRECTION_IN)
        buttonPin.setEdgeTriggerType(Gpio.EDGE_FALLING)
        buttonPin.registerGpioCallback(buttonHandler, gpioCallback)
    }

    fun updateStatus(newStatus : Long){
        status = newStatus
        if (this::statusRef.isInitialized){
            statusRef.setValue(status)
        }
        activity.runOnUiThread {
            statusListener.onStatusChanged(mode, status)
        }
    }

    private val gpioCallback = GpioCallback {
        if (isCurrentlyWorking) return@GpioCallback true
        if (mode == GATE_MODE){
            if (status != 1L && status != 2L){
                updateStatus(2)
            } else if (status != 3L && status != 4L){
                updateStatus(4)
            }
        } else if(mode == FEEDER_MODE) {
            if (status == 2L){
                updateStatus(1)
            } else if (status == 1L || status == 3L){
                updateStatus(2)
            }
        }
        true
    }

    suspend fun turn(degrees: Int){
        isCurrentlyWorking = true
        var factor = 1.8
        if (mode == GATE_MODE) factor = 1.8/2.5
        directionPin.value = degrees >= 0
        val iterations = (abs(degrees)/factor).toInt()
        runBlocking {
            for (i in 1..iterations){
                stepPin.value = true
                delay(10, TimeUnit.NANOSECONDS)
                stepPin.value = false
                delay(10, TimeUnit.NANOSECONDS)
            }
        }
        isCurrentlyWorking = false
    }

    fun setSleep(sleep: Boolean){
        directionPin.value = !sleep
    }
}