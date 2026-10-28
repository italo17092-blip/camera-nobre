package com.nobre.cameranobre.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import androidx.exifinterface.media.ExifInterface
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

class LocationHelper(private val context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    @Volatile
    var lastKnownLocation: Location? = null
        private set

    @SuppressLint("MissingPermission")
    fun updateLocation(onLocationReceived: ((Location?) -> Unit)? = null) {
        try {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { loc ->
                    if (loc != null) {
                        lastKnownLocation = loc
                    }
                    onLocationReceived?.invoke(lastKnownLocation)
                }
                .addOnFailureListener {
                    fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                        if (loc != null) {
                            lastKnownLocation = loc
                        }
                        onLocationReceived?.invoke(lastKnownLocation)
                    }
                }
        } catch (e: Exception) {
            e.printStackTrace()
            onLocationReceived?.invoke(null)
        }
    }

    fun injectGpsToExif(imageFile: File, loc: Location? = lastKnownLocation) {
        if (loc == null) return
        try {
            val exif = ExifInterface(imageFile.absolutePath)
            val lat = loc.latitude
            val lon = loc.longitude

            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, convertToDegreeMinuteSeconds(lat))
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, if (lat >= 0) "N" else "S")
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, convertToDegreeMinuteSeconds(lon))
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, if (lon >= 0) "E" else "W")

            if (loc.hasAltitude()) {
                val alt = loc.altitude
                exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE, "${(abs(alt) * 1000).toInt()}/1000")
                exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF, if (alt >= 0) "0" else "1")
            }

            val dateFormat = SimpleDateFormat("yyyy:MM:dd", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val now = Date(loc.time.takeIf { it > 0 } ?: System.currentTimeMillis())
            exif.setAttribute(ExifInterface.TAG_GPS_DATESTAMP, dateFormat.format(now))
            exif.setAttribute(ExifInterface.TAG_GPS_TIMESTAMP, timeFormat.format(now))

            // Identificação para enriquecer a exibição da Galeria One UI
            exif.setAttribute(ExifInterface.TAG_MAKE, "Samsung")
            exif.setAttribute(ExifInterface.TAG_MODEL, "Galaxy S25 Ultra")
            exif.setAttribute(ExifInterface.TAG_SOFTWARE, "Câmera Nobre")

            exif.saveAttributes()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun convertToDegreeMinuteSeconds(coordinate: Double): String {
        val absCoord = abs(coordinate)
        val degrees = absCoord.toInt()
        val minutesDouble = (absCoord - degrees) * 60.0
        val minutes = minutesDouble.toInt()
        val seconds = ((minutesDouble - minutes) * 60.0 * 1000).toInt()
        return "$degrees/1,$minutes/1,$seconds/1000"
    }
}
