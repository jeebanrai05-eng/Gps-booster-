package com.example.engine

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.telephony.PhoneStateListener
import android.telephony.ServiceState
import android.telephony.SignalStrength
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.example.data.local.RiderIntelligenceRepository
import com.example.data.local.SignalHandoverEntity
import com.example.domain.model.DualSimState
import com.example.domain.model.SimSlotTelemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DualSimAnalyzerEngine(
    private val context: Context,
    private val repository: RiderIntelligenceRepository,
    private val scope: CoroutineScope
) {
    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    private val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager

    private val _state = MutableStateFlow(DualSimState())
    val state: StateFlow<DualSimState> = _state.asStateFlow()

    private val registeredSubCallbacks = mutableMapOf<Int, Pair<TelephonyManager, Any>>()
    private val liveDbmBySubId = mutableMapOf<Int, Int?>()
    private val liveLevelBySubId = mutableMapOf<Int, Int>()
    private val liveNetTypeBySubId = mutableMapOf<Int, String>()
    private val lastRecordedNetTypeBySubId = mutableMapOf<Int, String>()
    private val lastAlertTimestampBySubId = mutableMapOf<Int, Long>()

    fun startMonitoring() {
        refreshSnapshot()
        attachTelephonyCallbacks()
    }

    fun stopMonitoring() {
        detachTelephonyCallbacks()
    }

    @SuppressLint("MissingPermission")
    fun refreshSnapshot(): DualSimState {
        val hasTelephony = context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
        val hasPhonePerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        val tm = telephonyManager
        if (!hasTelephony || tm == null) {
            val unavailable = DualSimState(
                hasPhoneStatePermission = hasPhonePerm,
                hasTelephonyFeature = false,
                activeSubscriptionCount = 0,
                simSlots = emptyList(),
                comparisonSummary = "Cellular telephony hardware is unavailable on this device.",
                lastUpdatedMs = System.currentTimeMillis()
            )
            _state.value = unavailable
            return unavailable
        }

        if (!hasPhonePerm) {
            val primaryOp = runCatching { tm.networkOperatorName }.getOrNull()
                ?.takeIf { it.isNotBlank() } ?: "Permission Required"
            val fallbackSlot = SimSlotTelemetry(
                slotIndex = 0,
                subscriptionId = -1,
                displayName = "SIM 1",
                carrierName = primaryOp,
                networkOperatorName = primaryOp,
                isActiveSlot = primaryOp != "Permission Required",
                isDefaultData = true,
                isDefaultVoice = true,
                isDefaultSms = true,
                isRoaming = false,
                networkTypeLabel = "Requires Phone State Permission",
                signalStrengthDbm = null,
                signalLevel = -1,
                signalQualityLabel = "Unavailable",
                dataAvailable = false,
                unavailableReason = "Grant READ_PHONE_STATE to inspect SIM 1 & SIM 2 subscriptions and TelephonyCallback signal strength."
            )
            val denied = DualSimState(
                hasPhoneStatePermission = false,
                hasTelephonyFeature = true,
                activeSubscriptionCount = 0,
                simSlots = listOf(fallbackSlot),
                comparisonSummary = "Phone State permission required to read Dual SIM slots, carriers, and live cellular signal dBm.",
                lastUpdatedMs = System.currentTimeMillis()
            )
            _state.value = denied
            return denied
        }

        val subscriptions: List<SubscriptionInfo> = runCatching {
            @Suppress("DEPRECATION")
            subscriptionManager?.activeSubscriptionInfoList ?: emptyList()
        }.getOrDefault(emptyList())

        val defaultDataId = runCatching { SubscriptionManager.getDefaultDataSubscriptionId() }.getOrDefault(-1)
        val defaultVoiceId = runCatching { SubscriptionManager.getDefaultVoiceSubscriptionId() }.getOrDefault(-1)
        val defaultSmsId = runCatching { SubscriptionManager.getDefaultSmsSubscriptionId() }.getOrDefault(-1)

        val slots = if (subscriptions.isEmpty()) {
            listOf(buildUnregisteredDefaultSlot(tm))
        } else {
            subscriptions
                .sortedBy { it.simSlotIndex }
                .map { sub ->
                    buildSlotFromSubscription(
                        sub = sub,
                        defaultDataId = defaultDataId,
                        defaultVoiceId = defaultVoiceId,
                        defaultSmsId = defaultSmsId
                    )
                }
        }

        val comparisonSummary = buildComparisonSummary(slots)
        val degradationAlert = evaluateDegradationAlert(slots)

        val next = DualSimState(
            hasPhoneStatePermission = true,
            hasTelephonyFeature = true,
            activeSubscriptionCount = subscriptions.size,
            simSlots = slots,
            comparisonSummary = comparisonSummary,
            signalDegradationAlert = degradationAlert,
            lastUpdatedMs = System.currentTimeMillis()
        )
        _state.value = next
        return next
    }

    @SuppressLint("MissingPermission")
    private fun buildSlotFromSubscription(
        sub: SubscriptionInfo,
        defaultDataId: Int,
        defaultVoiceId: Int,
        defaultSmsId: Int
    ): SimSlotTelemetry {
        val subId = sub.subscriptionId
        val subTm = runCatching { telephonyManager?.createForSubscriptionId(subId) }.getOrNull() ?: telephonyManager

        val netType = liveNetTypeBySubId[subId] ?: resolveNetworkTypeLabel(subTm)
        val sigStrength = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { subTm?.signalStrength }.getOrNull()
        } else null
        val (snapshotDbm, snapshotLevel) = extractSignalMetrics(sigStrength)

        val dbm = liveDbmBySubId[subId] ?: snapshotDbm
        val level = liveLevelBySubId[subId] ?: snapshotLevel
        val opName = runCatching { subTm?.networkOperatorName }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: sub.carrierName?.toString()?.takeIf { it.isNotBlank() }
            ?: "Unknown Operator"

        val qualityLabel = classifySignalQuality(dbm, level, netType)
        val hasMeasurements = dbm != null || level >= 0 || netType != "Unavailable"
        val unavailableReason = if (!hasMeasurements) {
            "Modem signal measurement unavailable for this slot until cellular registration."
        } else null

        return SimSlotTelemetry(
            slotIndex = sub.simSlotIndex.coerceAtLeast(0),
            subscriptionId = subId,
            displayName = sub.displayName?.toString()?.takeIf { it.isNotBlank() } ?: "SIM ${sub.simSlotIndex + 1}",
            carrierName = sub.carrierName?.toString()?.takeIf { it.isNotBlank() } ?: opName,
            networkOperatorName = opName,
            isActiveSlot = true,
            isDefaultData = subId == defaultDataId,
            isDefaultVoice = subId == defaultVoiceId,
            isDefaultSms = subId == defaultSmsId,
            isRoaming = runCatching { subTm?.isNetworkRoaming ?: false }.getOrDefault(false),
            networkTypeLabel = netType,
            signalStrengthDbm = dbm,
            signalLevel = level,
            signalQualityLabel = qualityLabel,
            dataAvailable = hasMeasurements,
            unavailableReason = unavailableReason
        )
    }

    @SuppressLint("MissingPermission")
    private fun buildUnregisteredDefaultSlot(tm: TelephonyManager): SimSlotTelemetry {
        val op = runCatching { tm.networkOperatorName }.getOrNull()?.takeIf { it.isNotBlank() } ?: "No SIM Detected"
        val netType = resolveNetworkTypeLabel(tm)
        val sig = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { tm.signalStrength }.getOrNull()
        } else null
        val (dbm, level) = extractSignalMetrics(sig)
        return SimSlotTelemetry(
            slotIndex = 0,
            subscriptionId = -1,
            displayName = "SIM 1",
            carrierName = op,
            networkOperatorName = op,
            isActiveSlot = op != "No SIM Detected",
            isDefaultData = true,
            isDefaultVoice = true,
            isDefaultSms = true,
            isRoaming = false,
            networkTypeLabel = netType,
            signalStrengthDbm = dbm,
            signalLevel = level,
            signalQualityLabel = classifySignalQuality(dbm, level, netType),
            dataAvailable = dbm != null || level >= 0,
            unavailableReason = if (op == "No SIM Detected") "No active SIM card detected in slot." else null
        )
    }

    @SuppressLint("MissingPermission")
    private fun attachTelephonyCallbacks() {
        val hasPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
        val tmRoot = telephonyManager
        if (!hasPerm || tmRoot == null) return

        detachTelephonyCallbacks()

        val subs = runCatching {
            @Suppress("DEPRECATION")
            subscriptionManager?.activeSubscriptionInfoList ?: emptyList()
        }.getOrDefault(emptyList())

        val targetIds = if (subs.isEmpty()) listOf(-1) else subs.map { it.subscriptionId }

        for (subId in targetIds) {
            val tm = if (subId >= 0) {
                runCatching { tmRoot.createForSubscriptionId(subId) }.getOrNull() ?: tmRoot
            } else {
                tmRoot
            }
            val slotIndex = subs.firstOrNull { it.subscriptionId == subId }?.simSlotIndex ?: 0
            val opName = subs.firstOrNull { it.subscriptionId == subId }?.carrierName?.toString()
                ?: runCatching { tm.networkOperatorName }.getOrNull().orEmpty().ifBlank { "Carrier" }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                registerCallbackApi31(subId, slotIndex, opName, tm)
            } else {
                registerLegacyPhoneStateListener(subId, slotIndex, opName, tm)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun registerCallbackApi31(
        subId: Int,
        slotIndex: Int,
        operatorName: String,
        tm: TelephonyManager
    ) {
        val callback = object : TelephonyCallback(),
            TelephonyCallback.SignalStrengthsListener,
            TelephonyCallback.DisplayInfoListener,
            TelephonyCallback.ServiceStateListener {

            override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
                val (dbm, level) = extractSignalMetrics(signalStrength)
                liveDbmBySubId[subId] = dbm
                liveLevelBySubId[subId] = level
                checkAndPersistDegradation(subId, slotIndex, operatorName, liveNetTypeBySubId[subId] ?: resolveNetworkTypeLabel(tm), dbm, level)
                refreshSnapshot()
            }

            override fun onDisplayInfoChanged(telephonyDisplayInfo: TelephonyDisplayInfo) {
                val newType = mapDisplayInfo(telephonyDisplayInfo)
                handleNetworkTypeTransition(subId, slotIndex, operatorName, newType)
                refreshSnapshot()
            }

            override fun onServiceStateChanged(serviceState: ServiceState) {
                refreshSnapshot()
            }
        }

        runCatching {
            tm.registerTelephonyCallback(ContextCompat.getMainExecutor(context), callback)
            registeredSubCallbacks[subId] = tm to callback
        }
    }

    @Suppress("DEPRECATION")
    private fun registerLegacyPhoneStateListener(
        subId: Int,
        slotIndex: Int,
        operatorName: String,
        tm: TelephonyManager
    ) {
        val listener = object : PhoneStateListener() {
            override fun onSignalStrengthsChanged(signalStrength: SignalStrength?) {
                val (dbm, level) = extractSignalMetrics(signalStrength)
                liveDbmBySubId[subId] = dbm
                liveLevelBySubId[subId] = level
                refreshSnapshot()
            }

            override fun onDataConnectionStateChanged(state: Int, networkType: Int) {
                val newType = mapNetworkTypeInt(networkType)
                handleNetworkTypeTransition(subId, slotIndex, operatorName, newType)
                refreshSnapshot()
            }
        }
        runCatching {
            tm.listen(listener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS or PhoneStateListener.LISTEN_DATA_CONNECTION_STATE)
            registeredSubCallbacks[subId] = tm to listener
        }
    }

    private fun handleNetworkTypeTransition(
        subId: Int,
        slotIndex: Int,
        operatorName: String,
        newType: String
    ) {
        val prevType = lastRecordedNetTypeBySubId[subId]
        liveNetTypeBySubId[subId] = newType
        if (prevType != null && prevType != newType && newType != "Unavailable") {
            lastRecordedNetTypeBySubId[subId] = newType
            val isDowngrade = (prevType.contains("5G") || prevType.contains("4G")) &&
                (newType.contains("3G") || newType.contains("2G") || newType == "No Service")
            val alert = if (isDowngrade) "Handover downgrade $prevType → $newType" else null
            scope.launch {
                repository.insertSignalHandover(
                    SignalHandoverEntity(
                        timestampMs = System.currentTimeMillis(),
                        slotIndex = slotIndex,
                        subscriptionId = subId,
                        operatorName = operatorName,
                        previousNetworkType = prevType,
                        newNetworkType = newType,
                        rssiDbm = liveDbmBySubId[subId],
                        signalLevel = liveLevelBySubId[subId] ?: -1,
                        alertMessage = alert
                    )
                )
            }
        } else if (prevType == null) {
            lastRecordedNetTypeBySubId[subId] = newType
        }
    }

    private fun checkAndPersistDegradation(
        subId: Int,
        slotIndex: Int,
        operatorName: String,
        networkType: String,
        dbm: Int?,
        level: Int
    ) {
        val isWeak = (dbm != null && dbm <= -110) || level == 0 || level == 1
        if (!isWeak) return
        val now = System.currentTimeMillis()
        val lastAlert = lastAlertTimestampBySubId[subId] ?: 0L
        if (now - lastAlert < 90_000L) return
        lastAlertTimestampBySubId[subId] = now
        scope.launch {
            repository.insertSignalHandover(
                SignalHandoverEntity(
                    timestampMs = now,
                    slotIndex = slotIndex,
                    subscriptionId = subId,
                    operatorName = operatorName,
                    previousNetworkType = networkType,
                    newNetworkType = networkType,
                    rssiDbm = dbm,
                    signalLevel = level,
                    alertMessage = "Weak signal detected (${dbm?.let { "$it dBm" } ?: "Level $level"})"
                )
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun detachTelephonyCallbacks() {
        registeredSubCallbacks.values.forEach { (tm, cb) ->
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && cb is TelephonyCallback) {
                    tm.unregisterTelephonyCallback(cb)
                } else if (cb is PhoneStateListener) {
                    tm.listen(cb, PhoneStateListener.LISTEN_NONE)
                }
            }
        }
        registeredSubCallbacks.clear()
    }

    @SuppressLint("MissingPermission")
    private fun resolveNetworkTypeLabel(tm: TelephonyManager?): String {
        if (tm == null) return "Unavailable"
        val typeInt = runCatching { tm.dataNetworkType }.getOrDefault(TelephonyManager.NETWORK_TYPE_UNKNOWN)
        return mapNetworkTypeInt(typeInt)
    }

    private fun extractSignalMetrics(signalStrength: SignalStrength?): Pair<Int?, Int> {
        if (signalStrength == null) return null to -1
        val level = runCatching { signalStrength.level }.getOrDefault(-1)
        val dbm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                signalStrength.cellSignalStrengths
                    .map { it.dbm }
                    .firstOrNull { it in -140..-30 }
            }.getOrNull()
        } else null
        return dbm to level
    }

    fun openSimSettings(): Boolean {
        val candidates = listOf(
            Intent(Settings.ACTION_DATA_ROAMING_SETTINGS),
            Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS),
            Intent(Settings.ACTION_WIRELESS_SETTINGS)
        )
        for (intent in candidates) {
            val launched = runCatching {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            }.getOrDefault(false)
            if (launched) return true
        }
        return false
    }

    fun openInternetPanel(): Boolean {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
        } else {
            Intent(Settings.ACTION_WIRELESS_SETTINGS)
        }
        return runCatching {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    companion object {
        fun mapNetworkTypeInt(networkType: Int): String = when (networkType) {
            TelephonyManager.NETWORK_TYPE_NR -> "5G NR"
            TelephonyManager.NETWORK_TYPE_LTE -> "4G LTE"
            TelephonyManager.NETWORK_TYPE_HSPAP,
            TelephonyManager.NETWORK_TYPE_HSPA,
            TelephonyManager.NETWORK_TYPE_HSDPA,
            TelephonyManager.NETWORK_TYPE_HSUPA,
            TelephonyManager.NETWORK_TYPE_UMTS,
            TelephonyManager.NETWORK_TYPE_EVDO_0,
            TelephonyManager.NETWORK_TYPE_EVDO_A,
            TelephonyManager.NETWORK_TYPE_EVDO_B -> "3G"
            TelephonyManager.NETWORK_TYPE_EDGE,
            TelephonyManager.NETWORK_TYPE_GPRS,
            TelephonyManager.NETWORK_TYPE_CDMA,
            TelephonyManager.NETWORK_TYPE_1xRTT -> "2G"
            else -> "Unavailable"
        }

        @RequiresApi(Build.VERSION_CODES.R)
        fun mapDisplayInfo(info: TelephonyDisplayInfo): String {
            return when (info.overrideNetworkType) {
                TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA,
                TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED -> "5G NR"
                TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_CA,
                TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_ADVANCED_PRO -> "4G LTE+"
                else -> mapNetworkTypeInt(info.networkType)
            }
        }

        fun classifySignalQuality(dbm: Int?, level: Int, netType: String): String {
            if (dbm == null && level < 0) return if (netType == "Unavailable") "Unavailable" else "Active ($netType)"
            return when {
                (dbm != null && dbm >= -85) || level >= 4 -> "Excellent"
                (dbm != null && dbm >= -98) || level == 3 -> "Good"
                (dbm != null && dbm >= -108) || level == 2 -> "Fair"
                else -> "Weak Signal"
            }
        }

        fun buildComparisonSummary(slots: List<SimSlotTelemetry>): String {
            if (slots.isEmpty()) return "No SIM subscriptions detected."
            if (slots.size == 1) {
                val s = slots.first()
                return "Single active SIM (${s.carrierName} • ${s.networkTypeLabel}). Insert a second SIM to compare SIM 1 vs SIM 2."
            }
            val sim1 = slots[0]
            val sim2 = slots[1]
            val dbm1 = sim1.signalStrengthDbm
            val dbm2 = sim2.signalStrengthDbm
            return when {
                dbm1 != null && dbm2 != null -> {
                    when {
                        dbm1 > dbm2 + 4 ->
                            "${sim1.displayName} (${sim1.carrierName}, $dbm1 dBm) currently shows stronger cellular signal than ${sim2.displayName} ($dbm2 dBm)."
                        dbm2 > dbm1 + 4 ->
                            "${sim2.displayName} (${sim2.carrierName}, $dbm2 dBm) currently shows stronger cellular signal than ${sim1.displayName} ($dbm1 dBm)."
                        else ->
                            "${sim1.displayName} ($dbm1 dBm) and ${sim2.displayName} ($dbm2 dBm) show comparable signal levels."
                    }
                }
                sim1.signalLevel >= 0 && sim2.signalLevel >= 0 -> {
                    when {
                        sim1.signalLevel > sim2.signalLevel ->
                            "${sim1.displayName} has higher signal bars (${sim1.signalLevel}/4 vs ${sim2.signalLevel}/4)."
                        sim2.signalLevel > sim1.signalLevel ->
                            "${sim2.displayName} has higher signal bars (${sim2.signalLevel}/4 vs ${sim1.signalLevel}/4)."
                        else ->
                            "Both SIMs report equal signal bar level (${sim1.signalLevel}/4)."
                    }
                }
                else -> "Dual SIM detected (${sim1.carrierName} & ${sim2.carrierName}). Some secondary-SIM RSSI metrics are hidden by device modem."
            }
        }

        fun evaluateDegradationAlert(slots: List<SimSlotTelemetry>): String? {
            val activeDataSlot = slots.firstOrNull { it.isDefaultData } ?: slots.firstOrNull() ?: return null
            if (!activeDataSlot.isActiveSlot) return null
            val dbm = activeDataSlot.signalStrengthDbm
            return when {
                dbm != null && dbm <= -112 ->
                    "Data SIM (${activeDataSlot.carrierName}) weak signal ($dbm dBm). Rider app dispatch may lag."
                activeDataSlot.signalLevel in 0..1 && activeDataSlot.dataAvailable ->
                    "Data SIM (${activeDataSlot.carrierName}) signal level is low (${activeDataSlot.signalLevel}/4)."
                activeDataSlot.networkTypeLabel == "2G" ->
                    "Data SIM dropped to 2G. Open SIM settings manually if 4G/5G coverage is expected."
                else -> null
            }
        }
    }
}
