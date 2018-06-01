package de.repictures.huehnerstall

import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.RelativeLayout
import android.widget.TextView
import com.bumptech.glide.Glide
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage
import de.repictures.huehnerstall.pojo.Time
import kotlinx.android.synthetic.main.activity_main.*
import java.text.DecimalFormat

private val TAG = MainActivity::class.java.simpleName

class MainActivity : AppCompatActivity(), View.OnClickListener {

    private var online = false
    private var gateStatus = 0
    private var feedStatus = 0L
    private var openingTime : Time = Time(0, 0)
    private var closingTime : Time = Time(0, 0)
    private lateinit var openingTimeText : TextView
    private lateinit var closingTimeText: TextView
    private lateinit var openingTimeRef: DatabaseReference
    private lateinit var closingTimeRef: DatabaseReference
    private lateinit var openRef : DatabaseReference
    private lateinit var openFlapButton : Button
    private lateinit var storageDatabase : FirebaseStorage
    private lateinit var feedStatusRef: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        storageDatabase = FirebaseStorage.getInstance()

        val openingLayout: RelativeLayout = findViewById(R.id.opening_time)
        openingLayout.setOnClickListener(this)

        val closingLayout: RelativeLayout = findViewById(R.id.closing_time)
        closingLayout.setOnClickListener(this)

        val gateStatusText = findViewById<TextView>(R.id.status_text)

        openFlapButton = findViewById(R.id.open_flap_button)
        openFlapButton.setOnClickListener(this)

        cameraCard.setOnClickListener(this)
        feedButton.setOnClickListener(this)

        openingTimeText = findViewById(R.id.opening_time_text)
        openingTimeText.text = getTimeStr(0, 0)

        closingTimeText = findViewById(R.id.closing_time_text)
        closingTimeText.text = getTimeStr(0, 0)

        // Write a message to the instantDatabase
        val instantDatabase = FirebaseDatabase.getInstance()
        openingTimeRef = instantDatabase.getReference("opening_time")
        closingTimeRef = instantDatabase.getReference("closing_time")
        openRef = instantDatabase.getReference("open")
        val imageRef = instantDatabase.getReference("camera_image")
        feedStatusRef = instantDatabase.getReference("feed_status")
        val lastFeedRef = instantDatabase.getReference("last_feed")
        val nextFeedRef = instantDatabase.getReference("next_feed")
        val onlineRef = instantDatabase.getReference("online")

        openingTimeRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                if (dataSnapshot.value != null) {
                    openingTime = dataSnapshot.getValue(Time::class.java)!!
                    openingTimeText.text = getTimeStr(openingTime.hour, openingTime.minutes)
                }
            }

            override fun onCancelled(error: DatabaseError) {

            }
        })

        closingTimeRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                if (dataSnapshot.value != null) {
                    closingTime = dataSnapshot.getValue(Time::class.java)!!
                    closingTimeText.text = getTimeStr(closingTime.hour, closingTime.minutes)
                }
            }

            override fun onCancelled(error: DatabaseError) {

            }
        })

        openRef.addValueEventListener(object : ValueEventListener{
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

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, error.toString())
            }
        })

        imageRef.addValueEventListener(object : ValueEventListener{
            override fun onCancelled(p0: DatabaseError) {

            }

            override fun onDataChange(dataSnapshot: DataSnapshot) {
                if (dataSnapshot.value != null){
                    Glide.with(this@MainActivity)
                            .load(dataSnapshot.value as String)
                            .into(cameraPreviewImage)
                }
            }

        })

        onlineRef.setValue(false)
        gateStatusText.text = getGateStatusStr(5)
        feedStatusText.text = getFeedStatusStr(5L)

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

        feedStatusRef.addValueEventListener(object : ValueEventListener{
            override fun onCancelled(p0: DatabaseError) {

            }

            override fun onDataChange(dataSnapshot: DataSnapshot) {
                if (dataSnapshot.value != null && dataSnapshot.value is Long){
                    feedStatus = dataSnapshot.value as Long
                    when(feedStatus){
                        1L, 3L -> feedButton.text = resources.getString(R.string.feed_manually)
                        2L -> feedButton.text = resources.getString(R.string.stop_feeding)
                    }
                    feedStatusText.text = getFeedStatusStr(feedStatus)
                }
            }

        })

        lastFeedRef.addValueEventListener(object : ValueEventListener{
            override fun onCancelled(p0: DatabaseError) {

            }

            override fun onDataChange(dataSnapshot: DataSnapshot) {
                if (dataSnapshot.value != null && dataSnapshot.value is String){
                    lastFeedText.text = dataSnapshot.value as String
                }
            }
        })

        onlineRef.addValueEventListener(object : ValueEventListener{
            override fun onCancelled(p0: DatabaseError) {

            }

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
    }

    override fun onClick(view: View) {
        when(view.id){
            R.id.closing_time -> setTime(false)
            R.id.opening_time -> setTime(true)
            R.id.cameraCard -> startActivity(Intent(this, CameraActivity::class.java))
            R.id.open_flap_button -> sendFlapMessage()
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

    private fun sendFlapMessage() {
        if(gateStatus == 1){
            openRef.setValue(4)
        } else if (gateStatus == 3){
            openRef.setValue(2)
        }
    }

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

    private fun getTimeStr(hours: Int, minutes: Int): String {
        val decimalFormat = DecimalFormat("00")
        return String.format("%s:%s Uhr", decimalFormat.format(hours), decimalFormat.format(minutes))
    }

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