package com.example.virtualfittingroom

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.ar.core.CameraNotAvailableException
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.ux.ArFragment
import com.google.ar.sceneform.ux.TransformableNode
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import kotlin.concurrent.thread

class VirtualFittingRoomActivity : AppCompatActivity() {
    private lateinit var arFragment: ArFragment
    private var modelRenderable: ModelRenderable? = null
    private val TAG = "VirtualFittingRoom"
    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_virtual_fitting_room)
        
        initializeAR()
        setupButtons()
    }

    private fun initializeAR() {
        arFragment = supportFragmentManager.findFragmentById(R.id.ar_fragment) as ArFragment
        
        arFragment.setOnTapArPlaneListener { hitResult, plane, motionEvent ->
            onArPlaneClicked(hitResult)
        }

        loadClothingModel()
    }

    private fun loadClothingModel() {
        ModelRenderable.builder()
            .setSource(this, android.net.Uri.parse("models/clothing.sfb"))
            .build()
            .thenAccept { renderable ->
                modelRenderable = renderable
                Log.d(TAG, "Clothing model loaded successfully")
            }
            .exceptionally { throwable ->
                Log.e(TAG, "Failed to load model", throwable)
                Toast.makeText(this, "Failed to load model", Toast.LENGTH_SHORT).show()
                null
            }
    }

    private fun onArPlaneClicked(hitResult: com.google.ar.core.HitTestResult) {
        if (modelRenderable == null) {
            Toast.makeText(this, "Model not loaded yet", Toast.LENGTH_SHORT).show()
            return
        }

        val anchor = hitResult.createAnchor()
        val anchorNode = AnchorNode(anchor)
        anchorNode.setParent(arFragment.arSceneView.scene)

        val transformableNode = TransformableNode(arFragment.transformationSystem)
        transformableNode.setParent(anchorNode)
        transformableNode.renderable = modelRenderable

        arFragment.transformationSystem.selectNode(transformableNode)
        Toast.makeText(this, "Clothing model placed!", Toast.LENGTH_SHORT).show()
        Log.d(TAG, "Model placed at anchor")
    }

    private fun setupButtons() {
        val sizeBtn: Button? = findViewById(R.id.btn_size_recommend)
        val fitBtn: Button? = findViewById(R.id.btn_fit_check)
        val removeBtn: Button? = findViewById(R.id.btn_remove)

        sizeBtn?.setOnClickListener { getSizeRecommendation() }
        fitBtn?.setOnClickListener { checkFit() }
        removeBtn?.setOnClickListener { 
            Toast.makeText(this, "Long press model to remove", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getSizeRecommendation() {
        thread {
            try {
                val json = JSONObject().apply {
                    put("height", 175)
                    put("chest", 95)
                    put("waist", 85)
                }

                val requestBody = json.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("http://192.168.1.100:5000/api/size-recommend")
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()
                val result = response.body?.string()?.let { JSONObject(it) }

                runOnUiThread {
                    if (result != null) {
                        val size = result.getString("recommended_size")
                        val confidence = result.getDouble("confidence")
                        Toast.makeText(this, "Recommended: $size (${(confidence * 100).toInt()}%)", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this, "Failed to get recommendation", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting recommendation", e)
                runOnUiThread {
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun checkFit() {
        thread {
            try {
                val json = JSONObject().apply {
                    put("measurements", JSONObject().apply {
                        put("height", 175)
                        put("chest", 95)
                        put("waist", 85)
                    })
                    put("clothing_size", "M")
                }

                val requestBody = json.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("http://192.168.1.100:5000/api/fit-check")
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()
                val result = response.body?.string()?.let { JSONObject(it) }

                runOnUiThread {
                    if (result != null) {
                        val score = result.getDouble("fit_score")
                        Toast.makeText(this, "Fit Score: ${(score * 100).toInt()}%", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this, "Failed to check fit", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking fit", e)
                runOnUiThread {
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            arFragment.arSceneView.resume()
        } catch (e: CameraNotAvailableException) {
            Log.e(TAG, "Camera not available", e)
        }
    }

    override fun onPause() {
        super.onPause()
        arFragment.arSceneView.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        arFragment.arSceneView.destroy()
    }
}
