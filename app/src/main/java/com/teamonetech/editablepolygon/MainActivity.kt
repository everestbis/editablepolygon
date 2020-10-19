package com.teamonetech.editablepolygon

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.annotation.NonNull
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.Point
import com.mapbox.geojson.Polygon
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.camera.CameraPosition
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.mapboxsdk.plugins.annotation.OnSymbolDragListener
import com.mapbox.mapboxsdk.plugins.annotation.Symbol
import com.mapbox.mapboxsdk.plugins.annotation.SymbolManager
import com.mapbox.mapboxsdk.plugins.annotation.SymbolOptions
import com.mapbox.mapboxsdk.style.layers.FillLayer
import com.mapbox.mapboxsdk.style.layers.PropertyFactory
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource
import com.mapbox.mapboxsdk.utils.BitmapUtils
import kotlinx.android.synthetic.main.activity_main.*


class MainActivity : AppCompatActivity(), MapboxMap.OnMapClickListener {
    companion object {
        private val TAG = MainActivity::class.qualifiedName
        private val FILL_LAYER_ID = "fill-layer-id"
        private val FILL_SOURCE_ID = "fill-source-id"
        private val ID_ICON_LOCATION = "location"

    }
    private var mapView: MapView? = null
    private var mapboxMap: MapboxMap? = null
    private var fillSource: GeoJsonSource? = null
    val polyHashList: HashMap<String, LinkedHashMap<String, LatLng>> = HashMap()
    private var rootSymbolId: String? = null
    private var newPolygon: Boolean = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Mapbox.getInstance(this, BuildConfig.MapboxAccessToken)
        setContentView(R.layout.activity_main)
        mapView = findViewById(R.id.mapView)
        mapView?.onCreate(savedInstanceState)
        Toast.makeText(this, "Click to add polygon point", Toast.LENGTH_SHORT).show()
        mapView?.getMapAsync { mapboxMap ->
            this.mapboxMap = mapboxMap
            mapboxMap.addOnMapClickListener(this)
            mapboxMap.setStyle(Style.MAPBOX_STREETS) {
                addMarkerIconStyle(it)
// Map is set up and the style has loaded. Now you can add data or make other map adjustments
                fillSource = initFillSource(it)
                initFillLayer(it)

                val position = CameraPosition.Builder()
                    .target(LatLng(51.50550, -0.07520))
                    .zoom(8.0)
                    .build()

                mapboxMap.animateCamera(CameraUpdateFactory.newCameraPosition(position), 2000);
            }

        }

        finish_btn.setOnClickListener(View.OnClickListener {
            newPolygon = true

        })


    }

    private fun initFillSource(@NonNull loadedMapStyle: Style): GeoJsonSource? {
        val fillFeatureCollection =
            FeatureCollection.fromFeatures(arrayOf())
        val fillGeoJsonSource = GeoJsonSource(FILL_SOURCE_ID, fillFeatureCollection)
        loadedMapStyle.addSource(fillGeoJsonSource)
        return fillGeoJsonSource
    }

    private fun initFillLayer(loadedMapStyle: Style) {
        val fillLayer = FillLayer(
            FILL_LAYER_ID,
            FILL_SOURCE_ID
        )
        fillLayer.setProperties(
            PropertyFactory.fillOpacity(.6f),
            PropertyFactory.fillColor(Color.parseColor("#00e9ff"))
        )
        loadedMapStyle.addLayer(fillLayer)
    }


    override fun onMapClick(point: LatLng): Boolean {
        Log.d(TAG, "Map clicked")


        var symbolManager = SymbolManager(mapView!!, mapboxMap!!, mapboxMap?.style!!)


        var symbolOptions: SymbolOptions =
            SymbolOptions().withLatLng(point).withIconImage(ID_ICON_LOCATION).withDraggable(true)
                .withIconSize(2f)


        if (polyHashList.values.isEmpty()) {
            rootSymbolId = symbolManager.layerId
        } else if (newPolygon) {
            newPolygon = false
            rootSymbolId = symbolManager.layerId
        }

        symbolManager.create(symbolOptions)
        symbolManager.iconAllowOverlap = true

        if (polyHashList.get(rootSymbolId) == null) {
            polyHashList.put(rootSymbolId!!, linkedMapOf(symbolManager.layerId to point))
        } else {
            polyHashList.get(rootSymbolId)?.put(symbolManager.layerId, point)
        }

        Log.d(TAG, "layer id " + symbolManager.layerId)
        Log.d(TAG, polyHashList.toString())

        symbolManager.addDragListener(object : OnSymbolDragListener {
            override fun onAnnotationDragStarted(annotation: Symbol?) {
                return
            }

            override fun onAnnotationDrag(annotation: Symbol?) {
                annotation?.latLng
                Log.d(TAG, "symbol layer id" + symbolManager.layerId)

                val polyKey = polyHashList.filter {
                    it.value.filterKeys { it.equals(symbolManager.layerId) }.keys.size > 0
                }.keys.first().toString()
                Log.d(TAG, "drag poly key " + polyKey)
                polyHashList.get(polyKey)?.put(symbolManager.layerId, annotation!!.latLng)


                drawPolygon()
                return
            }

            override fun onAnnotationDragFinished(annotation: Symbol?) {

                return
            }
        })


        drawPolygon()


        return false

    }

    private fun drawPolygon() {
        val points = polyHashList.values

        val latLngs: List<List<Point>> = points.map {
            val items = it.values.toList().map { it -> Point.fromLngLat(it.longitude, it.latitude) }
                .toMutableList()
            items.add(items.get(0))
            items
        }


        val finalFeatureList: MutableList<Feature> = ArrayList()
        finalFeatureList.add(Feature.fromGeometry(Polygon.fromLngLats(latLngs)))

        if (fillSource != null) {
            fillSource?.setGeoJson(
                FeatureCollection.fromFeatures(
                    listOf(
                        Feature.fromGeometry(Polygon.fromLngLats(latLngs))
                    )
                )
            );

        }


    }


    private fun addMarkerIconStyle(style: Style) {
        style.addImage(
            ID_ICON_LOCATION,
            BitmapUtils.getBitmapFromDrawable(
                ContextCompat.getDrawable(
                    this,
                    R.drawable.ic_location_on_black_24dp
                )
            )!!,
            true
        )
    }


    override fun onStart() {
        super.onStart()
        mapView?.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView?.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView?.onPause()
    }

    override fun onStop() {
        super.onStop()
        mapView?.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView?.onSaveInstanceState(outState)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView?.onLowMemory()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView?.onDestroy()
    }

}
