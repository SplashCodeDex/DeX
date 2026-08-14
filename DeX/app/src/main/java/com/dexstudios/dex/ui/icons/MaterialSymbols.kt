package com.dexstudios.dex.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Custom Material Symbols Rounded icons for DeX.
 * These are converted from SVG paths to Compose ImageVectors.
 */
object MaterialSymbols {
    val Wifi: ImageVector
        get() = _wifi ?: ImageVector.Builder(
            name = "wifi",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(10.23f, 20.27f)
                quadTo(9.5f, 19.55f, 9.5f, 18.5f)
                reflectiveQuadToRelative(0.73f, -1.77f)
                reflectiveQuadTo(12f, 16f)
                reflectiveQuadToRelative(1.78f, 0.73f)
                reflectiveQuadTo(14.5f, 18.5f)
                reflectiveQuadToRelative(-0.72f, 1.77f)
                reflectiveQuadTo(12f, 21f)
                reflectiveQuadTo(10.23f, 20.27f)
                close()
                moveTo(15.56f, 10.6f)
                quadToRelative(1.69f, 0.6f, 3.06f, 1.65f)
                quadToRelative(0.5f, 0.38f, 0.51f, 0.99f)
                reflectiveQuadTo(18.7f, 14.3f)
                quadToRelative(-0.42f, 0.43f, -1.05f, 0.44f)
                reflectiveQuadTo(16.53f, 14.4f)
                quadToRelative(-0.95f, -0.65f, -2.1f, -1.02f)
                reflectiveQuadTo(12f, 13f)
                reflectiveQuadTo(9.58f, 13.38f)
                reflectiveQuadTo(7.48f, 14.4f)
                quadToRelative(-0.5f, 0.35f, -1.13f, 0.33f)
                reflectiveQuadTo(5.3f, 14.27f)
                quadTo(4.88f, 13.83f, 4.88f, 13.21f)
                reflectiveQuadToRelative(0.5f, -0.99f)
                quadTo(6.75f, 11.18f, 8.44f, 10.59f)
                reflectiveQuadTo(12f, 10f)
                reflectiveQuadToRelative(3.56f, 0.6f)
                close()
                moveTo(17.89f, 5.02f)
                quadToRelative(2.76f, 1.03f, 4.96f, 2.9f)
                quadToRelative(0.5f, 0.42f, 0.52f, 1.05f)
                reflectiveQuadToRelative(-0.42f, 1.07f)
                quadToRelative(-0.43f, 0.43f, -1.05f, 0.44f)
                reflectiveQuadTo(20.78f, 10.1f)
                quadTo(18.98f, 8.63f, 16.74f, 7.81f)
                reflectiveQuadTo(12f, 7f)
                reflectiveQuadTo(7.26f, 7.81f)
                reflectiveQuadTo(3.23f, 10.1f)
                quadTo(2.73f, 10.5f, 2.1f, 10.49f)
                quadTo(1.48f, 10.48f, 1.05f, 10.05f)
                quadTo(0.6f, 9.6f, 0.63f, 8.98f)
                reflectiveQuadTo(1.15f, 7.93f)
                quadTo(3.35f, 6.05f, 6.11f, 5.02f)
                reflectiveQuadTo(12f, 4f)
                reflectiveQuadToRelative(5.89f, 1.02f)
                close()
            }
        }.build().also { _wifi = it }

    val Battery1: ImageVector
        get() = _battery1 ?: batteryFrameBuilder("battery_1") {
            moveTo(4.29f, 14.71f)
            quadTo(4f, 14.43f, 4f, 14f)
            verticalLineTo(10f)
            quadTo(4f, 9.57f, 4.29f, 9.29f)
            reflectiveQuadTo(5f, 9f)
            reflectiveQuadTo(5.71f, 9.29f)
            reflectiveQuadTo(6f, 10f)
            verticalLineToRelative(4f)
            quadToRelative(0f, 0.42f, -0.29f, 0.71f)
            reflectiveQuadTo(5f, 15f)
            quadTo(4.58f, 15f, 4.29f, 14.71f)
            close()
        }.build().also { _battery1 = it }

    val Battery2: ImageVector
        get() = _battery2 ?: batteryFrameBuilder("battery_2") {
            moveTo(4f, 14f)
            verticalLineTo(10f)
            quadTo(4f, 9.57f, 4.29f, 9.29f)
            reflectiveQuadTo(5f, 9f)
            horizontalLineTo(7f)
            quadTo(7.43f, 9f, 7.71f, 9.29f)
            reflectiveQuadTo(8f, 10f)
            verticalLineToRelative(4f)
            quadToRelative(0f, 0.42f, -0.29f, 0.71f)
            reflectiveQuadTo(7f, 15f)
            horizontalLineTo(5f)
            quadTo(4.58f, 15f, 4.29f, 14.71f)
            reflectiveQuadTo(4f, 14f)
            close()
        }.build().also { _battery2 = it }

    val Battery3: ImageVector
        get() = _battery3 ?: batteryFrameBuilder("battery_3") {
            moveTo(4f, 14f)
            verticalLineTo(10f)
            quadTo(4f, 9.57f, 4.29f, 9.29f)
            reflectiveQuadTo(5f, 9f)
            horizontalLineTo(9f)
            quadTo(9.43f, 9f, 9.71f, 9.29f)
            reflectiveQuadTo(10f, 10f)
            verticalLineToRelative(4f)
            quadToRelative(0f, 0.42f, -0.29f, 0.71f)
            reflectiveQuadTo(9f, 15f)
            horizontalLineTo(5f)
            quadTo(4.58f, 15f, 4.29f, 14.71f)
            reflectiveQuadTo(4f, 14f)
            close()
        }.build().also { _battery3 = it }

    val Battery4: ImageVector
        get() = _battery4 ?: batteryFrameBuilder("battery_4") {
            moveTo(4f, 14f)
            verticalLineTo(10f)
            quadTo(4f, 9.57f, 4.29f, 9.29f)
            reflectiveQuadTo(5f, 9f)
            horizontalLineToRelative(6f)
            quadToRelative(0.43f, 0f, 0.71f, 0.29f)
            reflectiveQuadTo(12f, 10f)
            verticalLineToRelative(4f)
            quadToRelative(0f, 0.42f, -0.29f, 0.71f)
            reflectiveQuadTo(11f, 15f)
            horizontalLineTo(5f)
            quadTo(4.58f, 15f, 4.29f, 14.71f)
            reflectiveQuadTo(4f, 14f)
            close()
        }.build().also { _battery4 = it }

    val Battery5: ImageVector
        get() = _battery5 ?: batteryFrameBuilder("battery_5") {
            moveTo(4f, 14f)
            verticalLineTo(10f)
            quadTo(4f, 9.57f, 4.29f, 9.29f)
            reflectiveQuadTo(5f, 9f)
            horizontalLineToRelative(8f)
            quadToRelative(0.43f, 0f, 0.71f, 0.29f)
            reflectiveQuadTo(14f, 10f)
            verticalLineToRelative(4f)
            quadToRelative(0f, 0.42f, -0.29f, 0.71f)
            reflectiveQuadTo(13f, 15f)
            horizontalLineTo(5f)
            quadTo(4.58f, 15f, 4.29f, 14.71f)
            reflectiveQuadTo(4f, 14f)
            close()
        }.build().also { _battery5 = it }

    val Battery6: ImageVector
        get() = _battery6 ?: batteryFrameBuilder("battery_6") {
            moveTo(4f, 14f)
            verticalLineTo(10f)
            quadTo(4f, 9.57f, 4.29f, 9.29f)
            reflectiveQuadTo(5f, 9f)
            horizontalLineTo(15f)
            quadToRelative(0.43f, 0f, 0.71f, 0.29f)
            reflectiveQuadTo(16f, 10f)
            verticalLineToRelative(4f)
            quadToRelative(0f, 0.42f, -0.29f, 0.71f)
            reflectiveQuadTo(15f, 15f)
            horizontalLineTo(5f)
            quadTo(4.58f, 15f, 4.29f, 14.71f)
            reflectiveQuadTo(4f, 14f)
            close()
        }.build().also { _battery6 = it }

    val BatteryFull: ImageVector
        get() = _batteryFull ?: ImageVector.Builder(
            name = "battery_full",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(4f, 18f)
                quadTo(2.75f, 18f, 1.88f, 17.13f)
                reflectiveQuadTo(1f, 15f)
                verticalLineTo(9f)
                quadTo(1f, 7.75f, 1.88f, 6.88f)
                reflectiveQuadTo(4f, 6f)
                horizontalLineTo(17.5f)
                quadToRelative(1.25f, 0f, 2.13f, 0.88f)
                reflectiveQuadTo(20.5f, 9f)
                verticalLineToRelative(6f)
                quadToRelative(0f, 1.25f, -0.88f, 2.13f)
                reflectiveQuadTo(17.5f, 18f)
                horizontalLineTo(4f)
                close()
                moveTo(21.5f, 14.5f)
                verticalLineToRelative(-5f)
                horizontalLineTo(22f)
                quadToRelative(0.43f, 0f, 0.71f, 0.29f)
                reflectiveQuadTo(23f, 10.5f)
                verticalLineToRelative(3f)
                quadToRelative(0f, 0.42f, -0.29f, 0.71f)
                reflectiveQuadTo(22f, 14.5f)
                horizontalLineTo(21.5f)
                close()
            }
        }.build().also { _batteryFull = it }

    val Notifications: ImageVector
        get() = _notifications ?: ImageVector.Builder(
            name = "notifications",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 22f)
                quadToRelative(-0.82f, 0f, -1.41f, -0.59f)
                reflectiveQuadTo(10f, 20f)
                horizontalLineTo(14f)
                quadToRelative(0f, 0.82f, -0.59f, 1.41f)
                reflectiveQuadTo(12f, 22f)
                close()
                moveTo(5f, 19f)
                verticalLineToRelative(-2f)
                horizontalLineToRelative(1f)
                verticalLineToRelative(-7f)
                quadToRelative(0f, -2.05f, 1.29f, -3.7f)
                reflectiveQuadTo(10.5f, 4.3f)
                verticalLineTo(3.5f)
                quadToRelative(0f, -0.63f, 0.44f, -1.07f)
                reflectiveQuadTo(12f, 2f)
                reflectiveQuadToRelative(1.07f, 0.44f)
                reflectiveQuadTo(13.5f, 3.5f)
                verticalLineToRelative(0.8f)
                quadToRelative(1.96f, 0.35f, 3.23f, 2f)
                reflectiveQuadTo(18f, 10f)
                verticalLineToRelative(7f)
                horizontalLineToRelative(1f)
                verticalLineToRelative(2f)
                horizontalLineTo(5f)
                close()
            }
        }.build().also { _notifications = it }

    val Check: ImageVector
        get() = _check ?: ImageVector.Builder(
            name = "check",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(9.55f, 18f)
                lineTo(3.85f, 12.3f)
                quadToRelative(-0.275f, -0.275f, -0.275f, -0.7f)
                reflectiveQuadToRelative(0.275f, -0.7f)
                quadToRelative(0.275f, -0.275f, 0.7f, -0.275f)
                reflectiveQuadToRelative(0.7f, 0.275f)
                lineTo(9.55f, 15.15f)
                lineTo(18.73f, 5.97f)
                quadToRelative(0.275f, -0.275f, 0.7f, -0.275f)
                reflectiveQuadToRelative(0.7f, 0.275f)
                quadToRelative(0.275f, 0.275f, 0.275f, 0.7f)
                reflectiveQuadToRelative(-0.275f, 0.7f)
                lineTo(9.55f, 18f)
                close()
            }
        }.build().also { _check = it }

    val CheckCircle: ImageVector
        get() = _checkCircle ?: ImageVector.Builder(
            name = "check_circle",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
                moveTo(12.0f, 22.0f)
                quadToRelative(-2.075f, 0.0f, -3.9f, -0.788f)
                reflectiveQuadToRelative(-3.175f, -2.137f)
                quadToRelative(-1.35f, -1.35f, -2.137f, -3.175f)
                reflectiveQuadTo(2.0f, 12.0f)
                quadToRelative(0.0f, -2.075f, 0.788f, -3.9f)
                reflectiveQuadToRelative(2.137f, -3.175f)
                quadToRelative(1.35f, -1.35f, 3.175f, -2.137f)
                reflectiveQuadTo(12.0f, 2.0f)
                quadToRelative(2.075f, 0.0f, 3.9f, 0.788f)
                reflectiveQuadToRelative(3.175f, 2.137f)
                quadToRelative(1.35f, -1.35f, 2.137f, -3.175f)
                reflectiveQuadTo(22.0f, 12.0f)
                quadToRelative(0.0f, 2.075f, -0.788f, 3.9f)
                reflectiveQuadToRelative(-2.137f, 3.175f)
                quadToRelative(-1.35f, -1.35f, -3.175f, -2.137f)
                reflectiveQuadTo(12.0f, 22.0f)
                close()
                moveTo(10.18f, 17.46f)
                lineToRelative(7.345f, -7.345f)
                quadToRelative(0.3575f, -0.3575f, 0.3575f, -0.91f)
                reflectiveQuadToRelative(-0.3575f, -0.91f)
                quadToRelative(-0.3575f, -0.3575f, -0.91f, -0.3575f)
                reflectiveQuadToRelative(-0.91f, 0.3575f)
                lineTo(10.18f, 13.82f)
                lineTo(7.45f, 11.09f)
                quadToRelative(-0.3575f, -0.3575f, -0.91f, -0.3575f)
                reflectiveQuadToRelative(-0.91f, 0.3575f)
                quadToRelative(-0.3575f, 0.3575f, -0.3575f, 0.91f)
                reflectiveQuadToRelative(0.3575f, 0.91f)
                lineToRelative(3.705f, 3.705f)
                quadToRelative(0.39f, 0.39f, 0.91f, 0.39f)
                reflectiveQuadToRelative(0.9425f, -0.39f)
                close()
            }
        }.build().also { _checkCircle = it }

    val CheckCircleOutlined: ImageVector
        get() = _checkCircleOutlined ?: ImageVector.Builder(
            name = "check_circle_outlined",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 22f)
                quadToRelative(-2.075f, 0f, -3.9f, -0.788f)
                reflectiveQuadToRelative(-3.175f, -2.137f)
                quadToRelative(-1.35f, -1.35f, -2.137f, -3.175f)
                reflectiveQuadTo(2f, 12f)
                quadToRelative(0f, -2.075f, 0.788f, -3.9f)
                reflectiveQuadToRelative(2.137f, -3.175f)
                quadToRelative(1.35f, -1.35f, 3.175f, -2.137f)
                reflectiveQuadTo(12f, 2f)
                quadToRelative(2.075f, 0f, 3.9f, 0.788f)
                reflectiveQuadToRelative(3.175f, 2.137f)
                quadToRelative(1.35f, 1.35f, 2.137f, 3.175f)
                reflectiveQuadTo(22f, 12f)
                quadToRelative(0f, 2.075f, -0.788f, 3.9f)
                reflectiveQuadToRelative(-2.137f, 3.175f)
                quadToRelative(-1.35f, 1.35f, -3.175f, 2.137f)
                reflectiveQuadTo(12f, 22f)
                close()
                moveTo(12f, 20f)
                quadToRelative(3.325f, 0f, 5.663f, -2.337f)
                reflectiveQuadTo(20f, 12f)
                reflectiveQuadToRelative(-2.337f, -5.663f)
                reflectiveQuadTo(12f, 4f)
                reflectiveQuadToRelative(-5.663f, 2.337f)
                reflectiveQuadTo(4f, 12f)
                reflectiveQuadToRelative(2.337f, 5.663f)
                reflectiveQuadTo(12f, 20f)
                close()
            }
        }.build().also { _checkCircleOutlined = it }

    val Close: ImageVector
        get() = _close ?: ImageVector.Builder(
            name = "close",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(18.3f, 5.71f)
                quadToRelative(-0.39f, -0.39f, -1.02f, -0.39f)
                reflectiveQuadToRelative(-1.02f, 0.39f)
                lineTo(12f, 10.59f)
                lineTo(7.71f, 6.3f)
                quadToRelative(-0.39f, -0.39f, -1.02f, -0.39f)
                reflectiveQuadToRelative(-1.02f, 0.39f)
                quadToRelative(-0.39f, 0.39f, -0.39f, 1.02f)
                reflectiveQuadToRelative(0.39f, 1.02f)
                lineTo(9.41f, 12f)
                lineTo(5.71f, 15.71f)
                quadToRelative(-0.39f, 0.39f, -0.39f, 1.02f)
                reflectiveQuadToRelative(0.39f, 1.02f)
                quadToRelative(0.39f, 0.39f, 1.02f, 0.39f)
                reflectiveQuadToRelative(1.02f, -0.39f)
                lineTo(12f, 13.41f)
                lineToRelative(4.29f, 4.29f)
                quadToRelative(0.39f, 0.39f, 1.02f, 0.39f)
                reflectiveQuadToRelative(1.02f, -0.39f)
                quadToRelative(0.39f, -0.39f, 0.39f, -1.02f)
                reflectiveQuadToRelative(-0.39f, -1.02f)
                lineTo(14.59f, 12f)
                lineToRelative(4.29f, -4.29f)
                quadToRelative(0.39f, -0.39f, 0.39f, -1.02f)
                reflectiveQuadToRelative(-0.39f, -1.02f)
                close()
            }
        }.build().also { _close = it }

    val Pin: ImageVector
        get() = _pin ?: ImageVector.Builder(
            name = "pin",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
                // Outer Rounded Box
                moveTo(4f, 20f)
                quadTo(3.175f, 20f, 2.588f, 19.412f)
                quadTo(2f, 18.825f, 2f, 18f)
                verticalLineTo(6f)
                quadTo(2f, 5.175f, 2.588f, 4.588f)
                quadTo(3.175f, 4f, 4f, 4f)
                horizontalLineTo(20f)
                quadTo(20.825f, 4f, 21.412f, 4.588f)
                quadTo(22f, 5.175f, 22f, 6f)
                verticalLineTo(18f)
                quadTo(22f, 18.825f, 21.412f, 19.412f)
                quadTo(20.825f, 20f, 20f, 20f)
                close()

                // Digit 1
                moveTo(7f, 15f)
                horizontalLineTo(9f)
                verticalLineTo(9f)
                horizontalLineTo(7f)
                verticalLineTo(10f)
                horizontalLineTo(8f)
                verticalLineTo(15f)
                close()

                // Digit 2
                moveTo(10.5f, 15f)
                horizontalLineTo(13.5f)
                quadTo(13.725f, 15f, 13.863f, 14.863f)
                quadTo(14f, 14.725f, 14f, 14.5f)
                verticalLineTo(13.5f)
                quadTo(14f, 13.275f, 13.863f, 13.138f)
                quadTo(13.725f, 13f, 13.5f, 13f)
                horizontalLineTo(11.5f)
                verticalLineTo(12f)
                horizontalLineTo(13.5f)
                quadTo(13.725f, 12f, 13.863f, 11.863f)
                quadTo(14f, 11.725f, 14f, 11.5f)
                verticalLineTo(10.5f)
                quadTo(14f, 10.275f, 13.863f, 10.138f)
                quadTo(13.725f, 10f, 13.5f, 10f)
                horizontalLineTo(10.5f)
                verticalLineTo(11f)
                horizontalLineTo(12.5f)
                verticalLineTo(12f)
                horizontalLineTo(10.5f)
                quadTo(10.275f, 12f, 10.138f, 12.138f)
                quadTo(10f, 12.275f, 10f, 12.5f)
                verticalLineTo(14.5f)
                quadTo(10f, 14.725f, 10.138f, 14.863f)
                quadTo(10.275f, 15f, 10.5f, 15f)
                close()

                // Digit 3
                moveTo(15.5f, 15f)
                horizontalLineTo(18.5f)
                quadTo(18.725f, 15f, 18.863f, 14.863f)
                quadTo(19f, 14.725f, 19f, 14.5f)
                verticalLineTo(13.25f)
                quadTo(19f, 13.025f, 18.863f, 12.888f)
                quadTo(18.725f, 12.75f, 18.5f, 12.75f)
                quadTo(18.725f, 12.75f, 18.863f, 12.612f)
                quadTo(19f, 12.475f, 19f, 12.25f)
                verticalLineTo(10.5f)
                quadTo(19f, 10.275f, 18.863f, 10.138f)
                quadTo(18.725f, 10f, 18.5f, 10f)
                horizontalLineTo(15.5f)
                verticalLineTo(11f)
                horizontalLineTo(17.5f)
                verticalLineTo(12.25f)
                horizontalLineTo(16.5f)
                verticalLineTo(13.25f)
                horizontalLineTo(17.5f)
                verticalLineTo(14f)
                horizontalLineTo(15.5f)
                verticalLineTo(15f)
                close()
            }
        }.build().also { _pin = it }

    val Google: ImageVector
        get() = _google ?: ImageVector.Builder(
            name = "google",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            // Blue path
            path(fill = SolidColor(Color(0xFF4285F4))) {
                moveTo(23.49f, 12.27f)
                curveTo(23.49f, 11.48f, 23.42f, 10.73f, 23.3f, 10f)
                horizontalLineTo(12f)
                verticalLineToRelative(4.51f)
                horizontalLineToRelative(6.5f)
                curveTo(18.22f, 15.67f, 17.5f, 16.73f, 16.5f, 17.39f)
                verticalLineToRelative(2.98f)
                horizontalLineToRelative(3.24f)
                curveTo(21.64f, 18.58f, 23.49f, 15.68f, 23.49f, 12.27f)
                close()
            }
            // Green path
            path(fill = SolidColor(Color(0xFF34A853))) {
                moveTo(12f, 24f)
                curveTo(15.24f, 24f, 17.96f, 22.93f, 19.74f, 20.37f)
                lineToRelative(-3.24f, -2.98f)
                curveTo(15.54f, 18.06f, 13.94f, 18.5f, 12f, 18.5f)
                curveTo(8.87f, 18.5f, 6.23f, 16.38f, 5.28f, 13.53f)
                horizontalLineTo(1.92f)
                verticalLineToRelative(3.11f)
                curveTo(3.91f, 20.59f, 7.73f, 24f, 12f, 24f)
                close()
            }
            // Yellow path
            path(fill = SolidColor(Color(0xFFFBBC05))) {
                moveTo(5.28f, 13.53f)
                curveTo(5.03f, 12.78f, 4.89f, 11.99f, 4.89f, 11.16f)
                curveTo(4.89f, 10.33f, 5.03f, 9.54f, 5.28f, 8.79f)
                verticalLineTo(5.68f)
                horizontalLineTo(1.92f)
                curveTo(1.1f, 7.33f, 0.63f, 9.19f, 0.63f, 11.16f)
                curveTo(0.63f, 13.13f, 1.1f, 14.99f, 1.92f, 16.64f)
                lineToRelative(3.36f, -3.11f)
                close()
            }
            // Red path
            path(fill = SolidColor(Color(0xFFEA4335))) {
                moveTo(12f, 4.32f)
                curveTo(13.76f, 4.32f, 15.34f, 4.93f, 16.59f, 6.13f)
                lineToRelative(3.24f, -3.24f)
                curveTo(17.96f, 0.88f, 15.24f, 0f, 12f, 0f)
                curveTo(7.73f, 0f, 3.91f, 3.41f, 1.92f, 7.55f)
                lineToRelative(3.36f, 3.11f)
                curveTo(6.23f, 7.92f, 8.87f, 5.8f, 12f, 5.8f)
                close()
            }
        }.build().also { _google = it }

    private fun batteryFrameBuilder(name: String, fillPath: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit): ImageVector.Builder {
        return ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(4f, 18f)
                quadTo(2.75f, 18f, 1.88f, 17.13f)
                reflectiveQuadTo(1f, 15f)
                verticalLineTo(9f)
                quadTo(1f, 7.75f, 1.88f, 6.88f)
                reflectiveQuadTo(4f, 6f)
                horizontalLineTo(17.5f)
                quadToRelative(1.25f, 0f, 2.13f, 0.88f)
                reflectiveQuadTo(20.5f, 9f)
                verticalLineToRelative(6f)
                quadToRelative(0f, 1.25f, -0.88f, 2.13f)
                reflectiveQuadTo(17.5f, 18f)
                horizontalLineTo(4f)
                close()
                moveTo(4f, 16f)
                horizontalLineTo(17.5f)
                quadToRelative(0.43f, 0f, 0.71f, -0.29f)
                reflectiveQuadTo(18.5f, 15f)
                verticalLineTo(9f)
                quadToRelative(0f, -0.43f, -0.29f, -0.71f)
                reflectiveQuadTo(17.5f, 8f)
                horizontalLineTo(4f)
                quadTo(3.58f, 8f, 3.29f, 8.29f)
                reflectiveQuadTo(3f, 9f)
                verticalLineToRelative(6f)
                quadToRelative(0f, 0.42f, 0.29f, 0.71f)
                reflectiveQuadTo(4f, 16f)
                close()
                moveTo(21.5f, 14.5f)
                verticalLineToRelative(-5f)
                horizontalLineTo(22f)
                quadToRelative(0.43f, 0f, 0.71f, 0.29f)
                reflectiveQuadTo(23f, 10.5f)
                verticalLineToRelative(3f)
                quadToRelative(0f, 0.42f, -0.29f, 0.71f)
                reflectiveQuadTo(22f, 14.5f)
                horizontalLineTo(21.5f)
                close()
                fillPath()
            }
        }
    }

    val BatteryCharging: ImageVector
        get() = _batteryCharging ?: batteryFrameBuilder("battery_charging") {
            moveTo(11f, 15f)
            lineTo(13f, 12f)
            horizontalLineTo(10f)
            lineTo(12f, 9f)
            horizontalLineTo(9f)
            lineTo(7f, 12f)
            horizontalLineTo(10f)
            close()
        }.build().also { _batteryCharging = it }

    val Share: ImageVector
        get() = _share ?: ImageVector.Builder(
            name = "share",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(18f, 22f)
                quadToRelative(-1.25f, 0f, -2.125f, -0.875f)
                reflectiveQuadTo(15f, 19f)
                quadToRelative(0f, -0.1f, 0.025f, -0.25f)
                reflectiveQuadToRelative(0.075f, -0.3f)
                lineToRelative(-7.05f, -4.1f)
                quadToRelative(-0.425f, 0.425f, -0.987f, 0.638f)
                reflectiveQuadTo(6f, 15f)
                quadToRelative(-1.25f, 0f, -2.125f, -0.875f)
                reflectiveQuadTo(3f, 12f)
                reflectiveQuadToRelative(0.875f, -2.125f)
                reflectiveQuadTo(6f, 9f)
                quadToRelative(0.575f, 0f, 1.138f, 0.213f)
                reflectiveQuadToRelative(0.987f, 0.637f)
                lineToRelative(7.05f, -4.1f)
                quadToRelative(-0.05f, -0.15f, -0.075f, -0.3f)
                reflectiveQuadTo(15f, 5f)
                quadToRelative(0f, -1.25f, 0.875f, -2.125f)
                reflectiveQuadTo(18f, 2f)
                reflectiveQuadToRelative(2.125f, 0.875f)
                reflectiveQuadTo(23f, 5f)
                reflectiveQuadToRelative(-0.875f, 2.125f)
                reflectiveQuadTo(18f, 8f)
                quadToRelative(-0.575f, 0f, -1.137f, -0.212f)
                reflectiveQuadToRelative(-0.988f, -0.638f)
                lineToRelative(-7.05f, 4.1f)
                quadToRelative(0.05f, 0.15f, 0.075f, 0.3f)
                reflectiveQuadTo(9f, 12f)
                reflectiveQuadToRelative(-0.025f, 0.25f)
                reflectiveQuadToRelative(-0.075f, 0.3f)
                lineToRelative(7.05f, 4.1f)
                quadToRelative(0.425f, -0.425f, 0.988f, -0.637f)
                reflectiveQuadTo(18f, 16f)
                quadToRelative(1.25f, 0f, 2.125f, 0.875f)
                reflectiveQuadTo(23f, 19f)
                reflectiveQuadToRelative(-0.875f, 2.125f)
                reflectiveQuadTo(18f, 22f)
                close()
            }
        }.build().also { _share = it }

    val Sort: ImageVector
        get() = _sort ?: ImageVector.Builder(
            name = "sort",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(3f, 18f)
                verticalLineToRelative(-2f)
                horizontalLineToRelative(6f)
                verticalLineToRelative(2f)
                horizontalLineTo(3f)
                close()
                moveTo(3f, 13f)
                verticalLineToRelative(-2f)
                horizontalLineToRelative(12f)
                verticalLineToRelative(2f)
                horizontalLineTo(3f)
                close()
                moveTo(3f, 8f)
                verticalLineTo(6f)
                horizontalLineToRelative(18f)
                verticalLineToRelative(2f)
                horizontalLineTo(3f)
                close()
            }
        }.build().also { _sort = it }

    val ExpandMore: ImageVector
        get() = _expandMore ?: ImageVector.Builder(
            name = "expand_more",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 15.4f)
                lineToRelative(-6f, -6f)
                lineToRelative(1.4f, -1.4f)
                lineToRelative(4.6f, 4.6f)
                lineToRelative(4.6f, -4.6f)
                lineTo(18f, 9.4f)
                close()
            }
        }.build().also { _expandMore = it }

    val Photo: ImageVector
        get() = _photo ?: ImageVector.Builder(
            name = "photo",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(19f, 19f)
                horizontalLineTo(5f)
                verticalLineTo(5f)
                horizontalLineTo(19f)
                verticalLineTo(19f)
                close()
                moveTo(5f, 21f)
                quadTo(4.15f, 21f, 3.58f, 20.43f)
                reflectiveQuadTo(3f, 19f)
                verticalLineTo(5f)
                quadToRelative(0f, -0.85f, 0.58f, -1.43f)
                reflectiveQuadTo(5f, 3f)
                horizontalLineTo(19f)
                quadToRelative(0.85f, 0f, 1.43f, 0.58f)
                reflectiveQuadTo(21f, 5f)
                verticalLineTo(19f)
                quadToRelative(0f, 0.85f, -0.58f, 1.43f)
                reflectiveQuadTo(19f, 21f)
                horizontalLineTo(5f)
                close()
                moveTo(16f, 13f)
                lineTo(13.5f, 16.5f)
                lineTo(11.75f, 14.15f)
                lineTo(9f, 18f)
                horizontalLineTo(17f)
                lineTo(16f, 13f)
                close()
            }
        }.build().also { _photo = it }

    val VideoCamera: ImageVector
        get() = _videoCamera ?: ImageVector.Builder(
            name = "video_camera",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(14f, 13f)
                verticalLineTo(8f)
                quadToRelative(0f, -0.825f, -0.588f, -1.413f)
                reflectiveQuadTo(12f, 6f)
                horizontalLineTo(4f)
                quadToRelative(-0.825f, 0f, -1.413f, 0.588f)
                reflectiveQuadTo(2f, 8f)
                verticalLineTo(16f)
                quadToRelative(0f, 0.825f, 0.588f, 1.413f)
                reflectiveQuadTo(4f, 18f)
                horizontalLineTo(12f)
                quadToRelative(0.825f, 0f, 1.413f, -0.588f)
                reflectiveQuadTo(14f, 16f)
                verticalLineTo(11f)
                lineToRelative(4f, 4f)
                verticalLineTo(9f)
                lineTo(14f, 13f)
                close()
            }
        }.build().also { _videoCamera = it }

    val Article: ImageVector
        get() = _article ?: ImageVector.Builder(
            name = "article",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(5f, 21f)
                quadTo(4.15f, 21f, 3.58f, 20.43f)
                reflectiveQuadTo(3f, 19f)
                verticalLineTo(5f)
                quadToRelative(0f, -0.85f, 0.58f, -1.43f)
                reflectiveQuadTo(5f, 3f)
                horizontalLineTo(19f)
                quadToRelative(0.85f, 0f, 1.43f, 0.58f)
                reflectiveQuadTo(21f, 5f)
                verticalLineTo(19f)
                quadToRelative(0f, 0.85f, -0.58f, 1.43f)
                reflectiveQuadTo(19f, 21f)
                horizontalLineTo(5f)
                close()
                moveTo(5f, 19f)
                horizontalLineTo(19f)
                verticalLineTo(5f)
                horizontalLineTo(5f)
                verticalLineTo(19f)
                close()
                moveTo(7f, 17f)
                horizontalLineTo(17f)
                verticalLineTo(15f)
                horizontalLineTo(7f)
                verticalLineTo(17f)
                close()
                moveTo(7f, 13f)
                horizontalLineTo(17f)
                verticalLineTo(11f)
                horizontalLineTo(7f)
                verticalLineTo(13f)
                close()
                moveTo(7f, 9f)
                horizontalLineTo(13f)
                verticalLineTo(7f)
                horizontalLineTo(7f)
                verticalLineTo(9f)
                close()
            }
        }.build().also { _article = it }

    val Inventory: ImageVector
        get() = _inventory ?: ImageVector.Builder(
            name = "inventory",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(5f, 22f)
                quadToRelative(-0.825f, 0f, -1.413f, -0.588f)
                reflectiveQuadTo(3f, 20f)
                verticalLineTo(6f)
                quadToRelative(0f, -0.825f, 0.588f, -1.413f)
                reflectiveQuadTo(5f, 4f)
                horizontalLineTo(7.1f)
                quadToRelative(0.4f, -1.15f, 1.413f, -1.825f)
                reflectiveQuadTo(11f, 1.5f)
                quadToRelative(1.175f, 0f, 2.188f, 0.675f)
                reflectiveQuadTo(14.6f, 4f)
                horizontalLineTo(17f)
                quadToRelative(0.825f, 0f, 1.413f, 0.588f)
                reflectiveQuadTo(19f, 6f)
                verticalLineTo(20f)
                quadToRelative(0f, 0.825f, -0.588f, 1.413f)
                reflectiveQuadTo(17f, 22f)
                horizontalLineTo(5f)
                close()
                moveTo(11f, 4.5f)
                quadToRelative(0.425f, 0f, 0.713f, -0.288f)
                reflectiveQuadTo(12f, 3.5f)
                reflectiveQuadToRelative(-0.288f, -0.712f)
                reflectiveQuadTo(11f, 2.5f)
                reflectiveQuadToRelative(-0.712f, 0.288f)
                reflectiveQuadTo(10f, 3.5f)
                reflectiveQuadToRelative(0.288f, 0.713f)
                reflectiveQuadTo(11f, 4.5f)
                close()
            }
        }.build().also { _inventory = it }

    val History: ImageVector
        get() = _history ?: ImageVector.Builder(
            name = "history",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(13f, 3f)
                quadToRelative(4.15f, 0f, 7.075f, 2.925f)
                reflectiveQuadTo(23f, 13f)
                reflectiveQuadToRelative(-2.925f, 7.075f)
                reflectiveQuadTo(13f, 23f)
                reflectiveQuadToRelative(-7.075f, -2.925f)
                reflectiveQuadTo(3f, 13f)
                horizontalLineTo(1f)
                lineTo(4f, 10f)
                lineTo(7f, 13f)
                horizontalLineTo(5f)
                quadToRelative(0f, 3.325f, 2.338f, 5.663f)
                reflectiveQuadTo(13f, 21f)
                reflectiveQuadToRelative(5.663f, -2.337f)
                reflectiveQuadTo(21f, 13f)
                reflectiveQuadToRelative(-2.337f, -5.663f)
                reflectiveQuadTo(13f, 5f)
                quadToRelative(-1.725f, 0f, -3.225f, 0.725f)
                reflectiveQuadTo(7.2f, 7.7f)
                lineTo(5.8f, 6.3f)
                quadToRelative(1.175f, -1.25f, 2.738f, -1.975f)
                reflectiveQuadTo(13f, 3f)
                close()
                moveTo(12f, 14f)
                horizontalLineTo(16.5f)
                verticalLineTo(12f)
                horizontalLineTo(14f)
                verticalLineTo(8f)
                horizontalLineTo(12f)
                verticalLineTo(14f)
                close()
            }
        }.build().also { _history = it }

    val Folder: ImageVector
        get() = _folder ?: ImageVector.Builder(
            name = "folder",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(4f, 20f)
                quadToRelative(-0.825f, 0f, -1.413f, -0.588f)
                reflectiveQuadTo(2f, 18f)
                verticalLineTo(6f)
                quadToRelative(0f, -0.825f, 0.588f, -1.413f)
                reflectiveQuadTo(4f, 4f)
                horizontalLineToRelative(6f)
                lineToRelative(2f, 2f)
                horizontalLineToRelative(8f)
                quadToRelative(0.825f, 0f, 1.413f, 0.588f)
                reflectiveQuadTo(22f, 8f)
                verticalLineToRelative(10f)
                quadToRelative(0f, 0.825f, -0.588f, 1.413f)
                reflectiveQuadTo(20f, 20f)
                horizontalLineTo(4f)
                close()
            }
        }.build().also { _folder = it }

    val Delete: ImageVector
        get() = _delete ?: ImageVector.Builder(
            name = "delete",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(7f, 21f)
                quadToRelative(-0.825f, 0f, -1.413f, -0.588f)
                reflectiveQuadTo(5f, 19f)
                verticalLineTo(6f)
                horizontalLineTo(4f)
                verticalLineTo(4f)
                horizontalLineTo(8f)
                verticalLineTo(3f)
                horizontalLineTo(16f)
                verticalLineTo(4f)
                horizontalLineTo(20f)
                verticalLineTo(6f)
                horizontalLineTo(19f)
                verticalLineTo(19f)
                quadToRelative(0f, 0.825f, -0.588f, 1.413f)
                reflectiveQuadTo(17f, 21f)
                horizontalLineTo(7f)
                close()
                moveTo(17f, 6f)
                horizontalLineTo(7f)
                verticalLineTo(19f)
                horizontalLineTo(17f)
                verticalLineTo(6f)
                close()
                moveTo(9f, 17f)
                horizontalLineTo(11f)
                verticalLineTo(8f)
                horizontalLineTo(9f)
                verticalLineTo(17f)
                close()
                moveTo(13f, 17f)
                horizontalLineTo(15f)
                verticalLineTo(8f)
                horizontalLineTo(13f)
                verticalLineTo(17f)
                close()
            }
        }.build().also { _delete = it }

    val GridView: ImageVector
        get() = _gridView ?: ImageVector.Builder(
            name = "grid_view",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(3f, 11f)
                horizontalLineToRelative(8f)
                verticalLineTo(3f)
                horizontalLineTo(3f)
                verticalLineToRelative(8f)
                close()
                moveTo(5f, 5f)
                horizontalLineToRelative(4f)
                verticalLineToRelative(4f)
                horizontalLineTo(5f)
                verticalLineTo(5f)
                close()
                moveTo(13f, 11f)
                horizontalLineToRelative(8f)
                verticalLineTo(3f)
                horizontalLineToRelative(-8f)
                verticalLineToRelative(8f)
                close()
                moveTo(15f, 5f)
                horizontalLineToRelative(4f)
                verticalLineToRelative(4f)
                horizontalLineToRelative(-4f)
                verticalLineTo(5f)
                close()
                moveTo(3f, 21f)
                horizontalLineToRelative(8f)
                verticalLineToRelative(-8f)
                horizontalLineTo(3f)
                verticalLineToRelative(8f)
                close()
                moveTo(5f, 15f)
                horizontalLineToRelative(4f)
                verticalLineToRelative(4f)
                horizontalLineTo(5f)
                verticalLineToRelative(-4f)
                close()
                moveTo(13f, 21f)
                horizontalLineToRelative(8f)
                verticalLineToRelative(-8f)
                horizontalLineToRelative(-8f)
                verticalLineToRelative(8f)
                close()
                moveTo(15f, 15f)
                horizontalLineToRelative(4f)
                verticalLineToRelative(4f)
                horizontalLineToRelative(-4f)
                verticalLineToRelative(-4f)
                close()
            }
        }.build().also { _gridView = it }

    val ViewList: ImageVector
        get() = _viewList ?: ImageVector.Builder(
            name = "view_list",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(3f, 14f)
                horizontalLineToRelative(4f)
                verticalLineToRelative(-4f)
                horizontalLineTo(3f)
                verticalLineToRelative(4f)
                close()
                moveTo(3f, 19f)
                horizontalLineToRelative(4f)
                verticalLineToRelative(-4f)
                horizontalLineTo(3f)
                verticalLineToRelative(4f)
                close()
                moveTo(3f, 9f)
                horizontalLineToRelative(4f)
                verticalLineTo(5f)
                horizontalLineTo(3f)
                verticalLineToRelative(4f)
                close()
                moveTo(8f, 14f)
                horizontalLineToRelative(13f)
                verticalLineToRelative(-4f)
                horizontalLineTo(8f)
                verticalLineToRelative(4f)
                close()
                moveTo(8f, 19f)
                horizontalLineToRelative(13f)
                verticalLineToRelative(-4f)
                horizontalLineTo(8f)
                verticalLineToRelative(4f)
                close()
                moveTo(8f, 5f)
                verticalLineToRelative(4f)
                horizontalLineToRelative(13f)
                verticalLineTo(5f)
                horizontalLineTo(8f)
                close()
            }
        }.build().also { _viewList = it }

    private var _google: ImageVector? = null
    private var _wifi: ImageVector? = null
    private var _batteryCharging: ImageVector? = null
    private var _battery1: ImageVector? = null
    private var _battery2: ImageVector? = null
    private var _battery3: ImageVector? = null
    private var _battery4: ImageVector? = null
    private var _battery5: ImageVector? = null
    private var _battery6: ImageVector? = null
    private var _batteryFull: ImageVector? = null
    private var _notifications: ImageVector? = null
    private var _check: ImageVector? = null
    private var _checkCircle: ImageVector? = null
    private var _checkCircleOutlined: ImageVector? = null
    private var _close: ImageVector? = null
    private var _pin: ImageVector? = null
    private var _share: ImageVector? = null
    private var _sort: ImageVector? = null
    private var _expandMore: ImageVector? = null
    private var _photo: ImageVector? = null
    private var _videoCamera: ImageVector? = null
    private var _article: ImageVector? = null
    private var _inventory: ImageVector? = null
    private var _history: ImageVector? = null
    private var _folder: ImageVector? = null
    private var _delete: ImageVector? = null
    private var _gridView: ImageVector? = null
    private var _viewList: ImageVector? = null
}
