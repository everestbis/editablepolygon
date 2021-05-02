package com.teamonetech.editablepolygon

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.plugins.annotation.Symbol

class MainViewModel : ViewModel() {
    private val _polyHashList: MutableLiveData<HashMap<String, Pair<LinkedHashMap<String, LatLng>, Boolean>>> =
        MutableLiveData(HashMap())

    var polyhashList: LiveData<HashMap<String, Pair<LinkedHashMap<String, LatLng>, Boolean>>> =
        _polyHashList

    private var rootId: String? = null
    private var newPolygon = false


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
            val polykey = polyHashList?.keys?.firstOrNull()
            polykey?.let {
                polyHashList.put(it, Pair(polyHashList.get(polykey)!!.first, true))

            }
        }
        _polyHashList.value = polyHashList
    }

}
