package com.teamonetech.editablepolygon

import MeasurementUnit
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.mapbox.geojson.Feature
import com.mapbox.geojson.Point
import com.mapbox.geojson.Polygon
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.plugins.annotation.Symbol
import com.mapbox.turf.TurfMeasurement

class MainViewModel : ViewModel() {
    private val _polyHashList: MutableLiveData<HashMap<String, Pair<LinkedHashMap<String, LatLng>, Boolean>>> =
            MutableLiveData(HashMap())

    var polyhashList: LiveData<HashMap<String, Pair<LinkedHashMap<String, LatLng>, Boolean>>> =
            _polyHashList

    private var rootId: String? = null
    private var newPolygon = false

    private var _message: MutableLiveData<String> = MutableLiveData("Click a point on the Map")
    val message: LiveData<String> = _message


    fun addPoint(point: LatLng, symbol: Symbol) {


        if (_polyHashList.value?.values?.isEmpty() == true) {
            rootId = symbol.id.toString()
        } else if (newPolygon) {
            newPolygon = false
            rootId = symbol.id.toString()
        }


        if (_polyHashList.value?.get(rootId) == null) {
            _polyHashList.value?.put(
                    rootId!!,
                    Pair(linkedMapOf(symbol.id.toString() to point), false)
            )
        } else {
            _message.postValue("Complete the polygon. Hold the point to reposition.")
            _polyHashList.value?.get(rootId)?.first?.put(symbol.id.toString(), point)
        }

        _polyHashList.value = _polyHashList.value

    }

    fun annotationDrag(annotation: Symbol?) {
        val polyHashList = _polyHashList.value
        val polyKey = polyHashList?.filter {
            it.value.first.filterKeys { it.equals(annotation!!.id.toString()) }.keys.isNotEmpty()
        }?.keys?.first().toString()
        polyHashList?.get(polyKey)?.first?.put(annotation!!.id.toString(), annotation!!.latLng)

        _polyHashList.value = polyHashList

    }

    fun undoClick(): String? {
        val polyHashList = _polyHashList.value
        val key = polyHashList?.keys?.firstOrNull()


        val lastKey = polyHashList?.get(key)?.first?.keys?.lastOrNull()

        if (key == null || lastKey == null) {
            return null
        }


        polyHashList?.get(key)?.let {
            if (it.second) {
                polyHashList.put(key, it.copy(second = false))
            } else {
                it.first.remove(lastKey)
            }
            _polyHashList.value = polyHashList

            if (it.second) {
                return null
            } else {
                return lastKey
            }
        }
        return null
    }

    fun onAnnotationClick(annotation: Symbol?) {
        val polyHashList = _polyHashList.value
        if (polyHashList?.keys?.firstOrNull().equals(annotation?.id.toString())) {
            //Polygon completed
            val polykey = polyHashList?.keys?.firstOrNull()
            polykey?.let {
                _message.postValue("")
                polyHashList.put(it, Pair(polyHashList.get(polykey)!!.first, true))

            }
        }
        _polyHashList.value = polyHashList
    }

    //Area in Acre
    fun calculateArea(): Double? {
        val data = _polyHashList.value
        val key = data?.keys?.firstOrNull()
        key?.let {
            val polygon = data[key]?.first
            val completed = data[key]?.second ?: false
            if (completed) {
                val poly = polygon?.values


                val pointList = poly?.map { Point.fromLngLat(it.longitude, it.latitude) }?.toMutableList()
                pointList?.firstOrNull()?.let {
                    pointList.add(it)
                }
                val feature = Feature.fromGeometry(Polygon.fromLngLats(listOf(pointList)))
                val area = (TurfMeasurement.area(feature))
                return area

            } else {
                return null
            }


        }
        return null
    }

    fun convertArea(unit: MeasurementUnit, area: Double): String {
        return when (unit) {
            MeasurementUnit.acre -> {
                val acre = area * 0.000247
                String.format("%.3f acre", acre)
            }
            MeasurementUnit.hectar -> {
                val hectare = area / 10000
                String.format("%.3f hectare", hectare)
            }
            MeasurementUnit.sqMeter -> {
                val sqMeter = area
                String.format("%.3f sq meter", sqMeter)
            }

        }

    }

}
