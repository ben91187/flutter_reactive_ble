package com.signify.hue.flutterreactiveble.channelhandlers

import com.signify.hue.flutterreactiveble.ProtobufModel as pb
import com.signify.hue.flutterreactiveble.converters.ProtobufMessageConverter
import com.signify.hue.flutterreactiveble.converters.UuidConverter
import io.flutter.plugin.common.EventChannel
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import android.util.Log
import java.util.UUID

private const val tag: String = "CharCentralNotificationHandler"

class CharCentralNotificationHandler(private val bleClient: com.signify.hue.flutterreactiveble.ble.BleClient) :
    EventChannel.StreamHandler {
    private val uuidConverter = UuidConverter()
    private val protobufConverter = ProtobufMessageConverter()

    private var charCentralNotificationSink: EventChannel.EventSink? = null
    private lateinit var charRequestDisposable: Disposable

    override fun onListen(objectSink: Any?, eventSink: EventChannel.EventSink?) {
        eventSink?.let {
            Log.i(tag, "onListen")
            charCentralNotificationSink = eventSink
            charRequestDisposable = listenToCharRequest()
        }
    }

    override fun onCancel(objectSink: Any?) {
        Log.i(tag, "onCancel")
        charRequestDisposable.dispose()
    }

    private fun listenToCharRequest() = bleClient.charRequestSubject
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe { charResult ->
            when (charResult) {
                is com.signify.hue.flutterreactiveble.ble.CharOperationSuccessful -> {
                    Log.i(tag, "Write request forworded")
                    handleValue(charResult.deviceId, charResult.value.toByteArray())
                }
                else -> {
                    Log.i(tag, "Write request forworded failed")
                }
            }
        }

    private fun handleValue(
        UuidString: String,
        value: ByteArray
    ) {
        Log.i(tag, "handleValue")

        val characteristicAddress = protobufConverter.convertToCharacteristicAddress(
            "",
            UUID.fromString(UuidString),
            UUID.fromString(UuidString)
        )
        val convertedMsg = protobufConverter.convertCharacteristicInfo(characteristicAddress, value)
        charCentralNotificationSink?.success(convertedMsg.toByteArray())
    }
}
