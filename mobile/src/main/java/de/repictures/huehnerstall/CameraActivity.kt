package de.repictures.huehnerstall

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage
import kotlinx.android.synthetic.main.activity_camera.*
import kotlinx.android.synthetic.main.content_camera.*

class CameraActivity : AppCompatActivity() {

    private var imageUri = ""
    private lateinit var storageDatabase : FirebaseStorage
    private lateinit var takePictureRef: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)
        setSupportActionBar(toolbar)

        storageDatabase = FirebaseStorage.getInstance()
        val instantDatabase = FirebaseDatabase.getInstance()
        val imageRef = instantDatabase.getReference("camera_image")
        takePictureRef = instantDatabase.getReference("take_picture")

        cameraButton.setOnClickListener {
            takePictureRef.setValue(2)
        }

        imageRef.addValueEventListener(object : ValueEventListener{
            override fun onCancelled(p0: DatabaseError) {

            }

            override fun onDataChange(dataSnapshot: DataSnapshot) {
                if (dataSnapshot.value != null){
                    imageUri = dataSnapshot.value as String
                    downloadPicture()
                }
            }

        })

        takePictureRef.addValueEventListener(object : ValueEventListener{
            override fun onCancelled(error: DatabaseError) {

            }

            override fun onDataChange(dataSnapshot: DataSnapshot) {
                if (dataSnapshot.value != null && dataSnapshot.value is Int && (dataSnapshot.value as Int) == 3){
                    downloadPicture()
                }
            }
        })
    }

    private fun downloadPicture() {
        Glide.with(this)
                .load(imageUri)
                .into(cameraView)
        takePictureRef.setValue(1)
    }

}
