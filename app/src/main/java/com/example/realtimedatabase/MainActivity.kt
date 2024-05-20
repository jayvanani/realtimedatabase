package com.example.realtimedatabase

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.firebase.ui.database.FirebaseRecyclerAdapter
import com.firebase.ui.database.FirebaseRecyclerOptions
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.Firebase
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
import com.google.firebase.database.database
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.storage
import com.theartofdev.edmodo.cropper.CropImage
import com.theartofdev.edmodo.cropper.CropImageView
import java.io.ByteArrayOutputStream
import kotlin.random.Random


class MainActivity : AppCompatActivity() {

    lateinit var btnAdd: Button
    lateinit var recyclerView: RecyclerView
    lateinit var imageView: ImageView
    var adapter: FirebaseRecyclerAdapter<*, *>? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted: Boolean ->
        if (isGranted) {
            // FCM SDK (and your app) can post notifications.
            Log.d("=======", ":  Is Granted...")
        } else {
            // TODO: Inform user that that your app will not show notifications.
            Log.d("=======", ": Is Not Granted...")
        }
    }
    private fun askNotificationPermission() {
        // This is only necessary for API level >= 33 (TIRAMISU)
        Log.d("=======", "askNotificationPermission:  --->  ${Build.VERSION.SDK_INT}")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                // FCM SDK (and your app) can post notifications.
            } else if (shouldShowRequestPermissionRationale(android.Manifest.permission.POST_NOTIFICATIONS)) {
                // TODO: display an educational UI explaining to the user the features that will be enabled
                //       by them granting the POST_NOTIFICATION permission. This UI should provide the user
                //       "OK" and "No thanks" buttons. If the user selects "OK," directly request the permission.
                //       If the user selects "No thanks," allow the user to continue without notifications.
            } else {
                // Directly ask for the permission
                requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnAdd = findViewById(R.id.btnAdd)
        recyclerView = findViewById(R.id.recyclerView)
        imageView = findViewById(R.id.imageView)

        showData()
        askNotificationPermission()

        FirebaseMessaging.getInstance().token.addOnCompleteListener(OnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w("======", "Fetching FCM registration token failed", task.exception)
                return@OnCompleteListener
            }

            // Get new FCM registration token
            val token = task.result

            // Log and toast
            Log.d("====== ","token = "+ token)
        })

        btnAdd.setOnClickListener {

            addDataToFirebase()

        }

    }

    private fun addDataToFirebase() {

        CropImage.activity()
            .setGuidelines(CropImageView.Guidelines.ON)
            .start(this)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == RESULT_OK) {
            val result = CropImage.getActivityResult(data);
            var uri = result.uri
            imageView.setImageURI(uri)
            uploadToFirebaseStorage()
            Log.d("=========", "onActivityResult: uri  --->   $uri")
        }

    }

    private fun uploadToFirebaseStorage() {
        var storage = Firebase.storage
        var ref = storage.getReference("userImages")
        ref = ref.child("" + Random.nextInt())

        // Get the data from an ImageView as bytes
        imageView.isDrawingCacheEnabled = true
        imageView.buildDrawingCache()
        val bitmap = (imageView.drawable as BitmapDrawable).bitmap
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos)
        val data = baos.toByteArray()

        var uploadTask = ref.putBytes(data)
        uploadTask.addOnFailureListener {

            Toast.makeText(this@MainActivity, it.localizedMessage, Toast.LENGTH_SHORT).show()


        }.addOnSuccessListener { taskSnapshot ->

            ref.downloadUrl.addOnSuccessListener {

                Log.d("========", "uploadToFirebaseStorage: ${it}")

                addUser(it.toString())
            }

        }
    }

    private fun addUser(imagePath: String) {
        val database = Firebase.database
        val ref: DatabaseReference = database.getReference("Users").push()

        val model = UserModel(ref.key!!, "Second Name", imagePath)

        ref.setValue(model)
    }

    private fun showData() {

        val query: Query = FirebaseDatabase.getInstance()
            .reference
            .child("Users")
            .limitToLast(50)

        val options: FirebaseRecyclerOptions<UserModel> =
            FirebaseRecyclerOptions.Builder<UserModel>()
                .setQuery(query, UserModel::class.java)
                .build()

        adapter =
            object : FirebaseRecyclerAdapter<UserModel, UserHolder>(options) {

                override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserHolder {
                    // Create a new instance of the ViewHolder, in this case we are using a custom
                    // layout called R.layout.message for each item
                    val view: View = LayoutInflater.from(parent.context)
                        .inflate(R.layout.user_layout, parent, false)
                    return UserHolder(view)
                }

                protected override fun onBindViewHolder(
                    holder: UserHolder,
                    position: Int,
                    model: UserModel
                ) {

                    holder.textView.text = model.name

                    Glide.with(this@MainActivity)
                        .load(model.image)
                        .into(holder.imageView)

                    holder.itemView.setOnClickListener {

                        //deleteUser(model)

                        updateUser(model)

                    }

                }
            }

        recyclerView.adapter = adapter
    }

    private fun updateUser(model: UserModel) {
        val database = Firebase.database
        var ref = database.getReference("Users")
        ref = ref.child(model.id)

        model.name = "new Name"
        ref.setValue(model)
    }

    private fun deleteUser(model: UserModel) {
        val database = Firebase.database
        var ref: DatabaseReference = database.getReference("Users")
        ref = ref.child(model.id)
        ref.removeValue()
    }

    class UserHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        val textView = itemView.findViewById<TextView>(R.id.userName)

        val imageView = itemView.findViewById<ImageView>(R.id.imageView)

    }

    override fun onStart() {
        super.onStart()
        adapter?.startListening()
    }

    override fun onStop() {
        super.onStop()
        adapter?.stopListening()
    }

}