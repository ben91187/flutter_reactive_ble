package com.signify.hue.flutterreactiveble.channelhandlers

import com.signify.hue.flutterreactiveble.ProtobufModel as pb
import com.signify.hue.flutterreactiveble.converters.ProtobufMessageConverter
import com.signify.hue.flutterreactiveble.converters.UuidConverter
import io.flutter.plugin.common.EventChannel
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import android.util.Log

private const val tag: String = "DidModifyServicesHandler"


class DidModifyServicesHandler(private val bleClient: com.signify.hue.flutterreactiveble.ble.BleClient) :
    EventChannel.StreamHandler {

    private var didModifyServicesEventSink: EventChannel.EventSink? = null
    private lateinit var didModifyServicesDisposable: Disposable


    override fun onListen(arg: Any?, eventSink: EventChannel.EventSink?) {
        eventSink?.let {
            Log.i(tag, "onListen")
            didModifyServicesEventSink = eventSink
            didModifyServicesDisposable = listenToModifyServices()
        }
    }

    private fun listenToModifyServices() = bleClient.didModifyServicesSubject
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe { result ->
            didModifyServicesEventSink?.success(null)
        }

    override fun onCancel(arg: Any?) {
        didModifyServicesDisposable.set(null)
    }
}
