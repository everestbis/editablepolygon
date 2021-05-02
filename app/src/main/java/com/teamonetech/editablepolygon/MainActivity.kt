package com.teamonetech.editablepolygon

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.util.LongSparseArray
import androidx.annotation.NonNull
import androidx.appcompat.app.AppCompatActivity
import androidx.collection.forEach
import androidx.core.content.ContextCompat
import androidx.core.util.forEach
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.camera.CameraPosition
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.location.LocationComponent
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.mapboxsdk.plugins.annotation.*
import com.mapbox.mapboxsdk.style.layers.LineLayer
import com.mapbox.mapboxsdk.style.layers.PropertyFactory
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource
import com.mapbox.mapboxsdk.utils.BitmapUtils
import kotlinx.android.synthetic.main.activity_main.*


fun <T> LongSparseArray<T>.getList(): List<T> {
    val list = ArrayList<T>()
    forEach { _, value ->
        list.add(value)
    }
    return list.toList()
}

class MainActivity : AppCompatActivity(), MapboxMap.OnMapClickListener {
    companion object {
        private val TAG = MainActivity::class.qualifiedName
        private val FILL_LAYER_ID = "fill-layer-id"
        private val FILL_SOURCE_ID = "fill-source-id"
        private val ID_ICON_LOCATION = "location"
        private val ID_ICON_LOCATION_SELECTED = "location_selected"

    }

    private lateinit var locationComponent: LocationComponent

    private var mapView: MapView? = null
    private var mapboxMap: MapboxMap? = null
    private var fillSource: GeoJsonSource? = null
    lateinit var symbolManager: SymbolManager;
    lateinit var viewModel: MainViewModel


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Mapbox.getInstance(this, getString(R.string.mapbox_access_token))
        setContentView(R.layout.activity_main)
        viewModel = ViewModelProvider(
                this,
                ViewModelProvider.NewInstanceFactory()
        ).get(MainViewModel::class.java)
        mapView = findViewById(R.id.mapView)
        mapView?.onCreate(savedInstanceState)
        viewModel.polyhashList.observe(this, Observer {
            if (it == null || it.values.size == 0) {
                return@Observer
            }
            drawPolygon(it)
        })
        mapView?.getMapAsync { mapboxMap ->
            this.mapboxMap = mapboxMap

            mapboxMap.setStyle(Style.SATELLITE) {
                addMarkerIconStyle(it)
                symbolManager = SymbolManager(mapView!!, mapboxMap!!, mapboxMap?.style!!)
                symbolManager.addClickListener(object : OnSymbolClickListener {
                    override fun onAnnotationClick(t: Symbol?): Boolean {
                        highlightSymbol(t)
                        Log.d("click click", t?.id.toString());
                        viewModel.onAnnotationClick(t)
                        return true
                    }
                })

                symbolManager.addDragListener(object : OnSymbolDragListener {
                    override fun onAnnotationDragStarted(annotation: Symbol?) {
                        highlightSymbol(annotation)
                        return
                    }

                    override fun onAnnotationDrag(annotation: Symbol?) {


                        viewModel.annotationDrag(annotation)
                        return
                    }

                    override fun onAnnotationDragFinished(annotation: Symbol?) {

                        return
                    }
                })

// Map is set up and the style has loaded. Now you can add data or make other map adjustments
                fillSource = initFillSource(it)
                initFillLayer(it)
                mapboxMap.addOnMapClickListener(this)

                val position = CameraPosition.Builder()
                        .target(LatLng(51.50550, -0.07520))
                        .zoom(14.0)
                        .build()

                mapboxMap.animateCamera(CameraUpdateFactory.newCameraPosition(position), 2000);

            }

        }
        undo_fab.setOnClickListener {
            val lastKey = viewModel.undoClick()

            lastKey?.let {
                symbolManager.annotations.forEach { key, value ->
                    if (key.toString().equals(lastKey)) {
                        symbolManager.delete(value)
                    }
                }
//            drawPolygon(it)
            }

//        finish_btn.setOnClickListener(View.OnClickListener {
//            newPolygon = true
//
//        })


        }
    }

    fun highlightSymbol(t: Symbol?) {
        var symbols = symbolManager.annotations
        symbols.forEach { k, v -> v.iconImage = ID_ICON_LOCATION }
        val lists = ArrayList<Symbol>(symbols.size())
        symbols.forEach { k, v ->
            lists.add(v)
        }

        symbolManager.update(lists)
        t?.iconImage = ID_ICON_LOCATION_SELECTED
        symbolManager.update(t)
    }

    private fun initFillSource(@NonNull loadedMapStyle: Style): GeoJsonSource? {
        val fillFeatureCollection =
                FeatureCollection.fromFeatures(arrayOf())
        val fillGeoJsonSource = GeoJsonSource(FILL_SOURCE_ID, fillFeatureCollection)
        loadedMapStyle.addSource(fillGeoJsonSource)
        return fillGeoJsonSource
    }

    private fun initFillLayer(loadedMapStyle: Style) {
        val fillLayer = LineLayer(
                FILL_LAYER_ID,
                FILL_SOURCE_ID
        )
        fillLayer.setProperties(
                PropertyFactory.lineWidth(2.5f),
                PropertyFactory.lineColor(Color.parseColor("#ffffff"))
        )
//        loadedMapStyle.addLayer(fillLayer)
        loadedMapStyle.addLayerBelow(fillLayer, symbolManager.layerId)

    }


    override fun onMapClick(point: LatLng): Boolean {
        val key = viewModel.polyhashList.value?.keys?.firstOrNull()
        val isCompleted = viewModel.polyhashList.value?.get(key)?.second ?: false

        if (isCompleted) {
            return false
        }


        var symbolOptions: SymbolOptions =
                SymbolOptions().withLatLng(point).withIconImage(ID_ICON_LOCATION).withDraggable(true)
                        .withIconSize(1f)

        val symbol = symbolManager.create(symbolOptions)

        viewModel.addPoint(point, symbol)


        symbolManager.iconAllowOverlap = true
        return false

    }

    private fun drawPolygon(hashMap: HashMap<String, Pair<LinkedHashMap<String, LatLng>, Boolean>>) {
        val points = hashMap.values

        val latLngs: List<Point> = points.map {
            val items = it.first.values.toList().map { it -> Point.fromLngLat(it.longitude, it.latitude) }
                    .toMutableList()
            if (it.second) {
                items.add(items.get(0))
            }
            items
        }.first()

        Log.d("final", latLngs.toString())
        val finalFeatureList: MutableList<Feature> = ArrayList()
        finalFeatureList.add(Feature.fromGeometry(LineString.fromLngLats(latLngs)))

        if (fillSource != null) {
            fillSource?.setGeoJson(
                    FeatureCollection.fromFeatures(
                            listOf(
                                    Feature.fromGeometry(LineString.fromLngLats(latLngs))
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
                                R.drawable.ic_marker
                        )
                )!!,
                false
        )

        style.addImage(ID_ICON_LOCATION_SELECTED, BitmapUtils.getBitmapFromDrawable(
                ContextCompat.getDrawable(
                        this,
                        R.drawable.marker_selected
                )
        )!!,
                false
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
