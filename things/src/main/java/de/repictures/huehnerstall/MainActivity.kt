package de.repictures.huehnerstall

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.ImageReader
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.widget.ImageView
import com.google.android.gms.tasks.Continuation
import com.google.android.gms.tasks.Task
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.UploadTask
import de.repictures.huehnerstall.pojo.Time
import de.repictures.huehnerstall.stepper.FeederStepper
import de.repictures.huehnerstall.stepper.GateStepper
import de.repictures.huehnerstall.stepper.StatusChangedListener
import de.repictures.huehnerstall.stepper.Stepper

private val TAG = MainActivity::class.java.simpleName

class MainActivity : Activity(), StatusChangedListener {
    //Globale Variablen initialisieren
    private lateinit var cameraImageRef : DatabaseReference
    private lateinit var takePictureRef: DatabaseReference
    private lateinit var lastFeedRef : DatabaseReference
    private lateinit var nextFeedRef : DatabaseReference
    private lateinit var feedStatusRef: DatabaseReference
    private lateinit var onlineRef : DatabaseReference
    private lateinit var gateStatusRef : DatabaseReference

    private lateinit var mCamera: CameraHelper
    private lateinit var mCameraThread: HandlerThread

    private lateinit var gateStepper: GateStepper
    private lateinit var feederStepper: FeederStepper

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
        gateStatusRef = instantDatabase.getReference("open")
        feedStatusRef = instantDatabase.getReference("feed_status")
        cameraImageRef = instantDatabase.getReference("camera_image")
        takePictureRef = instantDatabase.getReference("take_picture")
        lastFeedRef = instantDatabase.getReference("last_feed")
        nextFeedRef = instantDatabase.getReference("next_feed")
        onlineRef = instantDatabase.getReference("online")

        takePictureRef.addValueEventListener(object : ValueEventListener{
            override fun onCancelled(error: DatabaseError) {}

            //Sage dem CameraHelper er soll ein Foto schießen
            override fun onDataChange(databaseSnapshot: DataSnapshot) {
                if (databaseSnapshot.value != null && databaseSnapshot.value is Long && databaseSnapshot.value as Long == 2L){
                    mCamera.takePicture()
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

        gateStepper = GateStepper(this)
        gateStepper.registerStepper("BCM26", "BCM19", "BCM6")
        gateStepper.registerButton("BCM18")
        gateStepper.registerDatabase(gateStatusRef)

        feederStepper = FeederStepper(this)
        feederStepper.registerStepper("BCM16", "BCM20", "BCM12")
        feederStepper.registerButton("BCM13")
        feederStepper.registerDatabase(feedStatusRef)

        //Die Kamera wird mit der Hilfe der Helper-Klasse aus den Google-Samples initialisiert
        //https://github.com/androidthings/doorbell
        mCameraThread = HandlerThread("CameraBackground")
        mCameraThread.start()
        val mCameraHandler = Handler(mCameraThread.looper)
        mCamera = CameraHelper.getInstance()
        mCamera.initializeCamera(this, mCameraHandler, mOnImageAvailableListener)
    }


    override fun onStatusChanged(stepperMode : Int, status: Long) {
        if (stepperMode == Stepper.GATE_MODE){
            when(status){
                1L -> Log.d(TAG, "Tor wurde geöffnet")
                2L ->{
                    Log.d(TAG, "Tor wird geöffnet")
                    gateStepper.openGate()
                }
                3L -> Log.d(TAG, "Tor wurde geschlossen")
                4L ->{
                    Log.d(TAG, "Tor wird geschlossen")
                    gateStepper.closeGate()
                }
            }
        } else if (stepperMode == Stepper.FEEDER_MODE){
            when(status){
                1L -> Log.d(TAG, "Fütterung erfolgreich")
                2L ->{
                    feederStepper.feed()
                }
            }
        }
    }

    //Wenn die App geschlossen wird, soll die Kamera heruntergefahren werden
    override fun onDestroy() {
        super.onDestroy()
        mCamera.shutDown()
        mCameraThread.quitSafely()
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
}