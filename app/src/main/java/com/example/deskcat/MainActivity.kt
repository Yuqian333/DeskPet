package com.example.deskcat

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.deskcat.overlay.DesktopPetOverlayService
import com.example.deskcat.overlay.OverlayPermissionHelper
import com.example.deskcat.pet.PetProgressRepository
import com.example.deskcat.pet.PetStateRepository
import com.example.deskcat.settings.PetPreferencesRepository
import com.example.deskcat.settings.PetSettingsViewModel
import com.example.deskcat.ui.theme.DeskCatTheme
import com.example.deskcat.weather.WeatherRepositoryProvider
import com.example.deskcat.weather.WeatherViewModel

class MainActivity : ComponentActivity() {
    private var overlayRunningState by mutableStateOf(false)
    private var overlayGrantedState by mutableStateOf(false)
    private lateinit var petPreferencesRepository: PetPreferencesRepository
    private var pendingImageUriHandler: ((Uri?) -> Unit)? = null
    private var pendingLocationHandler: ((Location?) -> Unit)? = null

    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            runCatching {
                contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
        pendingImageUriHandler?.invoke(uri)
        pendingImageUriHandler = null
    }

    private val locationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        pendingLocationHandler?.invoke(if (granted) lastKnownLocation() else null)
        pendingLocationHandler = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        petPreferencesRepository = PetPreferencesRepository(this)
        PetStateRepository.bindProgressRepository(PetProgressRepository(applicationContext))
        PetStateRepository.startIdleChatter()
        overlayGrantedState = OverlayPermissionHelper.canDrawOverlays(this)

        setContent {
            val settingsViewModel: PetSettingsViewModel = viewModel(
                factory = PetSettingsViewModel.Factory(petPreferencesRepository),
            )
            val weatherViewModel: WeatherViewModel = viewModel(
                factory = WeatherViewModel.Factory(WeatherRepositoryProvider.get(applicationContext)),
            )

            DeskCatTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    BakingScreen(
                        settingsViewModel = settingsViewModel,
                        weatherViewModel = weatherViewModel,
                        overlayGranted = overlayGrantedState,
                        overlayRunning = overlayRunningState,
                        onPickCustomImage = { onPicked ->
                            pendingImageUriHandler = onPicked
                            imagePickerLauncher.launch(arrayOf("image/*"))
                        },
                        onOpenOverlayPermission = {
                            startActivity(OverlayPermissionHelper.createManageOverlayPermissionIntent(this))
                        },
                        onStartOverlay = {
                            if (!OverlayPermissionHelper.canDrawOverlays(this)) {
                                overlayGrantedState = false
                                startActivity(OverlayPermissionHelper.createManageOverlayPermissionIntent(this))
                            } else {
                                overlayGrantedState = true
                                val intent = Intent(this, DesktopPetOverlayService::class.java)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    startForegroundService(intent)
                                } else {
                                    startService(intent)
                                }
                                overlayRunningState = true
                            }
                        },
                        onStopOverlay = {
                            stopService(Intent(this, DesktopPetOverlayService::class.java))
                            overlayRunningState = false
                        },
                        onRequestDeviceLocation = { onLocated ->
                            requestDeviceLocation { location ->
                                onLocated(location?.latitude, location?.longitude)
                            }
                        },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        overlayGrantedState = OverlayPermissionHelper.canDrawOverlays(this)
    }

    override fun onStop() {
        super.onStop()
        if (overlayRunningState) {
            startService(Intent(this, DesktopPetOverlayService::class.java).setAction(DesktopPetOverlayService.ACTION_COLLAPSE_TO_EDGE))
        }
    }

    private fun requestDeviceLocation(onLocated: (Location?) -> Unit) {
        if (hasLocationPermission()) {
            onLocated(lastKnownLocation())
            return
        }
        pendingLocationHandler = onLocated
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ),
        )
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    private fun lastKnownLocation(): Location? {
        if (!hasLocationPermission()) return null
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        )
        return providers
            .mapNotNull { provider ->
                runCatching {
                    if (locationManager.isProviderEnabled(provider)) {
                        locationManager.getLastKnownLocation(provider)
                    } else {
                        null
                    }
                }.getOrNull()
            }
            .maxByOrNull { it.time }
    }
}
