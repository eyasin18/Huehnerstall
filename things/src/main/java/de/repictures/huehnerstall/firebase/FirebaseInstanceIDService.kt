package de.repictures.huehnerstall.firebase

import android.util.Log
import com.google.firebase.iid.FirebaseInstanceId
import com.google.firebase.iid.FirebaseInstanceIdService
import de.repictures.huehnerstall.MainActivity

class FirebaseInstanceIDService : FirebaseInstanceIdService(){

    private val TAG = FirebaseInstanceIDService::class.java.simpleName

    override fun onTokenRefresh() {
        val refreshedToken = FirebaseInstanceId.getInstance().token
        Log.d(TAG, "Refreshed Token: " + refreshedToken)
        sendTokenToServer(refreshedToken)
    }

    private fun sendTokenToServer(token : String?){

    }
}