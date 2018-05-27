package de.repictures.huehnerstall

import android.graphics.BitmapFactory
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.android.synthetic.main.activity_camera.*
import kotlinx.android.synthetic.main.content_camera.*


class CameraActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)
        setSupportActionBar(toolbar)

        val database = FirebaseDatabase.getInstance()
        val imageRef = database.getReference("camera_image")
        val takePictureRef = database.getReference("take_picture")

        val firestoreDatabase : FirebaseFirestore = FirebaseFirestore.getInstance()

        cameraButton.setOnClickListener {
            takePictureRef.setValue(true)
        }

        takePictureRef.addValueEventListener(object : ValueEventListener{
            override fun onCancelled(error: DatabaseError) {

            }

            override fun onDataChange(dataSnapshot: DataSnapshot) {
                if (dataSnapshot.value != null && dataSnapshot.value!! is Boolean && dataSnapshot.value!! == false) {
                    //val imageStr = dataSnapshot.value!! as String
                    firestoreDatabase.collection("stallfotos")
                            .get()
                            .addOnCompleteListener {
                                if (it.isSuccessful){
                                    var byteArray : ByteArray? = null
                                    for (document in it.result) {
                                        byteArray = (document.data["foto1"] as List<Byte>).toByteArray()
                                    }
                                    if (byteArray != null) {
                                        val bitmapImage = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size, null)
                                        cameraView.setImageBitmap(bitmapImage)
                                    }
                                } else {
                                    Log.e("TAG", "Daten sind nicht angekommen")
                                }
                            }
                    /*val image = dataSnapshot.value!! as Image
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.capacity())
                    buffer.get(bytes)
                    val bitmapImage = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, null)
                    cameraView.setImageBitmap(bitmapImage)*/
                }
            }
        })
    }

}
