package dev.wordloom.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer

/**
 * Keyless vector basemap. OpenFreeMap serves OpenStreetMap data without a token,
 * so the build carries no API key and no secret to leak.
 *
 * Attribution stays enabled: OSM data requires it.
 */
object BasemapStyle {
    const val POSITRON = "https://tiles.openfreemap.org/styles/positron"
    const val BRIGHT = "https://tiles.openfreemap.org/styles/bright"
    const val LIBERTY = "https://tiles.openfreemap.org/styles/liberty"
}

/** Where the camera wanders when the map is left alone. */
private val TourStops = listOf(
    48.5 to 9.5,      // Europe
    31.0 to 118.0,    // East Asia
    41.0 to -98.0,    // North America
    -8.0 to -55.0,    // South America
    6.0 to 20.0,      // Africa
    18.0 to 78.0,     // South Asia
)

@Composable
fun WorldMap(
    modifier: Modifier = Modifier,
    styleUrl: String = BasemapStyle.LIBERTY,
    target: LatLng = LatLng(24.0, 12.0),
    zoom: Double = 1.4,
    interactive: Boolean = true,
    tour: Boolean = false,
    tourZoom: Double = 2.6,
    onMapReady: (MapLibreMap) -> Unit = {},
    onTourStop: () -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // The tour loop outlives recompositions; without this it would keep calling
    // the callback captured on the first composition (when the word list was
    // still empty), so the card would never change.
    val tourStop by rememberUpdatedState(onTourStop)
    var loadedMap by remember { mutableStateOf<MapLibreMap?>(null) }

    val mapView = remember(interactive) {
        // MapView consumes every touch even with gestures off, which would freeze
        // the feed it sits in. In passive mode we drop touches before the map sees them.
        val view: MapView = if (interactive) MapView(context) else PassiveMapView(context)
        view.apply {
            onCreate(null)
            isFocusable = false
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        mapView.getMapAsync { map ->
            map.uiSettings.apply {
                isRotateGesturesEnabled = false
                isTiltGesturesEnabled = false
                isZoomGesturesEnabled = interactive
                isScrollGesturesEnabled = interactive
                isDoubleTapGesturesEnabled = interactive
                isAttributionEnabled = true
                isCompassEnabled = false
                isLogoEnabled = false
            }
            map.setMinZoomPreference(-1.5)
            map.cameraPosition = CameraPosition.Builder()
                .target(target)
                .zoom(zoom)
                .build()
            map.setStyle(Style.Builder().fromUri(styleUrl)) { style ->
                style.preferEnglishLabels()
                loadedMap = map
                onMapReady(map)
            }
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)

    // The Bowl map moves by itself; ours drifts between regions on a slow loop.
    LaunchedEffect(tour, loadedMap) {
        val map = loadedMap ?: return@LaunchedEffect
        if (!tour) return@LaunchedEffect
        var index = 0
        while (true) {
            delay(1_500)
            val (lat, lng) = TourStops[index % TourStops.size]
            // One word per move: the card changes exactly when the camera leaves.
            tourStop()
            map.animateCamera(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder()
                        .target(LatLng(lat, lng))
                        .zoom(tourZoom)
                        .build()
                ),
                3_200,
            )
            index++
            delay(9_000)
        }
    }
}

/**
 * OpenFreeMap styles label places with `name`, which renders Greek, Arabic and
 * Cyrillic alongside the Latin name in one line. Pin every symbol layer to
 * `name:en` (falling back to `name`) so the map reads like an English atlas.
 */
private fun Style.preferEnglishLabels() {
    val english = Expression.coalesce(
        Expression.get("name:en"),
        Expression.get("name"),
        Expression.literal(""),
    )
    layers.filterIsInstance<SymbolLayer>().forEach { layer ->
        layer.setProperties(PropertyFactory.textField(english))
    }
}

/**
 * A MapView that never takes touch input. Used for the decorative card on the
 * home feed, where the parent list must keep scrolling.
 */
private class PassiveMapView(context: android.content.Context) : MapView(context) {
    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean = false
}

fun MapLibreMap.flyTo(target: LatLng, zoom: Double = 2.0) {
    cameraPosition = CameraPosition.Builder()
        .target(target)
        .zoom(zoom)
        .build()
}
