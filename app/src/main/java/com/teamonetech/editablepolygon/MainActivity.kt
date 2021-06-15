package com.teamonetech.editablepolygon

import MeasurementUnit
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.util.LongSparseArray
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.NonNull
import androidx.appcompat.app.AppCompatActivity
import androidx.collection.forEach
import androidx.core.content.ContextCompat
import androidx.core.util.forEach
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.bottomsheet.BottomSheetBehavior
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
    private var bottomLt: LinearLayout? = null
    private var bottomText: TextView? = null
    private var hectarTv: TextView? = null
    private var sqMeterTv: TextView? = null
    private var informationText: TextView? = null
    private var arrowIv: ImageView? = null
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
        bottomText = findViewById(R.id.bottom_area_text)
        bottomLt = findViewById(R.id.linearLayout)
        arrowIv = findViewById(R.id.bottom_arrow)
        hectarTv = findViewById(R.id.hectar_tv)
        sqMeterTv = findViewById(R.id.sq_meter_tv)
        informationText = findViewById(R.id.bottom_information_text)
        mapView?.onCreate(savedInstanceState)
        arrowIv?.setOnClickListener {
            val bottomBehaviour = BottomSheetBehavior.from(bottomLt!!)
            when (bottomBehaviour.state) {
                BottomSheetBehavior.STATE_EXPANDED -> {
                    closeBottomSheet()
                }
                BottomSheetBehavior.STATE_COLLAPSED -> {
                    openBottomSheet()

                }
            }
        }
        initializeObservers()
        mapView?.getMapAsync { mapboxMap ->
            this.mapboxMap = mapboxMap

            mapboxMap.setStyle(Style.SATELLITE) {
                addMarkerIconStyle(it)
                symbolManager = SymbolManager(mapView!!, mapboxMap!!, mapboxMap?.style!!)
                symbolManager.addClickListener(object : OnSymbolClickListener {
                    override fun onAnnotationClick(t: Symbol?): Boolean {
                        viewModel.onAnnotationClick(t)
                        highlightSymbol(t)
                        Log.d("click click", t?.id.toString());

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

    private fun initializeObservers() {
        viewModel.polyhashList.observe(this, Observer {
            if (it == null || it.values.size == 0) {
                return@Observer
            }
            drawPolygon(it)
            val area = viewModel.calculateArea()
            if(area==null){
                bottomText?.text = ""
            }
            area?.let {
                val acre = viewModel.convertArea(MeasurementUnit.acre, area)
                bottomText?.text = "Area: $acre "
                val hectar = viewModel.convertArea(MeasurementUnit.hectar,area)
                hectarTv?.text = hectar
                val sqMeter = viewModel.convertArea(MeasurementUnit.sqMeter,area)
                sqMeterTv?.text = sqMeter
                openBottomSheet()

            }

        })

        viewModel.message.observe(this, Observer {
            informationText?.text = (it)
        })
    }

    private fun openBottomSheet() {
        val bottomBehaviour = BottomSheetBehavior.from(bottomLt!!)
        bottomBehaviour.state = BottomSheetBehavior.STATE_EXPANDED
        arrowIv?.let {
            it.setImageDrawable(
                    ContextCompat.getDrawable(
                            it.context,
                            R.drawable.ic_arrow_down
                    )
            )
        }
    }

    private fun closeBottomSheet() {
        val bottomBehaviour = BottomSheetBehavior.from(bottomLt!!)
        bottomBehaviour.state = BottomSheetBehavior.STATE_COLLAPSED
        arrowIv?.let {
            it.setImageDrawable(
                    ContextCompat.getDrawable(
                            it.context,
                            R.drawable.ic_arrow_up
                    )
            )
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
            val items =
                    it.first.values.toList().map { it -> Point.fromLngLat(it.longitude, it.latitude) }
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

        style.addImage(
                ID_ICON_LOCATION_SELECTED, BitmapUtils.getBitmapFromDrawable(
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
