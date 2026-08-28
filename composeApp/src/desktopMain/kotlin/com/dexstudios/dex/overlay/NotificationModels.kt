package com.dexstudios.dex.overlay

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Dp
import com.dexstudios.dex.core.designsystem.components.overlay.BannerMorphState
import com.dexstudios.dex.core.designsystem.components.overlay.ToastVariant
import org.jetbrains.compose.resources.DrawableResource

/**
 * Unique identifier for notification entries.
 */
typealias NotificationId = String

/**
 * Priority levels for incoming notifications. Higher priority entries appear immediately
 * at the top of the stack.
 */
enum class NotificationPriority {
    Low,
    Normal,
    High,
    Critical,
}

/**
 * Root polymorphic model hierarchy for active notifications.
 */
sealed interface NotificationPayload {
    val id: NotificationId
    val priority: NotificationPriority
    val autoDismissTimeoutMs: Long? // null = persistent (no auto dismiss)
    val createdAtEpochMs: Long
}

/**
 * Dynamic Island style Notification Banner payload.
 */
data class BannerNotification(
    override val id: NotificationId,
    val title: String,
    val subtitle: String? = null,
    val badgeText: String? = null,
    val iconResource: DrawableResource? = null,
    val iconPainter: Painter? = null,
    val iconTint: Color? = null,
    val iconBackgroundColor: Color? = null,
    val progress: Float? = null,
    val initialMorphState: BannerMorphState = BannerMorphState.Compact,
    val allowInteractiveMorph: Boolean = true,
    val trailingPreview: (@Composable () -> Unit)? = null,
    val expandedContent: (@Composable () -> Unit)? = null,
    val onActionClick: (() -> Unit)? = null,
    override val priority: NotificationPriority = NotificationPriority.Normal,
    override val autoDismissTimeoutMs: Long? = 5_000L,
    override val createdAtEpochMs: Long = System.currentTimeMillis(),
) : NotificationPayload

/**
 * AirDrop-style Modal Alert Dialog payload.
 */
data class AlertNotification(
    override val id: NotificationId,
    val title: String,
    val message: String,
    val iconResource: DrawableResource? = null,
    val iconPainter: Painter? = null,
    val iconTint: Color? = null,
    val badgeResource: DrawableResource? = null,
    val badgePainter: Painter? = null,
    val previewContent: (@Composable () -> Unit)? = null,
    val negativeButtonText: String = "Decline",
    val positiveButtonText: String = "Accept",
    val isPositiveActionLoading: Boolean = false,
    val isNegativeActionLoading: Boolean = false,
    val onNegativeAction: () -> Unit = {},
    val onPositiveAction: () -> Unit = {},
    override val priority: NotificationPriority = NotificationPriority.Critical,
    override val autoDismissTimeoutMs: Long? = null, // Persistent
    override val createdAtEpochMs: Long = System.currentTimeMillis(),
) : NotificationPayload

/**
 * Full-content StackedScreen overlay payload.
 */
data class StackedScreenNotification(
    override val id: NotificationId,
    val title: String,
    val subtitle: String? = null,
    val width: Dp? = null,
    val height: Dp? = null,
    val showBackButton: Boolean = true,
    val trailingHeaderAction: (@Composable () -> Unit)? = null,
    val onBack: () -> Unit = {},
    val content: @Composable () -> Unit,
    override val priority: NotificationPriority = NotificationPriority.High,
    override val autoDismissTimeoutMs: Long? = null, // Persistent
    override val createdAtEpochMs: Long = System.currentTimeMillis(),
) : NotificationPayload

/**
 * Corner Message Toast payload.
 */
data class ToastNotification(
    override val id: NotificationId,
    val message: String,
    val variant: ToastVariant = ToastVariant.Info,
    val iconResource: DrawableResource? = null,
    val iconPainter: Painter? = null,
    val actionText: String? = null,
    val onActionClick: (() -> Unit)? = null,
    val showCloseButton: Boolean? = null,
    val progress: Float? = null,
    val onClick: (() -> Unit)? = null,
    override val priority: NotificationPriority = NotificationPriority.Normal,
    override val autoDismissTimeoutMs: Long? = 3_000L,
    override val createdAtEpochMs: Long = System.currentTimeMillis(),
) : NotificationPayload

/**
 * Historical snapshot item for in-memory retention buffer (last 20 items).
 */
data class NotificationHistoryEntry(val id: NotificationId, val type: String, val title: String, val message: String, val timestampEpochMs: Long, val wasDismissedByUser: Boolean)
