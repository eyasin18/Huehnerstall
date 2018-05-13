package de.repictures.huehnerstall

import android.app.TimePickerDialog
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.widget.RelativeLayout
import android.widget.TextView
import com.google.firebase.database.*
import de.repictures.huehnerstall.pojo.Time
import java.text.DecimalFormat
import java.util.*

class MainActivity : AppCompatActivity(), View.OnClickListener {

    private var opened = 0
    private var openingTime : Time = Time(0, 0)
    private var closingTime : Time = Time(0, 0)
    private lateinit var openingTimeText : TextView
    private lateinit var closingTimeText: TextView
    private lateinit var openingTimeRef: DatabaseReference
    private lateinit var closingTimeRef: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val openingLayout: RelativeLayout = findViewById(R.id.opening_time)
        openingLayout.setOnClickListener(this)

        val closingLayout: RelativeLayout = findViewById(R.id.closing_time)
        closingLayout.setOnClickListener(this)

        val statusText = findViewById<TextView>(R.id.status_text)

        openingTimeText = findViewById(R.id.opening_time_text)
        openingTimeText.text = getTimeStr(0, 0)

        closingTimeText = findViewById(R.id.closing_time_text)
        closingTimeText.text = getTimeStr(0, 0)

        // Write a message to the database
        val database = FirebaseDatabase.getInstance()
        openingTimeRef = database.getReference("opening_time")
        closingTimeRef = database.getReference("closing_time")
        val openRef = database.getReference("open")

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

        statusText.text = getStatusStr(5)
        openRef.setValue(5)
        opened = 5
        openRef.addListenerForSingleValueEvent(object : ValueEventListener{
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                if (dataSnapshot.value != null) {
                    opened = dataSnapshot.getValue(Int::class.java)!!
                    statusText.text = getStatusStr(opened)
                }
            }

            override fun onCancelled(error: DatabaseError) {

            }
        })

        java.util.Timer().schedule(
                object : java.util.TimerTask() {
                    override fun run() {
                        if (opened == 5){
                            opened = 6
                            openRef.setValue(6)
                            statusText.text = getStatusStr(6)
                        }
                    }
                },
                15000
        )
    }

    override fun onClick(view: View) {
        when(view.id){
            R.id.closing_time -> setTime(false)
            R.id.opening_time -> setTime(true)

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

    private fun getStatusStr(code: Int): String {
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
}