package com.signify.hue.flutterreactiveble

import android.content.Context
import com.signify.hue.flutterreactiveble.ble.EstablishConnectionResult
import com.signify.hue.flutterreactiveble.ble.RequestConnectionPriorityFailed
import com.signify.hue.flutterreactiveble.channelhandlers.*
import com.signify.hue.flutterreactiveble.converters.ProtobufMessageConverter
import com.signify.hue.flutterreactiveble.converters.UuidConverter
import com.signify.hue.flutterreactiveble.model.ClearGattCacheErrorType
import com.signify.hue.flutterreactiveble.utils.discard
import com.signify.hue.flutterreactiveble.utils.toConnectionPriority
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel.Result
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import java.util.*
import java.util.concurrent.*
import com.signify.hue.flutterreactiveble.ProtobufModel as pb
import android.util.Log


@Suppress("TooManyFunctions")
class PluginController {
    private val pluginMethods = mapOf<String, (call: MethodCall, result: Result) -> Unit>(
        "initialize" to this::initializeClient,
        "deinitialize" to this::deinitializeClient,
        "scanForDevices" to this::scanForDevices,
        "connectToDevice" to this::connectToDevice,
        "clearGattCache" to this::clearGattCache,
        "disconnectFromDevice" to this::disconnectFromDevice,
        "readCharacteristic" to this::readCharacteristic,
        "writeCharacteristicWithResponse" to this::writeCharacteristicWithResponse,
        "writeCharacteristicWithoutResponse" to this::writeCharacteristicWithoutResponse,
        "readNotifications" to this::readNotifications,
        "stopNotifications" to this::stopNotifications,
        "negotiateMtuSize" to this::negotiateMtuSize,
        "requestConnectionPriority" to this::requestConnectionPriority,
        "discoverServices" to this::discoverServices,
        "startAdvertising" to this::startAdvertising,
        "stopAdvertising" to this::stopAdvertising,
        "addGattService" to this::addGattService,
        "addGattCharacteristic" to this::addGattCharacteristic,
        "startGattServer" to this::startGattServer,
        "stopGattServer" to this::stopGattServer,
        "writeLocalCharacteristic" to this::writeLocalCharacteristic,
        "checkIfOldInetBoxBondingExists" to this::checkIfOldInetBoxBondingExists,
        "removeInetBoxBonding" to this::removeInetBoxBonding,
        "isDeviceConnected" to this::isDeviceConnected,
    )

    lateinit var bleClient: com.signify.hue.flutterreactiveble.ble.BleClient

    lateinit var scanchannel: EventChannel
    lateinit var deviceConnectionChannel: EventChannel
    lateinit var charNotificationChannel: EventChannel
    lateinit var centralConnectionChannel: EventChannel
    lateinit var charCentralNotifcationChannel: EventChannel
    lateinit var didModifyServicesChannel: EventChannel

    lateinit var scandevicesHandler: ScanDevicesHandler
    lateinit var deviceConnectionHandler: DeviceConnectionHandler
    lateinit var charNotificationHandler: CharNotificationHandler
    lateinit var centralConnectionHandler: CentralConnectionHandler
    lateinit var charCentralNotificationHandler: CharCentralNotificationHandler
    lateinit var didModifyServicesHandler: DidModifyServicesHandler

    private val uuidConverter = UuidConverter()
    private val protoConverter = ProtobufMessageConverter()

    private val tag: String = "ReactiveBleClient"

    internal fun initialize(messenger: BinaryMessenger, context: Context) {
        bleClient = com.signify.hue.flutterreactiveble.ble.ReactiveBleClient(context)

        scanchannel = EventChannel(messenger, "flutter_reactive_ble_scan")
        deviceConnectionChannel = EventChannel(messenger, "flutter_reactive_ble_connected_device")
        charNotificationChannel = EventChannel(messenger, "flutter_reactive_ble_char_update")
        val bleStatusChannel = EventChannel(messenger, "flutter_reactive_ble_status")
        centralConnectionChannel = EventChannel(messenger, "flutter_reactive_ble_connected_central")
        charCentralNotifcationChannel =
            EventChannel(messenger, "flutter_reactive_ble_char_update_central")
        didModifyServicesChannel =
            EventChannel(messenger, "flutter_reactive_ble_did_modify_services")

        scandevicesHandler = ScanDevicesHandler(bleClient)
        deviceConnectionHandler = DeviceConnectionHandler(bleClient)
        charNotificationHandler = CharNotificationHandler(bleClient)
        val bleStatusHandler = BleStatusHandler(bleClient)
        centralConnectionHandler = CentralConnectionHandler(bleClient)
        charCentralNotificationHandler = CharCentralNotificationHandler(bleClient)
        didModifyServicesHandler = DidModifyServicesHandler(bleClient)

        scanchannel.setStreamHandler(scandevicesHandler)
        deviceConnectionChannel.setStreamHandler(deviceConnectionHandler)
        charNotificationChannel.setStreamHandler(charNotificationHandler)
        bleStatusChannel.setStreamHandler(bleStatusHandler)
        centralConnectionChannel.setStreamHandler(centralConnectionHandler)
        charCentralNotifcationChannel.setStreamHandler(charCentralNotificationHandler)
        didModifyServicesChannel.setStreamHandler(didModifyServicesHandler)
    }

    internal fun deinitialize() {
        scandevicesHandler.stopDeviceScan()
        deviceConnectionHandler.disconnectAll()
    }

    internal fun execute(call: MethodCall, result: Result) {
        pluginMethods[call.method]?.invoke(call, result) ?: result.notImplemented()
    }

    private fun initializeClient(call: MethodCall, result: Result) {
        bleClient.initializeClient()
        result.success(null)
    }

    private fun deinitializeClient(call: MethodCall, result: Result) {
        deinitialize()
        result.success(null)
    }

    private fun scanForDevices(call: MethodCall, result: Result) {
        scandevicesHandler.prepareScan(pb.ScanForDevicesRequest.parseFrom(call.arguments as ByteArray))
        result.success(null)
    }

    private fun connectToDevice(call: MethodCall, result: Result) {
        result.success(null)
        val connectDeviceMessage = pb.ConnectToDeviceRequest.parseFrom(call.arguments as ByteArray)
        deviceConnectionHandler.connectToDevice(connectDeviceMessage)
    }

    private fun clearGattCache(call: MethodCall, result: Result) {
        val args = pb.ClearGattCacheRequest.parseFrom(call.arguments as ByteArray)
        bleClient.clearGattCache(args.deviceId)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                {
                    val info = pb.ClearGattCacheInfo.getDefaultInstance()
                    result.success(info.toByteArray())
                },
                {
                    val info = protoConverter.convertClearGattCacheError(
                        ClearGattCacheErrorType.UNKNOWN,
                        it.message
                    )
                    result.success(info.toByteArray())
                }
            )
            .discard()
    }

    private fun disconnectFromDevice(call: MethodCall, result: Result) {
        result.success(null)
        val connectDeviceMessage =
            pb.DisconnectFromDeviceRequest.parseFrom(call.arguments as ByteArray)
        deviceConnectionHandler.disconnectDevice(connectDeviceMessage.deviceId)
    }

    private fun readCharacteristic(call: MethodCall, result: Result) {
        result.success(null)

        val readCharMessage = pb.ReadCharacteristicRequest.parseFrom(call.arguments as ByteArray)
        val deviceId = readCharMessage.characteristic.deviceId
        val service =
            uuidConverter.uuidFromByteArray(readCharMessage.characteristic.serviceUuid.data.toByteArray())
        val characteristic =
            uuidConverter.uuidFromByteArray(readCharMessage.characteristic.characteristicUuid.data.toByteArray())

        bleClient.readCharacteristic(
            readCharMessage.characteristic.deviceId, service, characteristic
        )
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { charResult ->
                    when (charResult) {
                        is com.signify.hue.flutterreactiveble.ble.CharOperationSuccessful -> {
                            val charInfo = protoConverter.convertCharacteristicInfo(
                                readCharMessage.characteristic,
                                charResult.value.toByteArray()
                            )
                            charNotificationHandler.addSingleReadToStream(charInfo)
                        }

                        is com.signify.hue.flutterreactiveble.ble.CharOperationFailed -> {
                            protoConverter.convertCharacteristicError(
                                readCharMessage.characteristic,
                                "Failed to connect"
                            )
                            charNotificationHandler.addSingleErrorToStream(
                                readCharMessage.characteristic,
                                charResult.errorMessage
                            )
                        }
                    }
                },
                { throwable ->
                    protoConverter.convertCharacteristicError(
                        readCharMessage.characteristic,
                        throwable.message
                    )
                    charNotificationHandler.addSingleErrorToStream(
                        readCharMessage.characteristic,
                        throwable?.message ?: "Failure"
                    )
                }
            )
            .discard()
    }

    private fun writeCharacteristicWithResponse(call: MethodCall, result: Result) {
        executeWriteAndPropagateResultToChannel(
            call,
            result,
            com.signify.hue.flutterreactiveble.ble.BleClient::writeCharacteristicWithResponse
        )
    }

    private fun writeCharacteristicWithoutResponse(call: MethodCall, result: Result) {
        executeWriteAndPropagateResultToChannel(
            call,
            result,
            com.signify.hue.flutterreactiveble.ble.BleClient::writeCharacteristicWithoutResponse
        )
    }

    private fun executeWriteAndPropagateResultToChannel(
        call: MethodCall,
        result: Result,
        writeOperation: com.signify.hue.flutterreactiveble.ble.BleClient.(
            deviceId: String,
            service: UUID,
            characteristic: UUID,
            value: ByteArray
        ) -> Single<com.signify.hue.flutterreactiveble.ble.CharOperationResult>
    ) {
        val writeCharMessage = pb.WriteCharacteristicRequest.parseFrom(call.arguments as ByteArray)
        bleClient.writeOperation(
            writeCharMessage.characteristic.deviceId,
            uuidConverter.uuidFromByteArray(writeCharMessage.characteristic.serviceUuid.data.toByteArray()),
            uuidConverter.uuidFromByteArray(writeCharMessage.characteristic.characteristicUuid.data.toByteArray()),
            writeCharMessage.value.toByteArray()
        )
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ operationResult ->
                when (operationResult) {
                    is com.signify.hue.flutterreactiveble.ble.CharOperationSuccessful -> {
                        result.success(
                            protoConverter.convertWriteCharacteristicInfo(
                                writeCharMessage,
                                null
                            ).toByteArray()
                        )
                    }

                    is com.signify.hue.flutterreactiveble.ble.CharOperationFailed -> {
                        result.success(
                            protoConverter.convertWriteCharacteristicInfo(
                                writeCharMessage,
                                operationResult.errorMessage
                            ).toByteArray()
                        )
                    }
                }
            },
                { throwable ->
                    result.success(
                        protoConverter.convertWriteCharacteristicInfo(
                            writeCharMessage,
                            throwable.message
                        ).toByteArray()
                    )
                }
            )
            .discard()
    }

    private fun readNotifications(call: MethodCall, result: Result) {
        val request = pb.NotifyCharacteristicRequest.parseFrom(call.arguments as ByteArray)
        charNotificationHandler.subscribeToNotifications(request)
        result.success(null)
    }

    private fun stopNotifications(call: MethodCall, result: Result) {
        val request = pb.NotifyNoMoreCharacteristicRequest.parseFrom(call.arguments as ByteArray)
        charNotificationHandler.unsubscribeFromNotifications(request)
        result.success(null)
    }

    private fun startAdvertising(call: MethodCall, result: Result) {
        bleClient.startAdvertising()
        result.success(null)
    }

    private fun stopAdvertising(call: MethodCall, result: Result) {
        bleClient.stopAdvertising()
        result.success(null)
    }

    private fun startGattServer(call: MethodCall, result: Result) {
        bleClient.startGattServer()
        result.success(null)
    }

    private fun stopGattServer(call: MethodCall, result: Result) {
        bleClient.stopGattServer()
        result.success(null)
    }

    private fun addGattService(call: MethodCall, result: Result) {
        bleClient.addGattService()
        result.success(null)
    }

    private fun checkIfOldInetBoxBondingExists(call: MethodCall, result: Result) {
        val macAddressInfo = pb.BtMacAddressInfo.parseFrom(call.arguments as ByteArray)
        result.success(bleClient.checkIfOldInetBoxBondingExists(macAddressInfo.deviceId))
    }

    private fun removeInetBoxBonding(call: MethodCall, result: Result) {
        val macAddressInfo = pb.BtMacAddressInfo.parseFrom(call.arguments as ByteArray)
        result.success(
            bleClient.removeInetBoxBonding(
                macAddressInfo.deviceId,
                macAddressInfo.forceDelete,
                null
            )
        )
    }

    private fun addGattCharacteristic(call: MethodCall, result: Result) {
        bleClient.addGattCharacteristic()
        result.success(null)
    }

    private fun writeLocalCharacteristic(call: MethodCall, result: Result) {
        val writeCharMessage = pb.WriteCharacteristicRequest.parseFrom(call.arguments as ByteArray)
        bleClient.writeLocalCharacteristic(
            writeCharMessage.characteristic.deviceId,
            uuidConverter.uuidFromByteArray(writeCharMessage.characteristic.characteristicUuid.data.toByteArray()),
            writeCharMessage.value.toByteArray()
        )
        result.success(null)
    }

    private fun negotiateMtuSize(call: MethodCall, result: Result) {
        val request = pb.NegotiateMtuRequest.parseFrom(call.arguments as ByteArray)
        bleClient.negotiateMtuSize(request.deviceId, request.mtuSize)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ mtuResult ->
                result.success(protoConverter.convertNegotiateMtuInfo(mtuResult).toByteArray())
            }, { throwable ->
                result.success(
                    protoConverter.convertNegotiateMtuInfo(
                        com.signify.hue.flutterreactiveble.ble.MtuNegotiateFailed(
                            request.deviceId,
                            throwable.message ?: ""
                        )
                    ).toByteArray()
                )
            }
            )
            .discard()
    }

    private fun requestConnectionPriority(call: MethodCall, result: Result) {
        val request = pb.ChangeConnectionPriorityRequest.parseFrom(call.arguments as ByteArray)

        bleClient.requestConnectionPriority(
            request.deviceId,
            request.priority.toConnectionPriority()
        )
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ requestResult ->
                result.success(
                    protoConverter
                        .convertRequestConnectionPriorityInfo(requestResult).toByteArray()
                )
            },
                { throwable ->
                    result.success(
                        protoConverter.convertRequestConnectionPriorityInfo(
                            RequestConnectionPriorityFailed(
                                request.deviceId, throwable?.message
                                    ?: "Unknown error"
                            )
                        ).toByteArray()
                    )
                })
            .discard()
    }

    private fun discoverServices(call: MethodCall, result: Result) {
        val request = pb.DiscoverServicesRequest.parseFrom(call.arguments as ByteArray)

        bleClient.discoverServices(request.deviceId)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ discoverResult ->
                result.success(
                    protoConverter.convertDiscoverServicesInfo(
                        request.deviceId,
                        discoverResult
                    ).toByteArray()
                )
            }, { throwable ->
                result.error("service_discovery_failure", throwable.message, null)
            })
            .discard()
    }

    private fun isDeviceConnected(call: MethodCall, result: Result) {
        val tag = "IsDeviceConnected"
        try {
            val request: pb.GetConnectionRequest =
                pb.GetConnectionRequest.parseFrom(call.arguments as ByteArray)
            result.success(
                protoConverter.convertGetConnectionInfo(
                    bleClient.isDeviceConnected(request.deviceId),
                ).toByteArray()
            )
        } catch (exception: Exception) {
            Log.d(tag, "Exception")
            result.error("connection_failure", exception.message, "Unexpected error")
        }
    }
}
