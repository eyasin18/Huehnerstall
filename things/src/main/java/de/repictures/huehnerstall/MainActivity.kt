package de.repictures.huehnerstall

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.ImageReader
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.widget.ImageView
import com.google.android.gms.tasks.Continuation
import com.google.android.gms.tasks.Task
import com.google.android.things.contrib.driver.button.Button
import com.google.android.things.pio.Gpio
import com.google.android.things.pio.PeripheralManager
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.UploadTask
import de.repictures.huehnerstall.pojo.Time
import java.text.SimpleDateFormat
import java.util.*
import com.google.android.things.pio.GpioCallback

private val TAG = MainActivity::class.java.simpleName

class MainActivity : Activity() {

    private var gateStatus = 0
    private var feedStatus = 0L
    private lateinit var openingTimeRef: DatabaseReference
    private lateinit var closingTimeRef: DatabaseReference
    private lateinit var phoneDeviceTokenRef : DatabaseReference
    private lateinit var thingsDeviceTokenRef : DatabaseReference
    private lateinit var cameraImageRef : DatabaseReference
    private lateinit var takePictureRef: DatabaseReference
    private lateinit var lastFeedRef : DatabaseReference
    private lateinit var nextFeedRef : DatabaseReference
    private lateinit var feedStatusRef: DatabaseReference
    private lateinit var onlineRef : DatabaseReference
    private lateinit var openRef : DatabaseReference

    private lateinit var mCamera: CameraHelper
    private lateinit var mCameraThread: HandlerThread

    private val service = PeripheralManager.getInstance()
    lateinit var gateDirectionPin : Gpio
    lateinit var gateStepPin : Gpio
    private lateinit var gateSleepPin : Gpio
    private lateinit var feedSleepPin : Gpio
    private lateinit var feedDirectionPin : Gpio
    private lateinit var feedStepPin : Gpio
    private lateinit var openGateLever : Gpio
    private lateinit var closeGateLever : Gpio

    private var closeRunnable: Runnable = Runnable {  }
    private var openRunnable: Runnable = Runnable {  }
    private var closeHandler: Handler = Handler()
    private var openHandler: Handler = Handler()
    private var feedButtonHandler : Handler = Handler()
    private var gateButtonHandler : Handler = Handler()

    private var gateIsCurrentlyWorking = false
    private var feedIsCurrentlyWorking = false

    private var feedPosition = 0
    private var gateTask : gateAsyncTask? = null
    private var feedTask : feedAsyncTask? = null

    private lateinit var storageDatabase : FirebaseStorage

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // We need permission to access the camera
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            // A problem occurred auto-granting the permission
            Log.e(TAG, "No permission")
        }
        storageDatabase = FirebaseStorage.getInstance()
        val instantDatabase = FirebaseDatabase.getInstance()
        openingTimeRef = instantDatabase.getReference("opening_time")
        closingTimeRef = instantDatabase.getReference("closing_time")
        openRef = instantDatabase.getReference("open")
        cameraImageRef = instantDatabase.getReference("camera_image")
        phoneDeviceTokenRef = instantDatabase.getReference("phone_device_token")
        thingsDeviceTokenRef = instantDatabase.getReference("things_device_token")
        takePictureRef = instantDatabase.getReference("take_picture")
        feedStatusRef = instantDatabase.getReference("feed_status")
        lastFeedRef = instantDatabase.getReference("last_feed")
        nextFeedRef = instantDatabase.getReference("next_feed")
        onlineRef = instantDatabase.getReference("online")

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
                        openRef.setValue(gateStatus)
                    } else if (newStatus == 2) {
                        gateStatus = newStatus
                        openCloseGate(true)
                    } else if (newStatus == 4) {
                        gateStatus = newStatus
                        openCloseGate(false)
                    } else {
                        gateStatus = newStatus
                    }
                    gateSleepPin.value = gateStatus != 3
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
                if (databaseSnapshot.value != null && databaseSnapshot.value is Long && databaseSnapshot.value as Long == 2L){
                    mCamera.takePicture()
                }
            }

        })

        feedStatusRef.addValueEventListener(object : ValueEventListener{
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                if (dataSnapshot.value != null && dataSnapshot.value is Long){
                    feedStatus = dataSnapshot.value as Long
                    Log.d(TAG, "zahl= $feedStatus")
                    runOnUiThread {
                        Log.d(TAG, "start")
                        feed()
                        Log.d(TAG, "fertig?")
                    }
                }
            }

            override fun onCancelled(p0: DatabaseError) {

            }

        })

        onlineRef.addValueEventListener(object : ValueEventListener{
            override fun onCancelled(p0: DatabaseError) {

            }

            override fun onDataChange(dataSnapshot: DataSnapshot) {
                if (dataSnapshot.value != null && dataSnapshot.value is Boolean){
                    if (!(dataSnapshot.value as Boolean)) onlineRef.setValue(true)
                }
            }

        })

        gateDirectionPin = service.openGpio("BCM16")
        gateDirectionPin.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW)
        gateStepPin = service.openGpio("BCM19")
        gateStepPin.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW)
        gateSleepPin = service.openGpio("BCM6")
        gateSleepPin.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW)

        feedDirectionPin = service.openGpio("BCM26")
        feedDirectionPin.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW)
        feedStepPin = service.openGpio("BCM20")
        feedStepPin.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW)
        feedSleepPin = service.openGpio("BCM12")
        feedSleepPin.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW)

        openGateLever = service.openGpio("BCM18")
        openGateLever.setDirection(Gpio.DIRECTION_IN)
        openGateLever.setEdgeTriggerType(Gpio.EDGE_FALLING)
        openGateLever.registerGpioCallback(gateButtonHandler, openGateGpioCallback)

        closeGateLever = service.openGpio("BCM13")
        closeGateLever.setDirection(Gpio.DIRECTION_IN)
        closeGateLever.setEdgeTriggerType(Gpio.EDGE_FALLING)
        closeGateLever.registerGpioCallback(feedButtonHandler, openFeedGpioCallback)

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
        if (gateStatus == 1 && open || gateStatus == 3 && !open || gateIsCurrentlyWorking) return
        runOnUiThread {
            gateTask = gateAsyncTask(open)
            gateTask!!.execute()
        }
    }

    private fun openCloseOnTimer(open: Boolean, time: Time) : Runnable{
        return Runnable {
            if (open){
                gateStatus = 4
                openRef.setValue(4)
            } else {
                gateStatus = 2
                openRef.setValue(2)
            }
            openCloseGate(open)
            setOpeningTime(time)
        }
    }

    private val mOnImageAvailableListener = ImageReader.OnImageAvailableListener { reader ->
        val image = reader.acquireLatestImage()
        val storageRef = storageDatabase.reference
        val stallRef = storageRef.child("stall.jpg")

        val imageBuf = image.planes[0].buffer
        val imageBytes = ByteArray(imageBuf.remaining())
        imageBuf.get(imageBytes)

        val uploadTask = stallRef.putBytes(imageBytes)
        uploadTask.addOnFailureListener({
            // Handle unsuccessful uploads
        }).addOnSuccessListener({
            // taskSnapshot.getMetadata() contains file metadata such as size, content-type, etc.
            // ...
        }).continueWithTask(Continuation<UploadTask.TaskSnapshot, Task<Uri>> {
            if (!it.isSuccessful) {
                throw it.exception!!
            }

            return@Continuation stallRef.downloadUrl
        }).addOnCompleteListener {
            if (it.isSuccessful){
                val uri = it.result
                cameraImageRef.setValue(uri.toString())
                takePictureRef.setValue(3)
            } else {
                Log.e(TAG, it.exception.toString())
            }
        }

        image.close()
        val bitmapImage = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, null)
        runOnUiThread { findViewById<ImageView>(R.id.cameraView).setImageBitmap(bitmapImage) }
    }

    private fun feed(){
        Log.d(TAG, "open 2 && $feedStatus")
        if (feedIsCurrentlyWorking) return
        if ((feedStatus == 1L || feedStatus == 3L) && feedTask != null){
            feedTask!!.cancel(true)
        } else if (feedStatus == 2L){
            feedTask = feedAsyncTask()
            feedTask!!.execute()
        }
    }

    private fun openFeed(){
        feedIsCurrentlyWorking = true
        feedSleepPin.value = true
        feedDirectionPin.value = true
        for(i in feedPosition..100){
            Log.d(TAG, i.toString())
            feedStepPin.value = true
            Thread.sleep(1)
            feedStepPin.value = false
            Thread.sleep(1)
            feedPosition++
        }
        feedStatusRef.setValue(2)
        feedIsCurrentlyWorking = false
    }

    private fun closeFeed(){
        feedIsCurrentlyWorking = true
        feedSleepPin.value = true
        feedDirectionPin.value = false
        for(i in 0..feedPosition){
            Log.d(TAG, i.toString())
            feedStepPin.value = true
            Thread.sleep(1)
            feedStepPin.value = false
            Thread.sleep(1)
            feedPosition--
        }
        feedStatusRef.setValue(1)
        feedSleepPin.value = false
        feedIsCurrentlyWorking = false
    }

    private val openGateGpioCallback = GpioCallback {
        Log.d(TAG, "open 1")
        if (gateStatus != 1 && gateStatus != 2){
            openCloseGate(true)
        } else if (gateStatus != 3 && gateStatus != 4){
            openCloseGate(false)
        }
        true
    }

    private val openFeedGpioCallback = GpioCallback {
        if (feedStatus == 2L){
            feedStatus = 1L
        } else if (feedStatus == 1L || feedStatus == 3L){
            feedStatus = 2L
        }
        runOnUiThread {
            feed()
        }
        true
    }

    inner class gateAsyncTask(private val direction : Boolean) : AsyncTask<Int, Int, Boolean>(){
        override fun onPreExecute() {
            gateIsCurrentlyWorking = true
            if (direction){
                gateStatus = 2
                openRef.setValue(2)
            } else {
                gateStatus = 4
                openRef.setValue(4)
            }
        }

        override fun doInBackground(vararg params: Int?): Boolean {
            gateDirectionPin.value = direction
            for(i in 1..500){
                Log.d(TAG, i.toString())
                gateStepPin.value = true
                Thread.sleep(0, 10)
                gateStepPin.value = false
                Thread.sleep(0, 10)
            }
            return true
        }

        override fun onPostExecute(result: Boolean?) {
            if (direction){
                gateStatus = 1
                openRef.setValue(1)
            } else {
                gateStatus = 3
                openRef.setValue(3)
            }
            gateIsCurrentlyWorking = false
        }
    }

    inner class feedAsyncTask() : AsyncTask<Int, Int, Boolean>(){
        override fun doInBackground(vararg params: Int?): Boolean {
            openFeed()
            Thread.sleep(10000)
            closeFeed()
            return true
        }

        override fun onCancelled(result: Boolean?) {
            closeFeed()
            val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm.ssSS")
            val currentTime = Calendar.getInstance()
            currentTime.set(Calendar.HOUR_OF_DAY, currentTime.get(Calendar.HOUR_OF_DAY)+2)
            lastFeedRef.setValue(dateFormat.format(currentTime.time))
        }

        override fun onPostExecute(result: Boolean?) {
            val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm.ssSS")
            val currentTime = Calendar.getInstance()
            currentTime.set(Calendar.HOUR_OF_DAY, currentTime.get(Calendar.HOUR_OF_DAY)+2)
            lastFeedRef.setValue(dateFormat.format(currentTime.time))
        }
    }
}