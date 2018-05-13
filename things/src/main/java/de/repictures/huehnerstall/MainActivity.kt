package de.repictures.huehnerstall

import android.app.Activity
import android.os.Bundle
import com.google.firebase.database.*
import de.repictures.huehnerstall.pojo.Time

private val TAG = MainActivity::class.java.simpleName

class MainActivity : Activity() {

    private var status = 0
    private lateinit var openingTimeRef: DatabaseReference
    private lateinit var closingTimeRef: DatabaseReference
    private var time: Time? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Write a message to the database
        val database = FirebaseDatabase.getInstance()
        openingTimeRef = database.getReference("opening_time")
        closingTimeRef = database.getReference("closing_time")
        val openRef = database.getReference("open")

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
                    if (status == 5){
                        openRef.setValue(1)
                        status = 1
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {

            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}