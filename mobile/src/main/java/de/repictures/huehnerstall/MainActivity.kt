package de.repictures.huehnerstall

import android.app.TimePickerDialog
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.widget.TextView
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import de.repictures.huehnerstall.pojo.Time
import java.text.DecimalFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private var opened = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val statusText = findViewById<TextView>(R.id.status_text)

        val openingTimeText = findViewById<TextView>(R.id.opening_time_text)
        openingTimeText.text = getTimeStr(0, 0)

        val closingTimeText = findViewById<TextView>(R.id.closing_time_text)
        closingTimeText.text = getTimeStr(0, 0)

        // Write a message to the database
        val database = FirebaseDatabase.getInstance()
        val openingTimeRef = database.getReference("opening_time")
        val closingTimeRef = database.getReference("closing_time")
        val openRef = database.getReference("open")

        openingTimeRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                val time = dataSnapshot.getValue(Time::class.java)
                openingTimeText.text = getTimeStr(time!!.hour, time.minutes)
            }

            override fun onCancelled(error: DatabaseError) {

            }
        })

        closingTimeRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                val time = dataSnapshot.getValue(Time::class.java)
                closingTimeText.text = getTimeStr(time!!.hour, time.minutes)
            }

            override fun onCancelled(error: DatabaseError) {

            }
        })

        openRef.addListenerForSingleValueEvent(object : ValueEventListener{
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                opened = dataSnapshot.getValue(Int::class.java)!!
                statusText.text = getStatusStr(opened)
            }

            override fun onCancelled(error: DatabaseError) {

            }
        })

        openingTimeText.setOnClickListener({
            val mCurrentTime = Calendar.getInstance()
            val hour = mCurrentTime.get(Calendar.HOUR_OF_DAY)
            val minute = mCurrentTime.get(Calendar.MINUTE)
            val mTimePicker: TimePickerDialog
            mTimePicker = TimePickerDialog(this, TimePickerDialog.OnTimeSetListener { _, selectedHour, selectedMinute ->
                run {
                    openingTimeText.text = getTimeStr(selectedHour, selectedMinute)
                    openingTimeRef.setValue(Time(selectedHour, selectedMinute))
                }
            }, hour, minute, true)//Yes 24 hour time
            mTimePicker.setTitle("Select Time")
            mTimePicker.show()
        })

        closingTimeText.setOnClickListener({
            val mCurrentTime = Calendar.getInstance()
            val hour = mCurrentTime.get(Calendar.HOUR_OF_DAY)
            val minute = mCurrentTime.get(Calendar.MINUTE)
            val mTimePicker: TimePickerDialog
            mTimePicker = TimePickerDialog(this, TimePickerDialog.OnTimeSetListener { _, selectedHour, selectedMinute ->
                run {
                    closingTimeText.text = getTimeStr(selectedHour, selectedMinute)
                    closingTimeRef.setValue(Time(selectedHour, selectedMinute))
                }
            }, hour, minute, true)//Yes 24 hour time
            mTimePicker.setTitle("Select Time")
            mTimePicker.show()
        })
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
            else -> resources.getString(R.string.error)
        }
    }
}