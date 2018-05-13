package de.repictures.huehnerstall

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import com.google.android.things.pio.Gpio
import com.google.android.things.pio.PeripheralManager
import com.google.firebase.database.*
import com.google.firebase.iid.FirebaseInstanceId
import de.repictures.huehnerstall.pojo.Time

private val TAG = MainActivity::class.java.simpleName

class MainActivity : Activity() {

    private var status = 0
    private lateinit var openingTimeRef: DatabaseReference
    private lateinit var closingTimeRef: DatabaseReference
    private lateinit var phoneDeviceTokenRef : DatabaseReference
    private lateinit var thingsDeviceTokenRef : DatabaseReference
    private var time: Time? = null
    private var phoneDeviceToken = ""
    private lateinit var statusText : TextView

    private val service = PeripheralManager.getInstance()
    private lateinit var directionPin : Gpio
    private lateinit var stepPin : Gpio

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.text_view)

        // Write a message to the database
        val database = FirebaseDatabase.getInstance()
        openingTimeRef = database.getReference("opening_time")
        closingTimeRef = database.getReference("closing_time")
        val openRef = database.getReference("open")
        phoneDeviceTokenRef = database.getReference("phone_device_token")
        thingsDeviceTokenRef = database.getReference("things_device_token")

        openingTimeRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                if (dataSnapshot.value != null) {
                    time = dataSnapshot.getValue(Time::class.java)
                }
            }

            override fun onCancelled(error: DatabaseError) {

            }
        })

        closingTimeRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                if (dataSnapshot.value != null) {
                    time = dataSnapshot.getValue(Time::class.java)
                }
            }

            override fun onCancelled(error: DatabaseError) {

            }
        })

        openRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                if (dataSnapshot.value != null) {
                    status = dataSnapshot.getValue(Int::class.java)!!
                    Log.d(TAG, "Status = $status")
                    statusText.text = status.toString()
                    if (status == 5 || status == 6){
                        openRef.setValue(1)
                        status = 1
                        onDataChange(dataSnapshot)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, error.toString())
            }
        })
        directionPin = service.openGpio("BCM26")
        directionPin.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW)
        stepPin = service.openGpio("BCM20")
        stepPin.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW)

        directionPin.value = true
        stepPin.value = true
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    private fun initializeGpioHigh(gpio : Gpio){
        gpio.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW)
        gpio.setActiveType(Gpio.ACTIVE_HIGH)
    }

    private fun initializeGpioLow(gpio : Gpio){
        gpio.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW)
        gpio.setActiveType(Gpio.ACTIVE_HIGH)
    }
}