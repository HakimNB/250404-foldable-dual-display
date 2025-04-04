package com.hakimh.example.dualdisplay

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.window.area.WindowAreaCapability
import androidx.window.area.WindowAreaController
import androidx.window.area.WindowAreaInfo
import androidx.window.area.WindowAreaPresentationSessionCallback
import androidx.window.area.WindowAreaSession
import androidx.window.area.WindowAreaSessionPresenter
import com.hakimh.example.dualdisplay.databinding.ActivityInnerScreenBinding
import com.hakimh.example.dualdisplay.databinding.ActivityOuterScreenBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.concurrent.Executor

class MainActivity : ComponentActivity(), WindowAreaPresentationSessionCallback {

    private lateinit var binding: ActivityInnerScreenBinding
    private lateinit var outerbinding: ActivityOuterScreenBinding

    private lateinit var windowAreaController: WindowAreaController
    private lateinit var displayExecutor: Executor
    private var windowAreaSession: WindowAreaSession? = null
    private var windowAreaInfo: WindowAreaInfo? = null
    private var capabilityStatus: WindowAreaCapability.Status =
        WindowAreaCapability.Status.WINDOW_AREA_STATUS_UNSUPPORTED

    private val presentOperation = WindowAreaCapability.Operation.OPERATION_PRESENT_ON_AREA

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityInnerScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toggleButton.setOnClickListener{
            Log.d("DualDisplay", "button clicked")
            toggleDualScreenMode()
        }

        enableEdgeToEdge()

        displayExecutor = ContextCompat.getMainExecutor(this)
        windowAreaController = WindowAreaController.getOrCreate()

        updateCapabilities()

    }

    private fun toggleDualScreenMode() {
        //here we are not checking the status because of b/302183399
        if(windowAreaSession != null) {
            Log.d("DualDisplay", "toggleDualScreenMode windowAreaSession is not null")
            windowAreaSession?.close()
        }
        else {
            Log.d("DualDisplay", "toggleDualScreenMode windowAreaSession is null")
            windowAreaInfo?.token?.let { token ->
                windowAreaController.presentContentOnWindowArea(
                    token = token,
                    activity = this,
                    executor = displayExecutor,
                    windowAreaPresentationSessionCallback = this
                )
            }
        }
    }

    private fun updateCapabilities() {
        lifecycleScope.launch(Dispatchers.Main) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                windowAreaController.windowAreaInfos
                    .map { info -> info.firstOrNull { it.type == WindowAreaInfo.Type.TYPE_REAR_FACING } }
                    .onEach { info -> windowAreaInfo = info }
                    .map { it?.getCapability(presentOperation)?.status ?: WindowAreaCapability.Status.WINDOW_AREA_STATUS_UNSUPPORTED }
                    .distinctUntilChanged()
                    .collect {
                        capabilityStatus = it
                        updateUI()
                    }
            }
        }
    }

    private fun updateUI() {
        Log.d("DualDisplay", "updateUI")
        if(windowAreaSession != null) {
            Log.d("DualDisplay", "windowAreaSession is not null")
        } else {
            when(capabilityStatus) {
                WindowAreaCapability.Status.WINDOW_AREA_STATUS_UNSUPPORTED -> {
                    binding.toggleButton.isEnabled = false
                    binding.status.text = "Dual Screen is not supported on this device"
                    Log.d("DualDisplay", "capabilityStatus: WINDOW_AREA_STATUS_UNSUPPORTED")
                }
                WindowAreaCapability.Status.WINDOW_AREA_STATUS_UNAVAILABLE -> {
                    binding.toggleButton.isEnabled = false
                    binding.status.text = "Dual Screen is not currently available"
                    Log.d("DualDisplay", "capabilityStatus: WINDOW_AREA_STATUS_UNAVAILABLE")
                }
                WindowAreaCapability.Status.WINDOW_AREA_STATUS_AVAILABLE -> {
                    binding.toggleButton.isEnabled = true
                    binding.status.text = "Dual Screen Mode is available"
                    Log.d("DualDisplay", "capabilityStatus: WINDOW_AREA_STATUS_AVAILABLE")
                }
                else -> {
//                    binding.toggleButton.isEnabled = false
//                    binding.status.text = "Dual Screen status is unknown"
                    Log.d("DualDisplay", "capabilityStatus: UNKNOWN")
                }
            }
        }
    }

    override fun onSessionStarted(session: WindowAreaSessionPresenter) {
        Log.d("DualDisplay", "onSessionStarted")
        windowAreaSession = session
        outerbinding = ActivityOuterScreenBinding.inflate(layoutInflater)
        session.setContentView(outerbinding.root)
        updateUI()
    }

    override fun onSessionEnded(t: Throwable?) {
        if(t != null) {
            Log.e("DualDisplay", "Something was broken: ${t.message}")
        }
        windowAreaSession = null
    }

    override fun onContainerVisibilityChanged(isVisible: Boolean) {
        Log.d("DualDisplay", "onContainerVisibilityChanged. isVisible = $isVisible")
    }

}
