package de.repictures.huehnerstall

import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import com.bumptech.glide.Glide
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage
import de.repictures.huehnerstall.pojo.Time
import kotlinx.android.synthetic.main.activity_main.*
import java.text.DecimalFormat

private val TAG = MainActivity::class.java.simpleName

class MainActivity : AppCompatActivity(), View.OnClickListener {

    //Initialisieren der globalen Variablen
    private var online = false
    private var gateStatus = 0
    private var feedStatus = 0L
    private var openingTime : Time = Time(0, 0)
    private var closingTime : Time = Time(0, 0)
    private lateinit var openingTimeRef: DatabaseReference
    private lateinit var closingTimeRef: DatabaseReference
    private lateinit var gateStatusRef : DatabaseReference
    private lateinit var storageDatabase : FirebaseStorage
    private lateinit var feedStatusRef: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //Firebase Instant Database und Firebase Cloud Storage werden initialisiert
        val instantDatabase = FirebaseDatabase.getInstance()
        storageDatabase = FirebaseStorage.getInstance()

        //Wenn das closingLayout, das openingLayout, der openFlapButton, die cameraCard oder der feedButton
        //angetippt werden, soll die Methode onClick ausgeführt werden
        openingLayout.setOnClickListener(this)
        closingLayout.setOnClickListener(this)
        openFlapButton.setOnClickListener(this)
        cameraCard.setOnClickListener(this)
        feedButton.setOnClickListener(this)

        //Die Uhrzeit in den TextViews für die Öffnungs- & Schließzeit wird beim initialisieren dieser Activity
        //auf 00:00 Uhr gesetzt
        openingTimeText.text = getTimeStr(0, 0)
        closingTimeText.text = getTimeStr(0, 0)

        //Hier werden die DatabaseReferences für die Instant Database gesetzt.
        //"A Firebase reference represents a particular location in your Database and can be used for reading or writing data to that Database location"
        //Mehr Infos unter:
        //https://firebase.google.com/docs/reference/android/com/google/firebase/database/DatabaseReference
        //https://firebase.google.com/docs/database/android/start/
        openingTimeRef = instantDatabase.getReference("opening_time") //Time-Objekt; Enthält Integers für die Stunden und Minuten zu der die Hühnerklappe geöffnet werden soll
        closingTimeRef = instantDatabase.getReference("closing_time") //Time-Objekt; Enthält Integers für die Stunden und Minuten zu der die Hühnerklappe geschlossen werden soll
        gateStatusRef = instantDatabase.getReference("open") //Integer; Jede mögliche Zahl des Integers stellt einen Status der Hühnerklappe dar (siehe getGateStatusStr())
        val imageRef = instantDatabase.getReference("camera_image") //String; Stellt die URL zum Foto der Kamera dar
        feedStatusRef = instantDatabase.getReference("feed_status") //Long; Jede mögliche Zahl des Longs stellt einen Status der Hühnerklappe dar (siehe getFeedStatusStr())
        val lastFeedRef = instantDatabase.getReference("last_feed") //String; Stellt ein formatiertes Datum der letzten Fütterung dar
        val nextFeedRef = instantDatabase.getReference("next_feed") //String; Stellt ein formatiertes Datum der nächsten Fütterung dar
        val onlineRef = instantDatabase.getReference("online") //Boolean; Stellt den Online-Status der Steuerzentrale dar; online = true --> "Online", online = false --> "Offline"

        //Immer dann wenn das Objekt in der openingTimeRef geändert wird, soll die onDataChange Methode ausgelöst werden
        //Sie wird auch einmal beim initialisieren der Activity ausgeführt, ohne dass dazu Daten geändert werden müssen
        //https://firebase.google.com/docs/reference/android/com/google/firebase/database/ValueEventListener
        //https://firebase.google.com/docs/database/android/start/
        openingTimeRef.addValueEventListener(object : ValueEventListener {
            override fun onCancelled(error: DatabaseError) {}

            //Lese das Time-Objekt aus der Datenbank. Es enthält einen Int für die Stunden und einen Int für die Minuten
            //Schreibe die formatierte Zeit in den TextView für die Öffnungszeit
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                if (dataSnapshot.value != null) {
                    openingTime = dataSnapshot.getValue(Time::class.java)!!
                    openingTimeText.text = getTimeStr(openingTime.hour, openingTime.minutes)
                }
            }
        })

        //Dieser Listener verhält sich genau wie der der openingTimeRef und beobachtet das Objekt in der closingTimeRef
        closingTimeRef.addValueEventListener(object : ValueEventListener {
            override fun onCancelled(error: DatabaseError) {}

            //Lese das Time-Objekt aus der Datenbank. Es enthält einen Int für die Stunden und einen Int für die Minuten
            //Schreibe die formatierte Zeit in den TextView für die Schlließzeit
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                if (dataSnapshot.value != null) {
                    closingTime = dataSnapshot.getValue(Time::class.java)!!
                    closingTimeText.text = getTimeStr(closingTime.hour, closingTime.minutes)
                }
            }
        })

        //Dieser Listener verhält sich genau wie der der openingTimeRef und beobachtet den Integer in der gateStatusRef
        gateStatusRef.addValueEventListener(object : ValueEventListener{
            override fun onCancelled(error: DatabaseError) {}

            //Der Status der Hühnerklappe in der Datenbank soll in die interne Variable gateStatus übertragen werden
            //Der Statustext für die Hühnerklappe soll entsprechend aktualisiert werden
            //Ist die Hühnerklappe geöffnet, soll der Button zum betätigen der Hühnerklappe aktiviert sein und der Text "Manuell schließen" anzeigen
            //Ist die Hühnerklappe geschlossen, soll der Button zum betätigen der Hühnerklappe aktiviert sein und der Text "Manuell öffnen" anzeigen
            //Andernfalls soll der Button zum betätigen der Hühnerklappe deaktiviert sein
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                if (dataSnapshot.getValue(Int::class.java) != 0) {
                    gateStatus = dataSnapshot.getValue(Int::class.java)!!
                    gateStatusText.text = getGateStatusStr(gateStatus)
                    Log.d(TAG, "Status = $gateStatus")
                    when (gateStatus) {
                        1 -> {
                            openFlapButton.isEnabled = true
                            openFlapButton.text = resources.getString(R.string.close_manually)
                        }
                        3 -> {
                            openFlapButton.isEnabled = true
                            openFlapButton.text = resources.getString(R.string.open_manually)
                        }
                        else -> openFlapButton.isEnabled = false
                    }
                }
            }
        })

        //Dieser Listener verhält sich genau wie der der openingTimeRef und beobachtet den String in der imageRef
        imageRef.addValueEventListener(object : ValueEventListener{
            override fun onCancelled(p0: DatabaseError) {}

            //Der eingegangene String ist die URL zum Bild der Kamera im Hühnerstall. Dieses soll in unseren ImageView
            //mithilfe der Bibliothek "Glide" geladen werden
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                if (dataSnapshot.value != null){
                    Glide.with(this@MainActivity)
                            .load(dataSnapshot.value as String)
                            .into(cameraPreviewImage)
                }
            }

        })

        //Dieser Listener verhält sich genau wie der der openingTimeRef und beobachtet den Boolean in der onlineRef
        onlineRef.addValueEventListener(object : ValueEventListener{
            override fun onCancelled(p0: DatabaseError) {}

            //Der Boolean wird in eine interne Variable geschrieben und die Statustexte aktualisiert, je nach dem ob die
            //Steuerzentrale online ist oder nicht
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                if (dataSnapshot.value != null && dataSnapshot.value is Boolean){
                    online = dataSnapshot.value as Boolean
                    if (!online){
                        gateStatusText.text = getGateStatusStr(6)
                        feedStatusText.text = getFeedStatusStr(4L)
                    } else {
                        gateStatusText.text = getGateStatusStr(gateStatus)
                        feedStatusText.text = getFeedStatusStr(feedStatus)
                    }
                }
            }
        })

        //Die Reference für den Online-Status der Steuerzentrale wird auf "Offline" (online = false) gesetzt.
        //Ist die Steuerzentrale online und "bekommt mit" dass ihr Online-Status auf false gesetzt wurde,
        //soll sie ihn wieder auf true setzen und der App zeigen, dass sie wirklich online ist.
        onlineRef.setValue(false)
        //Die TextViews, die den Status der Hühnerklappe und des Tors anzeigen, sollen "Status wird abgefragt" anzeigen
        gateStatusText.text = getGateStatusStr(5)
        feedStatusText.text = getFeedStatusStr(5L)

        //Wenn die Steuerzentrale in 15 Sekunden den Online-Status noch nicht aktualisiert hat, dann soll
        //in der App angezeigt werden, dass die Steuerzentrale offline ist.
        java.util.Timer().schedule(
                object : java.util.TimerTask() {
                    override fun run() {
                        if (!online){
                            runOnUiThread {
                                gateStatusText.text = getGateStatusStr(6)
                                feedStatusText.text = getFeedStatusStr(4L)
                            }
                        }
                    }
                },
                15000
        )

        //Dieser Listener verhält sich genau wie der der openingTimeRef und beobachtet den Long in der feedStatusRef
        feedStatusRef.addValueEventListener(object : ValueEventListener{
            override fun onCancelled(p0: DatabaseError) {}

            //Die interne Variable feedStatus soll auf den Wert auf der Datenbank gesetzt werden
            //Je nachdem ob die Fütterung gerade läuft oder abgeschlossen ist, soll der "Manuell füttern"-Button
            //entweder deaktiviert oder aktiviert sein.
            //Der Text des TextViews in dem der Status der letzten Fütterung angezeigt wird soll mit dem Status
            //der Datenbank aktualisiert werden.
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                if (dataSnapshot.value != null && dataSnapshot.value is Long){
                    feedStatus = dataSnapshot.value as Long
                    when(feedStatus){
                        1L, 3L -> feedButton.isEnabled = true
                        2L -> feedButton.isEnabled = false
                    }
                    feedStatusText.text = getFeedStatusStr(feedStatus)
                }
            }

        })

        //Dieser Listener verhält sich genau wie der der openingTimeRef und beobachtet den String in der lastFeedRef
        lastFeedRef.addValueEventListener(object : ValueEventListener{
            override fun onCancelled(p0: DatabaseError) {}

            //Der Text der TextView in dem der Zeitpunkt der letzten Fütterung angezeigt wird soll auf
            //den eingegangen String gesetzt werden
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                if (dataSnapshot.value != null && dataSnapshot.value is String){
                    lastFeedText.text = dataSnapshot.value as String
                }
            }
        })
    }

    //Wird immer dann ausgelöst, wenn eines der Views geklickt wird, der mit einem ".setOnClickListener(this)" versehen wurde
    override fun onClick(view: View) {
        when(view.id){
            R.id.closingLayout -> setTime(false) //Führe Methode "setTime(Boolean)" aus
            R.id.openingLayout -> setTime(true) //Führe Methode "setTime(Boolean)" aus
            //Startet die CameraActivity, welche nur dazu da ist, der Steuerzentrale zu sagen, dass sie ein Foto schießen soll
            R.id.cameraCard -> startActivity(Intent(this, CameraActivity::class.java))
            R.id.openFlapButton -> sendFlapMessage() //Führe Methode "sendFlapMessage()" aus
            //Wenn die Fütteranlage gerade nicht füttert, dann sag der Steuerzentrale sie soll jetzt füttern.
            //Andernfalls soll sie mit füttern aufhören
            R.id.feedButton -> {
                if (feedStatus == 1L || feedStatus == 3L) {
                    feedStatusRef.setValue(2)
                    feedStatus = 2
                } else if (feedStatus == 2L){
                    feedStatusRef.setValue(1)
                    feedStatus = 1
                }
            }
        }
    }

    //Wenn das Tor gerade geöffnet ist, soll der Status auf "Schließen..." gesetzt werden, um der
    //Steuerzentrale zu zeigen, das sie die Klappe schließen soll.
    //Wenn das Tor gerade geschlossen ist, soll der Status auf "Öffnet..." gesetzt werden, um der
    //Steuerzentrale zu zeigen, das sie die Klappe öffnen soll.
    private fun sendFlapMessage() {
        if(gateStatus == 1){
            gateStatusRef.setValue(4)
        } else if (gateStatus == 3){
            gateStatusRef.setValue(2)
        }
    }

    //Öffne einen von der Android API bereitgestellten TimePicker, der vom Nutzer eine Uhrzeit auswählen lassen soll.
    //Ist openingTime = true, wird diese Zeit als Öffnungszeit eingetragen, ist openingTime = false wird sie als Schließzeit eingetragen
    private fun setTime(openingTime: Boolean) {
        val mTimePicker: TimePickerDialog
        mTimePicker = TimePickerDialog(this, TimePickerDialog.OnTimeSetListener { _, selectedHour, selectedMinute ->
            run {
                if (!openingTime) {
                    closingTimeText.text = getTimeStr(selectedHour, selectedMinute)
                    closingTimeRef.setValue(Time(selectedHour, selectedMinute))
                } else {
                    openingTimeText.text = getTimeStr(selectedHour, selectedMinute)
                    openingTimeRef.setValue(Time(selectedHour, selectedMinute))
                }
            }
        }, if (openingTime) this.openingTime.hour else this.closingTime.hour, if (openingTime) this.openingTime.minutes else this.closingTime.minutes, true)
        mTimePicker.setTitle(if (openingTime) resources.getString(R.string.set_opening_time) else resources.getString(R.string.set_closing_time))
        mTimePicker.show()
    }

    //Formatiert einen Integer für die Stunden und einen Integer für die Minuten als "schönen" String mit einer Uhrzeit
    private fun getTimeStr(hours: Int, minutes: Int): String {
        val decimalFormat = DecimalFormat("00")
        return String.format("%s:%s Uhr", decimalFormat.format(hours), decimalFormat.format(minutes))
    }

    //Wandelt den Integer des Klappenstautus in einen String der den Status in Worten wiedergibt
    private fun getGateStatusStr(code: Int): String {
        return when(code){
            1 -> resources.getString(R.string.opened)
            2 -> resources.getString(R.string.opening)
            3 -> resources.getString(R.string.closed)
            4 -> resources.getString(R.string.closing)
            5 -> resources.getString(R.string.status_requested)
            6 -> resources.getString(R.string.offline)
            else -> resources.getString(R.string.error)
        }
    }

    //Wandelt den Long des Futterstautus in einen String der den Status in Worten wiedergibt
    private fun getFeedStatusStr(code: Long): String {
        return when(code){
            1L -> resources.getString(R.string.last_feed_successfull)
            2L -> resources.getString(R.string.feeding)
            3L -> resources.getString(R.string.error_at_last_feed)
            4L -> resources.getString(R.string.offline)
            5L -> resources.getString(R.string.status_requested)
            else -> resources.getString(R.string.error)
        }
    }
}