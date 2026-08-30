package com.dexstudios.dex

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.appfunctions.AppFunction
import androidx.appfunctions.AppFunctionContext
import androidx.appfunctions.AppFunctionService
import androidx.appfunctions.AppFunctionServiceEntryPoint
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.dexstudios.dex.network.DiscoveryEngine
import com.dexstudios.dex.network.TransferHistory
import com.dexstudios.dex.network.TransferWorkKeys
import com.dexstudios.dex.network.UploadWorker
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@AppFunctionServiceEntryPoint(
    serviceName = "DeXAppFunctionService",
    appFunctionXmlFileName = "dex_app_functions"
)
@RequiresApi(36)
abstract class BaseDeXAppFunctionService : AppFunctionService(), KoinComponent {

    /**
     * Sends the last received file back to a connected PC.
     */
    @AppFunction
    fun sendLastFileToPc(context: AppFunctionContext): String {
        val appContext = context.context
        val history = TransferHistory.items.value
        val lastReceived = history.firstOrNull { it.direction == "received" && it.uri != null }
            ?: return "No recently received files found."

        val discoveryEngine by inject<DiscoveryEngine>()
        val devices = discoveryEngine.devices.value
        val targetPc = devices.values.firstOrNull { it.info.deviceType == "pc" || it.info.deviceType == "desktop" }
            ?: return "No connected PC found on the network."

        val urisJson = "[\"${lastReceived.uri}\"]"
        val workRequest = OneTimeWorkRequestBuilder<UploadWorker>()
            .setInputData(workDataOf(
                TransferWorkKeys.IP to targetPc.ip,
                TransferWorkKeys.PORT to targetPc.info.port,
                TransferWorkKeys.URIS to urisJson,
                TransferWorkKeys.TARGET_FINGERPRINT to targetPc.info.fingerprint,
                TransferWorkKeys.TARGET_IDENTITY_HASH to targetPc.info.identityHash,
                TransferWorkKeys.TARGET_GOOGLE_SUB to targetPc.info.googleSub
            ))
            .build()

        WorkManager.getInstance(appContext).enqueue(workRequest)

        return "Sending ${lastReceived.name} to ${targetPc.info.alias}..."
    }
}
