package br.com.gruposetup.mdm

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

data class Fix(val location: Location, val provider: String)

object LocationRepository {

    private fun temGms(context: Context): Boolean =
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS

    @SuppressLint("MissingPermission")
    suspend fun obterLocalizacao(context: Context): Fix? {
        if (temGms(context)) {
            fusedFix(context)?.let { return it }
        }
        return legacyFix(context)
    }

    @SuppressLint("MissingPermission")
    private suspend fun fusedFix(context: Context): Fix? {
        val client = LocationServices.getFusedLocationProviderClient(context)
        val cts = CancellationTokenSource()
        return withTimeoutOrNull(30_000) {
            suspendCancellableCoroutine { cont ->
                client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
                    .addOnSuccessListener { loc -> if (cont.isActive) cont.resume(loc?.let { Fix(it, "fused") }) }
                    .addOnFailureListener { if (cont.isActive) cont.resume(null) }
                cont.invokeOnCancellation { cts.cancel() }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun legacyFix(context: Context): Fix? {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val provider = when {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> null
        }
        if (provider != null) {
            val fresh = withTimeoutOrNull(30_000) {
                suspendCancellableCoroutine { cont ->
                    val listener = object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            if (cont.isActive) {
                                val p = if (provider == LocationManager.GPS_PROVIDER) "gps" else "network"
                                cont.resume(Fix(location, p))
                            }
                        }
                        override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
                        override fun onProviderEnabled(p: String) {}
                        override fun onProviderDisabled(p: String) {}
                    }
                    try {
                        lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                        cont.invokeOnCancellation { try { lm.removeUpdates(listener) } catch (_: Exception) {} }
                    } catch (e: Exception) {
                        if (cont.isActive) cont.resume(null)
                    }
                }
            }
            if (fresh != null) return fresh
        }
        val last = listOfNotNull(
            safeLast(lm, LocationManager.GPS_PROVIDER),
            safeLast(lm, LocationManager.NETWORK_PROVIDER)
        ).maxByOrNull { it.time }
        return last?.let { Fix(it, "last_known") }
    }

    @SuppressLint("MissingPermission")
    private fun safeLast(lm: LocationManager, provider: String): Location? =
        try { lm.getLastKnownLocation(provider) } catch (e: Exception) { null }
}
