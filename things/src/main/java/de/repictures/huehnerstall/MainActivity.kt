package de.repictures.huehnerstall

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.media.ImageReader
import android.os.AsyncTask
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import com.google.android.things.pio.Gpio
import com.google.android.things.pio.PeripheralManager
import com.google.firebase.database.*
import de.repictures.huehnerstall.pojo.Time
import java.util.*
import android.graphics.BitmapFactory
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.android.synthetic.main.activity_main.*
import com.google.firebase.firestore.FirebaseFirestoreSettings



private val TAG = MainActivity::class.java.simpleName

class MainActivity : Activity() {

    private var status = 0
    private lateinit var openingTimeRef: DatabaseReference
    private lateinit var closingTimeRef: DatabaseReference
    private lateinit var phoneDeviceTokenRef : DatabaseReference
    private lateinit var thingsDeviceTokenRef : DatabaseReference
    private lateinit var cameraImageRef : DatabaseReference
    private lateinit var takePictureRef: DatabaseReference

    private lateinit var openRef : DatabaseReference

    private lateinit var mCamera: CameraHelper
    private lateinit var mCameraThread: HandlerThread

    private val service = PeripheralManager.getInstance()
    private lateinit var directionPin : Gpio
    private lateinit var stepPin : Gpio

    private var closeRunnable: Runnable = Runnable {  }
    private var openRunnable: Runnable = Runnable {  }
    private var closeHandler: Handler = Handler()
    private var openHandler: Handler = Handler()

    private var isCurrentlyWorking = false

    private lateinit var firestoreDatabase : FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // We need permission to access the camera
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            // A problem occurred auto-granting the permission
            Log.e(TAG, "No permission")

        }

        // Write a message to the instantDatabase
        firestoreDatabase = FirebaseFirestore.getInstance()
        val settings = FirebaseFirestoreSettings.Builder()
                .setTimestampsInSnapshotsEnabled(true)
                .build()
        firestoreDatabase.setFirestoreSettings(settings)
        val instantDatabase = FirebaseDatabase.getInstance()
        openingTimeRef = instantDatabase.getReference("opening_time")
        closingTimeRef = instantDatabase.getReference("closing_time")
        openRef = instantDatabase.getReference("open")
        phoneDeviceTokenRef = instantDatabase.getReference("phone_device_token")
        thingsDeviceTokenRef = instantDatabase.getReference("things_device_token")
        takePictureRef = instantDatabase.getReference("take_picture")

        openingTimeRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                if (dataSnapshot.value != null) {
                    val time = dataSnapshot.getValue(Time::class.java)!!
                    setOpeningTime(time)
                }
            }

            override fun onCancelled(error: DatabaseError) {

            }
        })

        closingTimeRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                if (dataSnapshot.value != null) {
                    val time = dataSnapshot.getValue(Time::class.java)!!
                    setClosingTime(time)
                }
            }

            override fun onCancelled(error: DatabaseError) {

            }
        })

        openRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                if (dataSnapshot.value != null) {
                    val newStatus = dataSnapshot.getValue(Int::class.java)!!
                    Log.d(TAG, "Status = $newStatus")
                    if (newStatus == 5 || newStatus == 6) {
                        openRef.setValue(status)
                    } else if (newStatus == 2) {
                        status = newStatus
                        openCloseGate(true)
                    } else if (newStatus == 4) {
                        status = newStatus
                        openCloseGate(false)
                    } else {
                        status = newStatus
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, error.toString())
            }
        })

        takePictureRef.addValueEventListener(object : ValueEventListener{
            override fun onCancelled(error: DatabaseError) {

            }

            override fun onDataChange(databaseSnapshot: DataSnapshot) {
                if (databaseSnapshot.value is Boolean && databaseSnapshot.value == true){
                    mCamera.takePicture()
                }
            }

        })

        directionPin = service.openGpio("BCM26")
        directionPin.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW)
        stepPin = service.openGpio("BCM20")
        stepPin.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW)

        mCameraThread = HandlerThread("CameraBackground")
        mCameraThread.start()
        val mCameraHandler = Handler(mCameraThread.looper)
        mCamera = CameraHelper.getInstance()
        mCamera.initializeCamera(this, mCameraHandler, mOnImageAvailableListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        mCamera.shutDown()
        mCameraThread.quitSafely()
    }

    private fun setClosingTime(time: Time){
        val currentTime = Calendar.getInstance(TimeZone.getTimeZone("Europe/Berlin"))
        val closingTime = Calendar.getInstance(TimeZone.getTimeZone("Europe/Berlin"))
        closingTime.set(Calendar.HOUR_OF_DAY, time.hour)
        closingTime.set(Calendar.MINUTE, time.minutes)
        var delayInMilliseconds = closingTime.timeInMillis - currentTime.timeInMillis
        if (delayInMilliseconds <= 0) delayInMilliseconds += 86400000
        Log.d(TAG, delayInMilliseconds.toString())
        closeHandler.removeCallbacks(closeRunnable)
        closeRunnable = openCloseOnTimer(true, time)
        closeHandler.postDelayed(closeRunnable, delayInMilliseconds)
    }

    private fun setOpeningTime(time: Time){
        val currentTime = Calendar.getInstance(TimeZone.getTimeZone("Europe/Berlin"))
        val openingTime = Calendar.getInstance(TimeZone.getTimeZone("Europe/Berlin"))
        openingTime.set(Calendar.HOUR_OF_DAY, time.hour)
        openingTime.set(Calendar.MINUTE, time.minutes)
        var delayInMilliseconds = openingTime.timeInMillis - currentTime.timeInMillis
        if (delayInMilliseconds <= 0) delayInMilliseconds += 86400000
        Log.d(TAG, "Delay: $delayInMilliseconds")
        openHandler.removeCallbacks(openRunnable)
        openRunnable = openCloseOnTimer(true, time)
        openHandler.postDelayed(openRunnable, delayInMilliseconds)
    }

    private fun openCloseGate(open : Boolean){
        if (status == 1 && open || status == 3 && !open || isCurrentlyWorking) return
        isCurrentlyWorking = true
        AsyncTask.execute {
            directionPin.value = open
            for(i in 1..1000){
                Log.d(TAG, i.toString())
                stepPin.value = true
                Thread.sleep(1)
                stepPin.value = false
                Thread.sleep(1)
            }
            if (open){
                status = 1
                openRef.setValue(1)
            } else {
                status = 3
                openRef.setValue(3)
            }
            isCurrentlyWorking = false
        }
    }

    private fun openCloseOnTimer(open: Boolean, time: Time) : Runnable{
        return Runnable {
            if (open){
                status = 4
                openRef.setValue(4)
            } else {
                status = 2
                openRef.setValue(2)
            }
            openCloseGate(open)
            setOpeningTime(time)
        }
    }

    private val mOnImageAvailableListener = ImageReader.OnImageAvailableListener { reader ->
        /*val image = reader.acquireLatestImage()
        // get image bytes
        val imageBuf = image.planes[0].buffer
        val imageBytes = ByteArray(imageBuf.remaining())
        imageBuf.get(imageBytes)

        val document = HashMap<String, Any>()
        document["foto1"] = imageBytes.toList()

        firestoreDatabase.collection("stallfotos")
                .add(document)
                .addOnSuccessListener {
                    takePictureRef.setValue(false)
                }
                .addOnFailureListener {
                    takePictureRef.setValue(false)
                    Log.e(TAG, "Des Bild isch ned oben")
                }

        image.close()

        val bitmapImage = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, null)
        runOnUiThread { cameraView.setImageBitmap(bitmapImage) }*/
    }
}