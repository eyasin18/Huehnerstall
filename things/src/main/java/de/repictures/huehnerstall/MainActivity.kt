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

    //Globale Variablen initialisieren
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
    private lateinit var gateStatusRef : DatabaseReference

    private lateinit var mCamera: CameraHelper
    private lateinit var mCameraThread: HandlerThread

    private val service = PeripheralManager.getInstance()
    lateinit var gateDirectionPin : Gpio
    lateinit var gateStepPin : Gpio
    private lateinit var gateSleepPin : Gpio
    private lateinit var feedSleepPin : Gpio
    private lateinit var feedDirectionPin : Gpio
    private lateinit var feedStepPin : Gpio
    private lateinit var openGateButton : Gpio
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

        //Überprüfe, ob wir die Berechtigung haben auf die Kamera zuzugreifen
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "No permission")
        }

        //Firebase Instant Database und Firebase Cloud Storage werden initialisiert
        storageDatabase = FirebaseStorage.getInstance()
        val instantDatabase = FirebaseDatabase.getInstance()

        //Hier werden die DatabaseReferences für die Instant Database gesetzt.
        //Die References sind die gleichen, wie die auf der Smartphone-App. Genauere Erläuterungen gibt es dort.
        openingTimeRef = instantDatabase.getReference("opening_time")
        closingTimeRef = instantDatabase.getReference("closing_time")
        gateStatusRef = instantDatabase.getReference("open")
        feedStatusRef = instantDatabase.getReference("feed_status")
        cameraImageRef = instantDatabase.getReference("camera_image")
        takePictureRef = instantDatabase.getReference("take_picture")
        lastFeedRef = instantDatabase.getReference("last_feed")
        nextFeedRef = instantDatabase.getReference("next_feed")
        onlineRef = instantDatabase.getReference("online")

        openingTimeRef.addValueEventListener(object : ValueEventListener {
            override fun onCancelled(error: DatabaseError) {}

            //Führe die setOpeningTime(Time) Methode mit der auf der Datenbank befindlichen Zeit aus
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                if (dataSnapshot.value != null) {
                    val time = dataSnapshot.getValue(Time::class.java)!!
                    setOpeningTime(time)
                }
            }
        })

        closingTimeRef.addValueEventListener(object : ValueEventListener {
            override fun onCancelled(error: DatabaseError) {}

            //Führe die setClosingTime(Time) Methode mit der auf der Datenbank befindlichen Zeit aus
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                if (dataSnapshot.value != null) {
                    val time = dataSnapshot.getValue(Time::class.java)!!
                    setClosingTime(time)
                }
            }
        })

        gateStatusRef.addValueEventListener(object : ValueEventListener {
            override fun onCancelled(error: DatabaseError) {}

            //Wird der Klappenstatus auf "Offline" oder "Status wird abgefragt..." gesetzt,
            //soll dieser wieder durch den richtigen Status ersetzt werden.
            //Wurde der Status auf "Öffnet..." gesetzt, dann öffne das Tor.
            //Wurde der Status auf "Schließt..." gesetzt, dann schließe das Tor.
            //Andererseits schreibe den Status der Datenbank in den internen Status.
            //Ist das Tor geschlossen soll der Tormotor zum Energiesparen abgeschaltet werden
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                if (dataSnapshot.value != null) {
                    val newStatus = dataSnapshot.getValue(Int::class.java)!!
                    Log.d(TAG, "Status = $newStatus")
                    if (newStatus == 5 || newStatus == 6) {
                        gateStatusRef.setValue(gateStatus)
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
        })

        takePictureRef.addValueEventListener(object : ValueEventListener{
            override fun onCancelled(error: DatabaseError) {}

            //Sage dem CameraHelper er soll ein Foto schießen
            override fun onDataChange(databaseSnapshot: DataSnapshot) {
                if (databaseSnapshot.value != null && databaseSnapshot.value is Long && databaseSnapshot.value as Long == 2L){
                    mCamera.takePicture()
                }
            }

        })

        feedStatusRef.addValueEventListener(object : ValueEventListener{
            override fun onCancelled(p0: DatabaseError) {}

            //Schreibe den Fütterstatus der Datenbank in den internen Status und führe die feed()-Methode aus
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

        })

        onlineRef.addValueEventListener(object : ValueEventListener{
            override fun onCancelled(p0: DatabaseError) {}

            //Wenn der Onlinestatus auf "Offline" gesetzt wurde, soll die Steuerzentrale diesen wieder auf "Online" setzen
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                if (dataSnapshot.value != null && dataSnapshot.value is Boolean){
                    if (!(dataSnapshot.value as Boolean)) onlineRef.setValue(true)
                }
            }

        })

        //Pins auf dem Raspberry PI werden in Variablen geschrieben

        //Pins für den Motor der Klappe werden initialisiert
        gateDirectionPin = service.openGpio("BCM16")
        gateDirectionPin = service.openGpio("BCM26")
        gateDirectionPin.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW)
        gateStepPin = service.openGpio("BCM19")
        gateStepPin.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW)
        gateSleepPin = service.openGpio("BCM6")
        gateSleepPin.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW)

        //Pins für den Motor der Futteranlage werden initialisiert
        feedDirectionPin = service.openGpio("BCM26")
        feedDirectionPin = service.openGpio("BCM16")
        feedDirectionPin.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW)
        feedStepPin = service.openGpio("BCM20")
        feedStepPin.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW)
        feedSleepPin = service.openGpio("BCM12")
        feedSleepPin.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW)

        //Pins für den mechanischen Klappenöffnungsknopf werden initialisiert und auf den
        //gateGpioCallback registriert. Dieser löst immer dann aus, wenn der Knopf gerdrückt wird
        openGateButton = service.openGpio("BCM18")
        openGateButton.setDirection(Gpio.DIRECTION_IN)
        openGateButton.setEdgeTriggerType(Gpio.EDGE_FALLING)
        openGateButton.registerGpioCallback(gateButtonHandler, gateGpioCallback)

        //Pins für den mechanischen Futteranlagenknopf werden initialisiert und auf den
        //feedGpioCallback registriert. Dieser löst immer dann aus, wenn der Knopf gerdrückt wird
        closeGateLever = service.openGpio("BCM13")
        closeGateLever.setDirection(Gpio.DIRECTION_IN)
        closeGateLever.setEdgeTriggerType(Gpio.EDGE_FALLING)
        closeGateLever.registerGpioCallback(feedButtonHandler, feedGpioCallback)

        //Die Kamera wird mit der Hilfe der Helper-Klasse aus den Google-Samples initialisiert
        //https://github.com/androidthings/doorbell
        mCameraThread = HandlerThread("CameraBackground")
        mCameraThread.start()
        val mCameraHandler = Handler(mCameraThread.looper)
        mCamera = CameraHelper.getInstance()
        mCamera.initializeCamera(this, mCameraHandler, mOnImageAvailableListener)
    }

    //Wenn die App geschlossen wird, soll die Kamera heruntergefahren werden
    override fun onDestroy() {
        super.onDestroy()
        mCamera.shutDown()
        mCameraThread.quitSafely()
    }

    //Runnable die dazu genutzt wird, um die Hühnerklappe zu einem beliebigen Zeitpunkt zu öffnen bzw. zu schließen
    private fun openCloseOnTimer(open: Boolean, time: Time) : Runnable{
        return Runnable {
            if (open){
                gateStatus = 4
                gateStatusRef.setValue(4)
            } else {
                gateStatus = 2
                gateStatusRef.setValue(2)
            }
            openCloseGate(open)
            setOpeningTime(time)
        }
    }

    //Zuerst werden Calendar-Objekte erstellt, die jeweils die momentane Zeit und die Schließzeit beinhalten
    //Dann wird die differenz dieser Zeiten in Millisekunden in delayInMilliseconds geschrieben
    //Ist diese Differenz negativ, ist also die Schließzeit für heute schon vorbei, sollen 86400000 (= 1 Tag)
    //hinzuaddiert werden.
    //Dann soll wird die Runnable, die das Tor zu einem beliebigen Zeitpunkt öffnen bzw. schließen kann
    //in genau diesem Betrag von Millisekunden ausgeführt werden.
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

    //Zuerst werden Calendar-Objekte erstellt, die jeweils die momentane Zeit und die Öffnungszeit beinhalten
    //Dann wird die differenz dieser Zeiten in Millisekunden in delayInMilliseconds geschrieben
    //Ist diese Differenz negativ, ist also die Schließzeit für heute schon vorbei, sollen 86400000 (= 1 Tag)
    //hinzuaddiert werden.
    //Dann soll wird die Runnable, die das Tor zu einem beliebigen Zeitpunkt öffnen bzw. schließen kann
    //in genau diesem Betrag von Millisekunden ausgeführt werden.
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

    //Methode zum auführen AsyncTasks, der die Hühnerklappe in einem Hintergrundthread öffnen/schließen soll
    //Ist
    // - das Tor geöffnet und der Status auf "Offen" oder
    // - das Tor geschlossen und der Status auf "Geschlossen" oder
    // - das Tor öffnet oder schließt gerade
    //dann soll der AsyncTask nicht ausgeführt werden
    private fun openCloseGate(open : Boolean){
        if (gateStatus == 1 && open || gateStatus == 3 && !open || gateIsCurrentlyWorking) return
        runOnUiThread {
            gateTask = gateAsyncTask(open)
            gateTask!!.execute()
        }
    }

    //Ist das Foto geschossen wird diese Methode ausgeführt. Sie lädt das Bild als ByteArray in den
    //Firebase Cloud Storage, schreibt die URL in die Instant Database und gibt der Smartphone-App zurück,
    //dass das Bild erfolgreich geschossen wurde.
    private val mOnImageAvailableListener = ImageReader.OnImageAvailableListener { reader ->
        val image = reader.acquireLatestImage()
        val storageRef = storageDatabase.reference
        val stallRef = storageRef.child("stall.jpg")

        val imageBuf = image.planes[0].buffer
        val imageBytes = ByteArray(imageBuf.remaining())
        imageBuf.get(imageBytes)

        val uploadTask = stallRef.putBytes(imageBytes)
        uploadTask.addOnFailureListener({}).addOnSuccessListener({})
                .continueWithTask(Continuation<UploadTask.TaskSnapshot, Task<Uri>> {
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

    //Wenn der Motor der Futteranlage sich gerade dreht, soll die ausführung dieser Methode abgebrochen werden
    //Wenn gerade schon eine Fütterung im Gange ist, soll der Füttervorgang abgebrochen werden
    //Wenn keine Fütterung im Gange ist, soll die Fütterung gestartet werden
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

    //Diese Methode lässt den Futtermotor um 180° drehen, um das Futter zu öffnen
    //Erst wird der Motor angeschaltet und die Drehrichtung gegen den Uhrzeigersinn gesetzt
    //Dann führt der Schrittmotor 100 Schritte (= 180°) aus
    //und teilt danach der Datenbank mit, dass der Füttervorgang gestartet wurde
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

    //Diese Methode lässt den Futtermotor um 180° drehen, um das Futter zu öffnen
    //Erst wird die Drehrichtung in den Uhrzeigersinn gesetzt
    //Dann führt der Schrittmotor 100 Schritte (= 180°) aus
    //Danach wird der Datenbank mitgeteilt, dass der Füttervorgang beendet wurde
    //und der Motor abgeschalten
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

    //Wird ausgelöst wenn der mechanische Knopf für die Hühnerklappe gedrückt wird
    //Ist das Tor gerade geschlossen, soll das Tor geöffnet werden
    //Ist das Tor gerade geöffnet, soll das Tor geschlossen werden
    private val gateGpioCallback = GpioCallback {
        Log.d(TAG, "open 1")
        if (gateStatus != 1 && gateStatus != 2){
            openCloseGate(true)
        } else if (gateStatus != 3 && gateStatus != 4){
            openCloseGate(false)
        }
        true
    }

    //Wird ausgelöst wenn der mechanische Knopf für die Futteranlage gedrückt wird
    //Der Fütterstatus wird entsprechend umgekehrt und danach die feed()-Methode ausgeführt
    private val feedGpioCallback = GpioCallback {
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

    //Async-Task zum betätigen des Motors der Hühnerklappe. Ist direction = true
    //wird die Hühnerklappe geöffnet, andernfalls wird sie geschlossen
    inner class gateAsyncTask(private val direction : Boolean) : AsyncTask<Int, Int, Boolean>(){

        //Bevor der Motor sich drehen soll wird der Klappenstatus auf der Datenbank aktualisiert
        override fun onPreExecute() {
            gateIsCurrentlyWorking = true
            if (direction){
                gateStatus = 2
                gateStatusRef.setValue(2)
            } else {
                gateStatus = 4
                gateStatusRef.setValue(4)
            }
        }

        //Nun wird die Richtung in die sich der Motor drehen soll entsprechend gesetzt
        //und danach 500 Schritte (= ca. 2,5 Umdrehungen) gedreht
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

        //War das erfolgreich, wird der Status auf der Datenbank wieder entsprechend aktualisiert
        override fun onPostExecute(result: Boolean?) {
            if (direction){
                gateStatus = 1
                gateStatusRef.setValue(1)
            } else {
                gateStatus = 3
                gateStatusRef.setValue(3)
            }
            gateIsCurrentlyWorking = false
        }
    }

    //AsyncTask zum betätigen des Motors der Futteranlage
    inner class feedAsyncTask() : AsyncTask<Int, Int, Boolean>(){

        //Standartmäßig soll die Futteranlage geöffnet, 10 Sekunden gewartet und dann wieder geschlossen werden
        override fun doInBackground(vararg params: Int?): Boolean {
            openFeed()
            Thread.sleep(10000)
            closeFeed()
            return true
        }

        //Ist dieser Vorgang erfolgreich, so wird der jetzige Zeitpunkt als String formatiert
        //als Zeitpunkt der letzten Fütterung auf die Datenbank eingetragen
        override fun onPostExecute(result: Boolean?) {
            val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm.ssSS")
            val currentTime = Calendar.getInstance()
            currentTime.set(Calendar.HOUR_OF_DAY, currentTime.get(Calendar.HOUR_OF_DAY)+2)
            lastFeedRef.setValue(dateFormat.format(currentTime.time))
        }

        //Wurde der Vorgang abgebrochen, so wird zuerst die Futteranlage geschlossen und dann
        //der jetzige Zeitpunkt als String formatiert als Zeitpunkt
        //der letzten Fütterung auf die Datenbank eingetragen
        override fun onCancelled(result: Boolean?) {
            closeFeed()
            val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm.ssSS")
            val currentTime = Calendar.getInstance()
            currentTime.set(Calendar.HOUR_OF_DAY, currentTime.get(Calendar.HOUR_OF_DAY)+2)
            lastFeedRef.setValue(dateFormat.format(currentTime.time))
        }
    }
}