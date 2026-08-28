package com.dexstudios.dex.overlay

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Dp
import co.touchlab.kermit.Logger
import com.dexstudios.dex.core.designsystem.components.overlay.BannerMorphState
import com.dexstudios.dex.core.designsystem.components.overlay.ToastVariant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import java.util.UUID

/**
 * Central orchestrator and state authority for the DeX Fluid Overlay & Notification System.
 *
 * Exposes:
 * - Two separate reactive streams for the dual AWT host windows
 * - Hover-aware auto-dismiss timer lifecycle
 * - In-memory retention buffer of the 20 most recent notifications
 * - Sound playback integration via [OverlaySoundService]
 */
class OverlayManager(private val scope: CoroutineScope, private val soundService: OverlaySoundService? = null, private val timeProvider: () -> Long = { System.currentTimeMillis() }) {
    // Action Alerts Tier (Banners, Alerts, Confirmations, StackedScreens)
    private val _actionAlerts = MutableStateFlow<List<NotificationPayload>>(emptyList())
    val actionAlerts: StateFlow<List<NotificationPayload>> = _actionAlerts.asStateFlow()
    val bottomCenterNotifications: StateFlow<List<NotificationPayload>> get() = actionAlerts

    // Status Toasts Tier (Compact Toasts & Progress Pills)
    private val _statusToasts = MutableStateFlow<List<ToastNotification>>(emptyList())
    val statusToasts: StateFlow<List<ToastNotification>> = _statusToasts.asStateFlow()
    val cornerToasts: StateFlow<List<ToastNotification>> get() = statusToasts

    // In-memory retention history (capped at 20)
    private val _history = MutableStateFlow<List<NotificationHistoryEntry>>(emptyList())
    val history: StateFlow<List<NotificationHistoryEntry>> = _history.asStateFlow()

    // Do Not Disturb (DND) / Focus Mode
    private val _isDoNotDisturb = MutableStateFlow(false)
    val isDoNotDisturb: StateFlow<Boolean> = _isDoNotDisturb.asStateFlow()

    // 3-Second Deduplication Window to guard against duplicate event floods
    private val recentDispatches = mutableMapOf<String, Long>()

    // Active timer jobs mapped by notification ID
    private val activeTimerJobs = mutableMapOf<NotificationId, Job>()
    private val timerRemainingMs = mutableMapOf<NotificationId, Long>()
    private val timerLastResumeEpochMs = mutableMapOf<NotificationId, Long>()

    private var isAlertsHovered = false
    private var isToastsHovered = false

    companion object {
        const val MAX_HISTORY_ITEMS = 20
        const val DEDUPLICATION_WINDOW_MS = 3_000L

        /** Global singleton access for simple calls where DI injection is indirect. */
        var instance: OverlayManager? = null
            internal set
    }

    fun setDoNotDisturb(enabled: Boolean) {
        _isDoNotDisturb.value = enabled
        Logger.i("OverlayManager: Do Not Disturb state set to $enabled")
    }

    init {
        instance = this
    }

    // =========================================================================
    // Public Dispatch API
    // =========================================================================

    /**
     * Dispatch a Dynamic Island style Notification Banner.
     */
    fun showBanner(
        title: String,
        subtitle: String? = null,
        badgeText: String? = null,
        iconResource: DrawableResource? = null,
        iconPainter: Painter? = null,
        iconTint: Color? = null,
        iconBackgroundColor: Color? = null,
        progress: Float? = null,
        initialMorphState: BannerMorphState = BannerMorphState.Compact,
        allowInteractiveMorph: Boolean = true,
        trailingPreview: (@Composable () -> Unit)? = null,
        expandedContent: (@Composable () -> Unit)? = null,
        onActionClick: (() -> Unit)? = null,
        priority: NotificationPriority = NotificationPriority.Normal,
        autoDismissTimeoutMs: Long? = 5_000L,
        playSound: Boolean = true,
    ): NotificationId {
        val dedupeKey = "banner:$title:${subtitle ?: ""}"
        val now = timeProvider()
        val lastTime = recentDispatches[dedupeKey]
        if (lastTime != null && (now - lastTime) < DEDUPLICATION_WINDOW_MS) {
            return "deduped"
        }
        recentDispatches[dedupeKey] = now

        val id = UUID.randomUUID().toString()
        val payload = BannerNotification(
            id = id,
            title = title,
            subtitle = subtitle,
            badgeText = badgeText,
            iconResource = iconResource,
            iconPainter = iconPainter,
            iconTint = iconTint,
            iconBackgroundColor = iconBackgroundColor,
            progress = progress,
            initialMorphState = initialMorphState,
            allowInteractiveMorph = allowInteractiveMorph,
            trailingPreview = trailingPreview,
            expandedContent = expandedContent,
            onActionClick = onActionClick,
            priority = priority,
            autoDismissTimeoutMs = autoDismissTimeoutMs,
        )

        enqueueBottomCenter(payload)
        recordHistory(id, "Banner", title, subtitle ?: "")
        val effectivePlaySound = if (_isDoNotDisturb.value) false else playSound
        if (effectivePlaySound) soundService?.playNotificationSound()
        scheduleTimer(payload)
        return id
    }

    /**
     * Update an active Banner notification in-place (e.g. For real-time transfer progress).
     */
    fun updateBanner(id: NotificationId, title: String? = null, subtitle: String? = null, progress: Float? = null, badgeText: String? = null) {
        _actionAlerts.update { current ->
            current.map { item ->
                if (item.id == id && item is BannerNotification) {
                    item.copy(
                        title = title ?: item.title,
                        subtitle = subtitle ?: item.subtitle,
                        progress = progress ?: item.progress,
                        badgeText = badgeText ?: item.badgeText,
                    )
                } else {
                    item
                }
            }
        }
    }

    /**
     * Dispatch an AirDrop-style Modal Alert Dialog.
     */
    fun showAlert(
        title: String,
        message: String,
        iconResource: DrawableResource? = null,
        iconPainter: Painter? = null,
        iconTint: Color? = null,
        badgeResource: DrawableResource? = null,
        badgePainter: Painter? = null,
        previewContent: (@Composable () -> Unit)? = null,
        negativeButtonText: String = "Decline",
        positiveButtonText: String = "Accept",
        isPositiveActionLoading: Boolean = false,
        isNegativeActionLoading: Boolean = false,
        onNegativeAction: () -> Unit = {},
        onPositiveAction: () -> Unit = {},
        playSound: Boolean = true,
    ): NotificationId {
        val id = UUID.randomUUID().toString()
        val payload = AlertNotification(
            id = id,
            title = title,
            message = message,
            iconResource = iconResource,
            iconPainter = iconPainter,
            iconTint = iconTint,
            badgeResource = badgeResource,
            badgePainter = badgePainter,
            previewContent = previewContent,
            negativeButtonText = negativeButtonText,
            positiveButtonText = positiveButtonText,
            isPositiveActionLoading = isPositiveActionLoading,
            isNegativeActionLoading = isNegativeActionLoading,
            onNegativeAction = {
                onNegativeAction()
                dismiss(id, wasUserAction = true)
            },
            onPositiveAction = {
                onPositiveAction()
                dismiss(id, wasUserAction = true)
            },
        )

        enqueueBottomCenter(payload)
        recordHistory(id, "Alert", title, message)
        val effectivePlaySound = if (_isDoNotDisturb.value) false else playSound
        if (effectivePlaySound) soundService?.playNotificationSound()
        return id
    }

    /**
     * Dispatch a full-content StackedScreen overlay.
     */
    fun pushStackedScreen(
        title: String,
        subtitle: String? = null,
        width: Dp? = null,
        height: Dp? = null,
        showBackButton: Boolean = true,
        trailingHeaderAction: (@Composable () -> Unit)? = null,
        onBack: () -> Unit = {},
        content: @Composable () -> Unit,
    ): NotificationId {
        val id = UUID.randomUUID().toString()
        val payload = StackedScreenNotification(
            id = id,
            title = title,
            subtitle = subtitle,
            width = width,
            height = height,
            showBackButton = showBackButton,
            trailingHeaderAction = trailingHeaderAction,
            onBack = {
                onBack()
                dismiss(id, wasUserAction = true)
            },
            content = content,
        )

        enqueueBottomCenter(payload)
        recordHistory(id, "StackedScreen", title, subtitle ?: "")
        return id
    }

    /**
     * Dispatch a compact corner Message Toast.
     */
    fun showToast(
        message: String,
        variant: ToastVariant = ToastVariant.Info,
        iconResource: DrawableResource? = null,
        iconPainter: Painter? = null,
        actionText: String? = null,
        onActionClick: (() -> Unit)? = null,
        showCloseButton: Boolean? = null,
        progress: Float? = null,
        autoDismissTimeoutMs: Long? = 3_000L,
        playSound: Boolean = false,
    ): NotificationId {
        val dedupeKey = "toast:${variant::class.simpleName}:$message"
        val now = timeProvider()
        val lastTime = recentDispatches[dedupeKey]
        if (lastTime != null && (now - lastTime) < DEDUPLICATION_WINDOW_MS) {
            // Discard duplicate within 3s window
            return "deduped"
        }
        recentDispatches[dedupeKey] = now

        val id = UUID.randomUUID().toString()
        recordHistory(id, "Toast", variant::class.simpleName.toString(), message)

        // When Do Not Disturb is active, silently queue in history without on-screen visual overlay
        if (_isDoNotDisturb.value) {
            return id
        }

        val payload = ToastNotification(
            id = id,
            message = message,
            variant = variant,
            iconResource = iconResource,
            iconPainter = iconPainter,
            actionText = actionText,
            onActionClick = onActionClick?.let { action ->
                {
                    action()
                    dismiss(id, wasUserAction = true)
                }
            },
            showCloseButton = showCloseButton,
            progress = progress,
            autoDismissTimeoutMs = autoDismissTimeoutMs,
        )

        enqueueToast(payload)
        if (playSound) soundService?.playNotificationSound()
        scheduleTimer(payload)
        return id
    }

    /**
     * Dismiss a notification by ID.
     */
    fun dismiss(id: NotificationId, wasUserAction: Boolean = false) {
        cancelTimer(id)

        _actionAlerts.value = _actionAlerts.value.filterNot { it.id == id }
        _statusToasts.value = _statusToasts.value.filterNot { it.id == id }

        Logger.i("OverlayManager: Notification dismissed [id=$id, byUser=$wasUserAction]")
    }

    /**
     * Dismiss all active notifications.
     */
    fun dismissAll() {
        activeTimerJobs.values.forEach { it.cancel() }
        activeTimerJobs.clear()
        timerRemainingMs.clear()
        timerLastResumeEpochMs.clear()

        _actionAlerts.value = emptyList()
        _statusToasts.value = emptyList()
    }

    /**
     * Dismiss all Tier 1 Action Alerts.
     */
    fun dismissAlerts() {
        val alertIds = _actionAlerts.value.map { it.id }.toSet()
        alertIds.forEach { cancelTimer(it) }
        _actionAlerts.value = emptyList()
    }

    /**
     * Dismiss all Tier 2 Status Toasts.
     */
    fun dismissToasts() {
        val toastIds = _statusToasts.value.map { it.id }.toSet()
        toastIds.forEach { cancelTimer(it) }
        _statusToasts.value = emptyList()
    }

    // =========================================================================
    // Hover & Timer Management
    // =========================================================================

    fun setAlertsHovered(hovered: Boolean) {
        if (isAlertsHovered == hovered) return
        isAlertsHovered = hovered

        if (hovered) {
            pauseTimersFor(_actionAlerts.value)
        } else {
            resumeTimersFor(_actionAlerts.value)
        }
    }

    fun setToastsHovered(hovered: Boolean) {
        if (isToastsHovered == hovered) return
        isToastsHovered = hovered

        if (hovered) {
            pauseTimersFor(_statusToasts.value)
        } else {
            resumeTimersFor(_statusToasts.value)
        }
    }

    fun setBottomCenterHovered(hovered: Boolean) = setAlertsHovered(hovered)
    fun setCornerHovered(hovered: Boolean) = setToastsHovered(hovered)

    private fun enqueueAlert(payload: NotificationPayload) {
        val current = _actionAlerts.value
        // Place new items at the top of the list
        _actionAlerts.value = listOf(payload) + current
    }

    private fun enqueueBottomCenter(payload: NotificationPayload) = enqueueAlert(payload)

    private fun enqueueToast(payload: ToastNotification) {
        val current = _statusToasts.value
        _statusToasts.value = listOf(payload) + current
    }

    private fun scheduleTimer(payload: NotificationPayload) {
        val timeout = payload.autoDismissTimeoutMs ?: return
        timerRemainingMs[payload.id] = timeout
        timerLastResumeEpochMs[payload.id] = timeProvider()

        startTimerJob(payload.id, timeout)
    }

    private fun startTimerJob(id: NotificationId, durationMs: Long) {
        activeTimerJobs[id]?.cancel()
        activeTimerJobs[id] = scope.launch {
            delay(durationMs)
            dismiss(id, wasUserAction = false)
        }
    }

    private fun pauseTimersFor(list: List<NotificationPayload>) {
        val now = timeProvider()
        for (item in list) {
            val job = activeTimerJobs.remove(item.id) ?: continue
            job.cancel()

            val lastResume = timerLastResumeEpochMs[item.id] ?: now
            val elapsed = now - lastResume
            val previousRemaining = timerRemainingMs[item.id] ?: item.autoDismissTimeoutMs ?: 0L
            val newRemaining = (previousRemaining - elapsed).coerceAtLeast(100L)
            timerRemainingMs[item.id] = newRemaining
        }
    }

    private fun resumeTimersFor(list: List<NotificationPayload>) {
        val now = timeProvider()
        for (item in list) {
            val remaining = timerRemainingMs[item.id] ?: continue
            // Enforce minimum 2.5s grace period on unhover so notifications never vanish immediately
            val effectiveDuration = maxOf(remaining, 2_500L)
            timerLastResumeEpochMs[item.id] = now
            startTimerJob(item.id, effectiveDuration)
        }
    }

    private fun cancelTimer(id: NotificationId) {
        activeTimerJobs.remove(id)?.cancel()
        timerRemainingMs.remove(id)
        timerLastResumeEpochMs.remove(id)
    }

    private fun recordHistory(id: NotificationId, type: String, title: String, message: String) {
        val entry = NotificationHistoryEntry(
            id = id,
            type = type,
            title = title,
            message = message,
            timestampEpochMs = timeProvider(),
            wasDismissedByUser = false,
        )
        val current = _history.value
        _history.value = (listOf(entry) + current).take(MAX_HISTORY_ITEMS)
    }
}
