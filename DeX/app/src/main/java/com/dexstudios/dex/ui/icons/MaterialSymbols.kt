package com.dexstudios.dex.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Custom Material Symbols icons for DeX.
 * These are converted from official Material Symbols Kotlin code.
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
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel,
                strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero,
            ) {
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
                moveTo(6.35f, 15.35f)
                lineTo(4.25f, 13.2f)
                quadTo(5.73f, 11.73f, 7.71f, 10.86f)
                reflectiveQuadTo(12f, 10f)
                reflectiveQuadToRelative(4.29f, 0.88f)
                reflectiveQuadToRelative(3.46f, 2.38f)
                lineToRelative(-2.1f, 2.1f)
                quadToRelative(-1.1f, -1.1f, -2.55f, -1.72f)
                reflectiveQuadTo(12f, 13f)
                reflectiveQuadTo(8.9f, 13.63f)
                reflectiveQuadTo(6.35f, 15.35f)
                close()
                moveTo(2.1f, 11.1f)
                lineTo(0f, 9f)
                quadTo(2.3f, 6.65f, 5.38f, 5.32f)
                reflectiveQuadTo(12f, 4f)
                reflectiveQuadToRelative(6.63f, 1.32f)
                reflectiveQuadTo(24f, 9f)
                lineToRelative(-2.1f, 2.1f)
                quadTo(19.98f, 9.17f, 17.44f, 8.09f)
                reflectiveQuadTo(12f, 7f)
                reflectiveQuadTo(6.56f, 8.09f)
                reflectiveQuadTo(2.1f, 11.1f)
                close()
            }
        }.build().also { _wifi = it }

    val Check: ImageVector
        get() = _check ?: ImageVector.Builder(
            name = "check",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel,
                strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero,
            ) {
                moveTo(9.55f, 18f)
                lineTo(3.85f, 12.3f)
                lineTo(5.28f, 10.88f)
                lineToRelative(4.28f, 4.28f)
                lineTo(18.73f, 5.97f)
                lineTo(20.15f, 7.4f)
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
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel,
                strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero,
            ) {
                moveTo(10.6f, 16.6f)
                lineTo(17.65f, 9.55f)
                lineToRelative(-1.4f, -1.4f)
                lineTo(10.6f, 13.8f)
                lineTo(7.75f, 10.95f)
                lineToRelative(-1.4f, 1.4f)
                lineTo(10.6f, 16.6f)
                close()
                moveTo(12f, 22f)
                quadTo(9.93f, 22f, 8.1f, 21.21f)
                quadTo(6.28f, 20.43f, 4.93f, 19.08f)
                quadTo(3.58f, 17.73f, 2.79f, 15.9f)
                reflectiveQuadTo(2f, 12f)
                quadTo(2f, 9.92f, 2.79f, 8.1f)
                quadTo(3.58f, 6.27f, 4.93f, 4.93f)
                quadTo(6.28f, 3.57f, 8.1f, 2.79f)
                quadTo(9.93f, 2f, 12f, 2f)
                reflectiveQuadToRelative(3.9f, 0.79f)
                reflectiveQuadToRelative(3.17f, 2.14f)
                quadToRelative(1.35f, 1.35f, 2.14f, 3.17f)
                quadTo(22f, 9.92f, 22f, 12f)
                reflectiveQuadToRelative(-0.79f, 3.9f)
                reflectiveQuadToRelative(-2.14f, 3.17f)
                quadToRelative(-1.35f, 1.35f, -3.17f, 2.14f)
                reflectiveQuadTo(12f, 22f)
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
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel,
                strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero,
            ) {
                moveTo(10.6f, 16.6f)
                lineTo(17.65f, 9.55f)
                lineToRelative(-1.4f, -1.4f)
                lineTo(10.6f, 13.8f)
                lineTo(7.75f, 10.95f)
                lineToRelative(-1.4f, 1.4f)
                lineTo(10.6f, 16.6f)
                close()
                moveTo(12f, 22f)
                quadTo(9.93f, 22f, 8.1f, 21.21f)
                quadTo(6.28f, 20.43f, 4.93f, 19.08f)
                quadTo(3.58f, 17.73f, 2.79f, 15.9f)
                reflectiveQuadTo(2f, 12f)
                quadTo(2f, 9.92f, 2.79f, 8.1f)
                quadTo(3.58f, 6.27f, 4.93f, 4.93f)
                quadTo(6.28f, 3.57f, 8.1f, 2.79f)
                quadTo(9.93f, 2f, 12f, 2f)
                reflectiveQuadToRelative(3.9f, 0.79f)
                reflectiveQuadToRelative(3.17f, 2.14f)
                quadToRelative(1.35f, 1.35f, 2.14f, 3.17f)
                quadTo(22f, 9.92f, 22f, 12f)
                reflectiveQuadToRelative(-0.79f, 3.9f)
                reflectiveQuadToRelative(-2.14f, 3.17f)
                quadToRelative(-1.35f, 1.35f, -3.17f, 2.14f)
                reflectiveQuadTo(12f, 22f)
                close()
                moveToRelative(0f, -2f)
                quadToRelative(3.35f, 0f, 5.68f, -2.32f)
                reflectiveQuadTo(20f, 12f)
                reflectiveQuadTo(17.68f, 6.32f)
                reflectiveQuadTo(12f, 4f)
                reflectiveQuadTo(6.33f, 6.32f)
                reflectiveQuadTo(4f, 12f)
                reflectiveQuadToRelative(2.33f, 5.68f)
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
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel,
                strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero,
            ) {
                moveTo(6.4f, 19f)
                lineTo(5f, 17.6f)
                lineTo(10.6f, 12f)
                lineTo(5f, 6.4f)
                lineTo(6.4f, 5f)
                lineTo(12f, 10.6f)
                lineTo(17.6f, 5f)
                lineTo(19f, 6.4f)
                lineTo(13.4f, 12f)
                lineTo(19f, 17.6f)
                lineTo(17.6f, 19f)
                lineTo(12f, 13.4f)
                lineTo(6.4f, 19f)
                close()
            }
        }.build().also { _close = it }

    val Share: ImageVector
        get() = _share ?: ImageVector.Builder(
            name = "share",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel,
                strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero,
            ) {
                moveTo(18f, 22f)
                quadToRelative(-1.25f, 0f, -2.12f, -0.88f)
                reflectiveQuadTo(15f, 19f)
                quadToRelative(0f, -0.18f, 0.02f, -0.32f)
                reflectiveQuadToRelative(0.08f, -0.28f)
                lineToRelative(-7.05f, -4.1f)
                quadToRelative(-0.43f, 0.43f, -0.99f, 0.66f)
                reflectiveQuadTo(6f, 15f)
                quadToRelative(-1.25f, 0f, -2.12f, -0.88f)
                reflectiveQuadTo(3f, 12f)
                reflectiveQuadToRelative(0.88f, -2.13f)
                reflectiveQuadTo(6f, 9f)
                quadToRelative(0.57f, 0f, 1.14f, 0.23f)
                reflectiveQuadToRelative(1f, 0.67f)
                lineToRelative(7.05f, -4.1f)
                quadToRelative(-0.05f, -0.12f, -0.08f, -0.27f)
                reflectiveQuadTo(15f, 5f)
                quadToRelative(0f, -1.25f, 0.88f, -2.12f)
                reflectiveQuadTo(18f, 2f)
                reflectiveQuadToRelative(2.13f, 0.88f)
                reflectiveQuadTo(23f, 5f)
                reflectiveQuadToRelative(-0.87f, 2.13f)
                reflectiveQuadTo(18f, 8f)
                quadToRelative(-0.58f, 0f, -1.14f, -0.22f)
                reflectiveQuadToRelative(-0.99f, -0.68f)
                lineToRelative(-7.05f, 4.1f)
                quadToRelative(0.05f, 0.15f, 0.08f, 0.3f)
                reflectiveQuadTo(9f, 12f)
                reflectiveQuadToRelative(-0.03f, 0.3f)
                reflectiveQuadToRelative(-0.07f, 0.3f)
                lineToRelative(7.05f, 4.1f)
                quadToRelative(0.42f, -0.45f, 0.99f, -0.67f)
                reflectiveQuadTo(18f, 16f)
                quadToRelative(1.25f, 0f, 2.13f, 0.88f)
                reflectiveQuadTo(23f, 19f)
                reflectiveQuadToRelative(-0.87f, 2.12f)
                reflectiveQuadTo(18f, 22f)
                close()
                moveTo(18f, 20f)
                quadToRelative(0.43f, 0f, 0.71f, -0.29f)
                reflectiveQuadTo(19f, 19f)
                reflectiveQuadToRelative(-0.29f, -0.71f)
                reflectiveQuadTo(18f, 18f)
                reflectiveQuadToRelative(-0.71f, 0.29f)
                reflectiveQuadTo(17f, 19f)
                reflectiveQuadToRelative(0.29f, 0.71f)
                reflectiveQuadTo(18f, 20f)
                close()
                moveTo(6f, 13f)
                quadToRelative(0.43f, 0f, 0.71f, -0.29f)
                reflectiveQuadTo(7f, 12f)
                reflectiveQuadToRelative(-0.29f, -0.71f)
                reflectiveQuadTo(6f, 11f)
                reflectiveQuadToRelative(-0.71f, 0.29f)
                reflectiveQuadTo(5f, 12f)
                reflectiveQuadToRelative(0.29f, 0.71f)
                reflectiveQuadTo(6f, 13f)
                close()
                moveTo(18f, 6f)
                quadToRelative(0.43f, 0f, 0.71f, -0.29f)
                reflectiveQuadTo(19f, 5f)
                reflectiveQuadToRelative(-0.29f, -0.71f)
                reflectiveQuadTo(18f, 4f)
                reflectiveQuadToRelative(-0.71f, 0.29f)
                reflectiveQuadTo(17f, 5f)
                reflectiveQuadToRelative(0.29f, 0.71f)
                reflectiveQuadTo(18f, 6f)
                close()
            }
        }.build().also { _share = it }

    val IosShare: ImageVector
        get() = _iosShare ?: ImageVector.Builder(
            name = "ios_share",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel,
                strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero,
            ) {
                moveTo(12f, 16f)
                quadToRelative(-0.425f, 0f, -0.712f, -0.288f)
                reflectiveQuadTo(11f, 15f)
                verticalLineTo(6.825f)
                lineToRelative(-2.875f, 2.875f)
                lineTo(6.7f, 8.275f)
                lineTo(12f, 3f)
                lineToRelative(5.3f, 5.275f)
                lineToRelative(-1.425f, 1.425f)
                lineTo(13f, 6.825f)
                verticalLineTo(15f)
                quadToRelative(0f, 0.425f, -0.288f, 0.713f)
                reflectiveQuadTo(12f, 16f)
                close()
                moveTo(5f, 21f)
                quadToRelative(-0.825f, 0f, -1.412f, -0.587f)
                reflectiveQuadTo(3f, 19f)
                verticalLineToRelative(-5.5f)
                quadToRelative(0f, -0.425f, 0.288f, -0.712f)
                reflectiveQuadTo(4f, 12.5f)
                reflectiveQuadToRelative(0.713f, 0.288f)
                reflectiveQuadTo(5f, 13.5f)
                verticalLineTo(19f)
                horizontalLineToRelative(14f)
                verticalLineToRelative(-5.5f)
                quadToRelative(0f, -0.425f, 0.288f, -0.712f)
                reflectiveQuadTo(20f, 12.5f)
                reflectiveQuadToRelative(0.713f, 0.288f)
                reflectiveQuadTo(21f, 13.5f)
                verticalLineTo(19f)
                quadToRelative(0f, 0.825f, -0.587f, 1.413f)
                reflectiveQuadTo(19f, 21f)
                horizontalLineTo(5f)
                close()
            }
        }.build().also { _iosShare = it }

    val FileUpload: ImageVector
        get() = _fileUpload ?: ImageVector.Builder(
            name = "file_upload",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel,
                strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero,
            ) {
                moveTo(12f, 16f)
                quadToRelative(-0.425f, 0f, -0.712f, -0.288f)
                reflectiveQuadTo(11f, 15f)
                verticalLineTo(7.825f)
                lineToRelative(-2.6f, 2.6f)
                lineToRelative(-1.4f, -1.4f)
                lineTo(12f, 4f)
                lineToRelative(5f, 5f)
                lineToRelative(-1.4f, 1.425f)
                lineToRelative(-2.6f, -2.6f)
                verticalLineTo(15f)
                quadToRelative(0f, 0.425f, -0.288f, 0.713f)
                reflectiveQuadTo(12f, 16f)
                close()
                moveTo(5f, 21f)
                quadToRelative(-0.825f, 0f, -1.412f, -0.587f)
                reflectiveQuadTo(3f, 19f)
                verticalLineToRelative(-2f)
                quadToRelative(0f, -0.425f, 0.288f, -0.712f)
                reflectiveQuadTo(4f, 16f)
                reflectiveQuadToRelative(0.713f, 0.288f)
                reflectiveQuadTo(5f, 17f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(14f)
                verticalLineToRelative(-2f)
                quadToRelative(0f, -0.425f, 0.288f, -0.712f)
                reflectiveQuadTo(20f, 16f)
                reflectiveQuadToRelative(0.713f, 0.288f)
                reflectiveQuadTo(21f, 17f)
                verticalLineToRelative(2f)
                quadToRelative(0f, 0.825f, -0.587f, 1.413f)
                reflectiveQuadTo(19f, 21f)
                horizontalLineTo(5f)
                close()
            }
        }.build().also { _fileUpload = it }

    val FileDownload: ImageVector
        get() = _fileDownload ?: ImageVector.Builder(
            name = "file_download",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel,
                strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero,
            ) {
                moveTo(12f, 17f)
                lineTo(7f, 12f)
                lineToRelative(1.425f, -1.425f)
                lineTo(11f, 13.175f)
                verticalLineTo(5f)
                quadToRelative(0f, -0.425f, 0.288f, -0.712f)
                reflectiveQuadTo(12f, 4f)
                reflectiveQuadToRelative(0.713f, 0.288f)
                reflectiveQuadTo(13f, 5f)
                verticalLineToRelative(8.175f)
                lineToRelative(2.575f, -2.575f)
                lineTo(17f, 12f)
                lineTo(12f, 17f)
                close()
                moveTo(5f, 21f)
                quadToRelative(-0.825f, 0f, -1.412f, -0.587f)
                reflectiveQuadTo(3f, 19f)
                verticalLineToRelative(-2f)
                quadToRelative(0f, -0.425f, 0.288f, -0.712f)
                reflectiveQuadTo(4f, 16f)
                reflectiveQuadToRelative(0.713f, 0.288f)
                reflectiveQuadTo(5f, 17f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(14f)
                verticalLineToRelative(-2f)
                quadToRelative(0f, -0.425f, 0.288f, -0.712f)
                reflectiveQuadTo(20f, 16f)
                reflectiveQuadToRelative(0.713f, 0.288f)
                reflectiveQuadTo(21f, 17f)
                verticalLineToRelative(2f)
                quadToRelative(0f, 0.825f, -0.587f, 1.413f)
                reflectiveQuadTo(19f, 21f)
                horizontalLineTo(5f)
                close()
            }
        }.build().also { _fileDownload = it }

    val CloudDownload: ImageVector
        get() = _cloudDownload ?: ImageVector.Builder(
            name = "cloud_download",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel,
                strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero,
            ) {
                moveTo(17f, 20f)
                quadToRelative(-2.075f, 0f, -3.537f, -1.463f)
                quadTo(12f, 17.075f, 12f, 15f)
                quadToRelative(0f, -2.075f, 1.463f, -3.537f)
                quadTo(14.925f, 10f, 17f, 10f)
                quadToRelative(2.075f, 0f, 3.538f, 1.463f)
                quadTo(22f, 12.925f, 22f, 15f)
                quadToRelative(0f, 2.075f, -1.462f, 3.537f)
                quadTo(19.075f, 20f, 17f, 20f)
                close()
                moveToRelative(0f, -2f)
                quadToRelative(0.825f, 0f, 1.413f, -0.587f)
                quadTo(19f, 16.825f, 19f, 16f)
                verticalLineToRelative(-3f)
                horizontalLineToRelative(-4f)
                verticalLineToRelative(3f)
                quadToRelative(0f, 0.825f, 0.588f, 1.413f)
                quadTo(16.175f, 18f, 17f, 18f)
                close()
                moveTo(6.5f, 20f)
                quadTo(4.225f, 20f, 2.613f, 18.438f)
                quadTo(1f, 16.875f, 1f, 14.575f)
                quadToRelative(0f, -1.95f, 1.175f, -3.475f)
                reflectiveQuadTo(5.25f, 9.15f)
                quadToRelative(0.625f, -2.3f, 2.5f, -3.725f)
                reflectiveQuadTo(12f, 4f)
                quadToRelative(2.925f, 0f, 4.962f, 2.038f)
                reflectiveQuadTo(19f, 11f)
                quadToRelative(1.725f, 0.2f, 2.863f, 1.488f)
                reflectiveQuadTo(23f, 15.5f)
                quadToRelative(0f, 0.25f, -0.038f, 0.5f)
                reflectiveQuadToRelative(-0.112f, 0.5f)
                quadToRelative(-0.45f, -1.025f, -1.362f, -1.762f)
                reflectiveQuadTo(19.45f, 13.55f)
                quadTo(18.975f, 11.5f, 17.513f, 10.15f)
                reflectiveQuadTo(14f, 8.575f)
                verticalLineToRelative(-1.3f)
                quadToRelative(0f, -1.25f, -0.875f, -2.125f)
                reflectiveQuadTo(11f, 4.275f)
                reflectiveQuadToRelative(-2.125f, 0.875f)
                reflectiveQuadTo(8f, 7.275f)
                verticalLineToRelative(1.15f)
                quadToRelative(-1.425f, 0.25f, -2.363f, 1.338f)
                reflectiveQuadTo(4.7f, 12.35f)
                quadToRelative(-1.05f, 0.125f, -1.875f, 0.863f)
                reflectiveQuadTo(2f, 15.05f)
                quadToRelative(0f, 1.25f, 0.875f, 2.125f)
                reflectiveQuadTo(5f, 18.05f)
                horizontalLineToRelative(5.25f)
                quadToRelative(0.125f, 0.525f, 0.363f, 1f)
                reflectiveQuadToRelative(0.587f, 0.95f)
                lineTo(6.5f, 20f)
                close()
            }
        }.build().also { _cloudDownload = it }

    val CloudUpload: ImageVector
        get() = _cloudUpload ?: ImageVector.Builder(
            name = "cloud_upload",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel,
                strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero,
            ) {
                moveTo(17f, 20f)
                quadToRelative(-2.075f, 0f, -3.537f, -1.463f)
                quadTo(12f, 17.075f, 12f, 15f)
                quadToRelative(0f, -2.075f, 1.463f, -3.537f)
                quadTo(14.925f, 10f, 17f, 10f)
                quadToRelative(2.075f, 0f, 3.538f, 1.463f)
                quadTo(22f, 12.925f, 22f, 15f)
                quadToRelative(0f, 2.075f, -1.462f, 3.537f)
                quadTo(19.075f, 20f, 17f, 20f)
                close()
                moveToRelative(0f, -2f)
                quadToRelative(0.825f, 0f, 1.413f, -0.587f)
                quadTo(19f, 16.825f, 19f, 16f)
                verticalLineToRelative(-1.5f)
                horizontalLineToRelative(1.5f)
                lineToRelative(-3.5f, -3.5f)
                lineToRelative(-3.5f, 3.5f)
                horizontalLineTo(15f)
                verticalLineTo(16f)
                quadToRelative(0f, 0.825f, 0.588f, 1.413f)
                quadTo(16.175f, 18f, 17f, 18f)
                close()
                moveTo(6.5f, 20f)
                quadTo(4.225f, 20f, 2.613f, 18.438f)
                quadTo(1f, 16.875f, 1f, 14.575f)
                quadToRelative(0f, -1.95f, 1.175f, -3.475f)
                reflectiveQuadTo(5.25f, 9.15f)
                quadToRelative(0.625f, -2.3f, 2.5f, -3.725f)
                reflectiveQuadTo(12f, 4f)
                quadToRelative(2.925f, 0f, 4.962f, 2.038f)
                reflectiveQuadTo(19f, 11f)
                quadToRelative(1.725f, 0.2f, 2.863f, 1.488f)
                reflectiveQuadTo(23f, 15.5f)
                quadToRelative(0f, 0.25f, -0.038f, 0.5f)
                reflectiveQuadToRelative(-0.112f, 0.5f)
                quadToRelative(-0.45f, -1.025f, -1.362f, -1.762f)
                reflectiveQuadTo(19.45f, 13.55f)
                quadTo(18.975f, 11.5f, 17.513f, 10.15f)
                reflectiveQuadTo(14f, 8.575f)
                verticalLineToRelative(-1.3f)
                quadToRelative(0f, -1.25f, -0.875f, -2.125f)
                reflectiveQuadTo(11f, 4.275f)
                reflectiveQuadToRelative(-2.125f, 0.875f)
                reflectiveQuadTo(8f, 7.275f)
                verticalLineToRelative(1.15f)
                quadToRelative(-1.425f, 0.25f, -2.363f, 1.338f)
                reflectiveQuadTo(4.7f, 12.35f)
                quadToRelative(-1.05f, 0.125f, -1.875f, 0.863f)
                reflectiveQuadTo(2f, 15.05f)
                quadToRelative(0f, 1.25f, 0.875f, 2.125f)
                reflectiveQuadTo(5f, 18.05f)
                horizontalLineToRelative(5.25f)
                quadToRelative(0.125f, 0.525f, 0.363f, 1f)
                reflectiveQuadToRelative(0.587f, 0.95f)
                lineTo(6.5f, 20f)
                close()
            }
        }.build().also { _cloudUpload = it }


    val ArrowUploadReady: ImageVector
        get() = _arrowUploadReady ?: ImageVector.Builder(
            name = "arrow_upload_ready",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel,
                strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero,
            ) {
                moveTo(4.25f, 14f)
                quadToRelative(0.15f, 0.57f, 0.36f, 1.1f)
                quadToRelative(0.21f, 0.53f, 0.49f, 1f)
                quadToRelative(0.22f, 0.38f, 0.17f, 0.8f)
                quadTo(5.23f, 17.33f, 4.95f, 17.6f)
                reflectiveQuadTo(4.26f, 17.86f)
                reflectiveQuadTo(3.63f, 17.5f)
                quadTo(3.1f, 16.7f, 2.74f, 15.85f)
                reflectiveQuadTo(2.18f, 14.05f)
                quadToRelative(-0.1f, -0.4f, 0.16f, -0.72f)
                reflectiveQuadTo(3.03f, 13f)
                reflectiveQuadToRelative(0.76f, 0.27f)
                reflectiveQuadTo(4.25f, 14f)
                close()
                moveTo(5.1f, 7.9f)
                quadTo(4.83f, 8.38f, 4.61f, 8.9f)
                reflectiveQuadTo(4.25f, 10f)
                quadTo(4.13f, 10.45f, 3.79f, 10.73f)
                reflectiveQuadTo(3.03f, 11f)
                reflectiveQuadTo(2.34f, 10.7f)
                quadTo(2.08f, 10.4f, 2.18f, 10f)
                quadTo(2.38f, 9.02f, 2.75f, 8.13f)
                quadTo(3.13f, 7.22f, 3.65f, 6.47f)
                quadTo(3.88f, 6.15f, 4.28f, 6.14f)
                reflectiveQuadTo(4.95f, 6.4f)
                quadTo(5.23f, 6.68f, 5.28f, 7.1f)
                reflectiveQuadTo(5.1f, 7.9f)
                close()
                moveTo(7.88f, 18.85f)
                quadToRelative(0.5f, 0.3f, 1.03f, 0.52f)
                reflectiveQuadToRelative(1.08f, 0.38f)
                quadToRelative(0.42f, 0.13f, 0.7f, 0.45f)
                reflectiveQuadToRelative(0.28f, 0.75f)
                reflectiveQuadToRelative(-0.3f, 0.68f)
                reflectiveQuadTo(9.95f, 21.8f)
                quadTo(9.03f, 21.6f, 8.16f, 21.25f)
                reflectiveQuadTo(6.5f, 20.38f)
                quadTo(6.15f, 20.15f, 6.11f, 19.74f)
                reflectiveQuadTo(6.35f, 19.02f)
                quadToRelative(0.3f, -0.3f, 0.72f, -0.35f)
                reflectiveQuadToRelative(0.8f, 0.18f)
                close()
                moveTo(10.03f, 4.25f)
                quadTo(9.48f, 4.4f, 8.96f, 4.61f)
                reflectiveQuadTo(7.95f, 5.13f)
                quadTo(7.55f, 5.35f, 7.11f, 5.31f)
                quadTo(6.68f, 5.27f, 6.38f, 4.97f)
                quadTo(6.08f, 4.67f, 6.1f, 4.27f)
                reflectiveQuadTo(6.48f, 3.65f)
                quadTo(7.3f, 3.13f, 8.19f, 2.76f)
                quadTo(9.08f, 2.4f, 10.03f, 2.2f)
                quadTo(10.4f, 2.13f, 10.7f, 2.38f)
                reflectiveQuadTo(11f, 3.05f)
                reflectiveQuadTo(10.73f, 3.8f)
                quadToRelative(-0.28f, 0.33f, -0.7f, 0.45f)
                close()
                moveToRelative(6.05f, 14.63f)
                quadToRelative(0.38f, -0.23f, 0.81f, -0.19f)
                reflectiveQuadToRelative(0.74f, 0.34f)
                quadToRelative(0.3f, 0.3f, 0.28f, 0.71f)
                reflectiveQuadToRelative(-0.38f, 0.61f)
                quadToRelative(-0.8f, 0.52f, -1.7f, 0.89f)
                reflectiveQuadTo(13.98f, 21.8f)
                quadToRelative(-0.4f, 0.07f, -0.71f, -0.18f)
                reflectiveQuadTo(12.95f, 20.95f)
                reflectiveQuadTo(13.24f, 20.2f)
                reflectiveQuadToRelative(0.71f, -0.45f)
                quadToRelative(0.57f, -0.15f, 1.1f, -0.36f)
                quadToRelative(0.53f, -0.21f, 1.02f, -0.51f)
                close()
                moveTo(13.98f, 4.25f)
                quadTo(13.55f, 4.13f, 13.28f, 3.8f)
                reflectiveQuadTo(13f, 3.05f)
                quadTo(13f, 2.63f, 13.3f, 2.38f)
                reflectiveQuadTo(13.98f, 2.2f)
                quadToRelative(0.95f, 0.2f, 1.85f, 0.56f)
                reflectiveQuadToRelative(1.7f, 0.89f)
                quadToRelative(0.35f, 0.23f, 0.38f, 0.63f)
                reflectiveQuadToRelative(-0.25f, 0.7f)
                quadToRelative(-0.3f, 0.3f, -0.73f, 0.35f)
                reflectiveQuadTo(16.1f, 5.15f)
                quadTo(15.58f, 4.85f, 15.05f, 4.63f)
                reflectiveQuadTo(13.98f, 4.25f)
                close()
                moveToRelative(5.77f, 9.73f)
                quadToRelative(0.13f, -0.43f, 0.46f, -0.7f)
                reflectiveQuadTo(20.98f, 13f)
                reflectiveQuadToRelative(0.69f, 0.32f)
                reflectiveQuadToRelative(0.16f, 0.72f)
                quadTo(21.63f, 15f, 21.24f, 15.88f)
                reflectiveQuadTo(20.35f, 17.5f)
                quadToRelative(-0.23f, 0.32f, -0.63f, 0.35f)
                reflectiveQuadTo(19.05f, 17.6f)
                quadTo(18.78f, 17.33f, 18.73f, 16.89f)
                reflectiveQuadTo(18.9f, 16.08f)
                quadToRelative(0.27f, -0.5f, 0.49f, -1.01f)
                reflectiveQuadToRelative(0.36f, -1.09f)
                close()
                moveTo(18.9f, 7.9f)
                quadTo(18.68f, 7.52f, 18.73f, 7.1f)
                quadTo(18.78f, 6.68f, 19.05f, 6.4f)
                quadTo(19.33f, 6.13f, 19.73f, 6.15f)
                quadToRelative(0.4f, 0.03f, 0.63f, 0.35f)
                quadToRelative(0.55f, 0.8f, 0.93f, 1.67f)
                reflectiveQuadTo(21.85f, 10f)
                quadToRelative(0.07f, 0.4f, -0.19f, 0.7f)
                reflectiveQuadTo(20.98f, 11f)
                reflectiveQuadTo(20.21f, 10.73f)
                reflectiveQuadTo(19.75f, 10f)
                quadTo(19.6f, 9.42f, 19.39f, 8.9f)
                quadTo(19.18f, 8.38f, 18.9f, 7.9f)
                close()
                moveToRelative(-7.64f, 8.81f)
                quadTo(10.98f, 16.43f, 10.98f, 16f)
                verticalLineTo(10.88f)
                lineTo(9.1f, 12.77f)
                quadToRelative(-0.3f, 0.3f, -0.71f, 0.3f)
                reflectiveQuadTo(7.68f, 12.77f)
                quadTo(7.38f, 12.48f, 7.36f, 12.06f)
                reflectiveQuadTo(7.65f, 11.35f)
                lineTo(11.28f, 7.7f)
                quadToRelative(0.28f, -0.27f, 0.7f, -0.27f)
                reflectiveQuadToRelative(0.7f, 0.27f)
                lineToRelative(3.57f, 3.57f)
                quadToRelative(0.3f, 0.3f, 0.31f, 0.73f)
                reflectiveQuadToRelative(-0.29f, 0.72f)
                quadToRelative(-0.3f, 0.3f, -0.73f, 0.3f)
                reflectiveQuadToRelative(-0.72f, -0.3f)
                lineTo(12.98f, 10.88f)
                verticalLineTo(16f)
                quadToRelative(0f, 0.43f, -0.29f, 0.71f)
                reflectiveQuadTo(11.98f, 17f)
                reflectiveQuadTo(11.26f, 16.71f)
                close()
            }
        }.build().also { _arrowUploadReady = it }

    val ArrowUploadCircle: ImageVector
        get() = _arrowUploadCircle ?: ImageVector.Builder(
            name = "arrow_upload_circle",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel,
                strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero,
            ) {
                moveTo(4.25f, 14f)
                quadToRelative(0.15f, 0.57f, 0.36f, 1.1f)
                quadToRelative(0.21f, 0.53f, 0.49f, 1f)
                quadToRelative(0.22f, 0.38f, 0.17f, 0.8f)
                quadTo(5.23f, 17.33f, 4.95f, 17.6f)
                reflectiveQuadTo(4.26f, 17.86f)
                reflectiveQuadTo(3.63f, 17.5f)
                quadTo(3.1f, 16.7f, 2.74f, 15.85f)
                reflectiveQuadTo(2.18f, 14.05f)
                quadToRelative(-0.1f, -0.4f, 0.16f, -0.72f)
                reflectiveQuadTo(3.03f, 13f)
                reflectiveQuadToRelative(0.76f, 0.27f)
                reflectiveQuadTo(4.25f, 14f)
                close()
                moveTo(5.1f, 7.9f)
                quadTo(4.83f, 8.38f, 4.61f, 8.9f)
                reflectiveQuadTo(4.25f, 10f)
                quadTo(4.13f, 10.45f, 3.79f, 10.73f)
                reflectiveQuadTo(3.03f, 11f)
                reflectiveQuadTo(2.34f, 10.7f)
                quadTo(2.08f, 10.4f, 2.18f, 10f)
                quadTo(2.38f, 9.02f, 2.75f, 8.13f)
                quadTo(3.13f, 7.22f, 3.65f, 6.47f)
                quadTo(3.88f, 6.15f, 4.28f, 6.14f)
                reflectiveQuadTo(4.95f, 6.4f)
                quadTo(5.23f, 6.68f, 5.28f, 7.1f)
                reflectiveQuadTo(5.1f, 7.9f)
                close()
                moveTo(7.88f, 18.85f)
                quadToRelative(0.5f, 0.3f, 1.03f, 0.52f)
                reflectiveQuadToRelative(1.08f, 0.38f)
                quadToRelative(0.42f, 0.13f, 0.7f, 0.45f)
                reflectiveQuadToRelative(0.28f, 0.75f)
                reflectiveQuadToRelative(-0.3f, 0.68f)
                reflectiveQuadTo(9.95f, 21.8f)
                quadTo(9.03f, 21.6f, 8.16f, 21.25f)
                reflectiveQuadTo(6.5f, 20.38f)
                quadTo(6.15f, 20.15f, 6.11f, 19.74f)
                reflectiveQuadTo(6.35f, 19.02f)
                quadToRelative(0.3f, -0.3f, 0.72f, -0.35f)
                reflectiveQuadToRelative(0.8f, 0.18f)
                close()
                moveTo(10.03f, 4.25f)
                quadTo(9.48f, 4.4f, 8.96f, 4.61f)
                reflectiveQuadTo(7.95f, 5.13f)
                quadTo(7.55f, 5.35f, 7.11f, 5.31f)
                quadTo(6.68f, 5.27f, 6.38f, 4.97f)
                quadTo(6.08f, 4.67f, 6.1f, 4.27f)
                reflectiveQuadTo(6.48f, 3.65f)
                quadTo(7.3f, 3.13f, 8.19f, 2.76f)
                quadTo(9.08f, 2.4f, 10.03f, 2.2f)
                quadTo(10.4f, 2.13f, 10.7f, 2.38f)
                reflectiveQuadTo(11f, 3.05f)
                reflectiveQuadTo(10.73f, 3.8f)
                quadToRelative(-0.28f, 0.33f, -0.7f, 0.45f)
                close()
                moveToRelative(6.05f, 14.63f)
                quadToRelative(0.38f, -0.23f, 0.81f, -0.19f)
                reflectiveQuadToRelative(0.74f, 0.34f)
                quadToRelative(0.3f, 0.3f, 0.28f, 0.71f)
                reflectiveQuadToRelative(-0.38f, 0.61f)
                quadToRelative(-0.8f, 0.52f, -1.7f, 0.89f)
                reflectiveQuadTo(13.98f, 21.8f)
                quadToRelative(-0.4f, 0.07f, -0.71f, -0.18f)
                reflectiveQuadTo(12.95f, 20.95f)
                reflectiveQuadTo(13.24f, 20.2f)
                reflectiveQuadToRelative(0.71f, -0.45f)
                quadToRelative(0.57f, -0.15f, 1.1f, -0.36f)
                quadToRelative(0.53f, -0.21f, 1.02f, -0.51f)
                close()
                moveTo(13.98f, 4.25f)
                quadTo(13.55f, 4.13f, 13.28f, 3.8f)
                reflectiveQuadTo(13f, 3.05f)
                quadTo(13f, 2.63f, 13.3f, 2.38f)
                reflectiveQuadTo(13.98f, 2.2f)
                quadToRelative(0.95f, 0.2f, 1.85f, 0.56f)
                reflectiveQuadToRelative(1.7f, 0.89f)
                quadToRelative(0.35f, 0.23f, 0.38f, 0.63f)
                reflectiveQuadToRelative(-0.25f, 0.7f)
                quadToRelative(-0.3f, 0.3f, -0.73f, 0.35f)
                reflectiveQuadTo(16.1f, 5.15f)
                quadTo(15.58f, 4.85f, 15.05f, 4.63f)
                reflectiveQuadTo(13.98f, 4.25f)
                close()
                moveToRelative(5.77f, 9.73f)
                quadToRelative(0.13f, -0.43f, 0.46f, -0.7f)
                reflectiveQuadTo(20.98f, 13f)
                reflectiveQuadToRelative(0.69f, 0.32f)
                reflectiveQuadToRelative(0.16f, 0.72f)
                quadTo(21.63f, 15f, 21.24f, 15.88f)
                reflectiveQuadTo(20.35f, 17.5f)
                quadToRelative(-0.23f, 0.32f, -0.63f, 0.35f)
                reflectiveQuadTo(19.05f, 17.6f)
                quadTo(18.78f, 17.33f, 18.73f, 16.89f)
                reflectiveQuadTo(18.9f, 16.08f)
                quadToRelative(0.27f, -0.5f, 0.49f, -1.01f)
                reflectiveQuadToRelative(0.36f, -1.09f)
                close()
                moveTo(18.9f, 7.9f)
                quadTo(18.68f, 7.52f, 18.73f, 7.1f)
                quadTo(18.78f, 6.68f, 19.05f, 6.4f)
                quadTo(19.33f, 6.13f, 19.73f, 6.15f)
                quadToRelative(0.4f, 0.03f, 0.63f, 0.35f)
                quadToRelative(0.55f, 0.8f, 0.93f, 1.67f)
                reflectiveQuadTo(21.85f, 10f)
                quadToRelative(0.07f, 0.4f, -0.19f, 0.7f)
                reflectiveQuadTo(20.98f, 11f)
                reflectiveQuadTo(20.21f, 10.73f)
                reflectiveQuadTo(19.75f, 10f)
                quadTo(19.6f, 9.42f, 19.39f, 8.9f)
                quadTo(19.18f, 8.38f, 18.9f, 7.9f)
                close()
            }
        }.build().also { _arrowUploadCircle = it }

    val ArrowUploadArrow: ImageVector
        get() = _arrowUploadArrow ?: ImageVector.Builder(
            name = "arrow_upload_arrow",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(11.3f, 17f)
                curveTo(11.3f, 17.4f, 11.6f, 17.7f, 12f, 17.7f)
                curveTo(12.4f, 17.7f, 12.7f, 17.4f, 12.7f, 17f)
                verticalLineTo(10.4f)
                lineTo(14.9f, 12.6f)
                curveTo(15.2f, 12.9f, 15.7f, 12.9f, 16f, 12.6f)
                curveTo(16.3f, 12.3f, 16.3f, 11.8f, 16f, 11.5f)
                lineTo(12.7f, 8.2f)
                curveTo(12.3f, 7.8f, 11.7f, 7.8f, 11.3f, 8.2f)
                lineTo(8f, 11.5f)
                curveTo(7.7f, 11.8f, 7.7f, 12.3f, 8f, 12.6f)
                curveTo(8.3f, 12.9f, 8.8f, 12.9f, 9.1f, 12.6f)
                lineTo(11.3f, 10.4f)
                verticalLineTo(17f)
                close()
            }
        }.build().also { _arrowUploadArrow = it }

    val ArrowDownloadArrow: ImageVector
        get() = _arrowDownloadArrow ?: ImageVector.Builder(
            name = "arrow_download_arrow",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12.7f, 7f)
                curveTo(12.7f, 6.6f, 12.4f, 6.3f, 12f, 6.3f)
                curveTo(11.6f, 6.3f, 11.3f, 6.6f, 11.3f, 7f)
                verticalLineTo(13.6f)
                lineTo(9.1f, 11.4f)
                curveTo(8.8f, 11.1f, 8.3f, 11.1f, 8f, 11.4f)
                curveTo(7.7f, 11.7f, 7.7f, 12.2f, 8f, 12.5f)
                lineTo(11.3f, 15.8f)
                curveTo(11.7f, 16.2f, 12.3f, 16.2f, 12.7f, 15.8f)
                lineTo(16f, 12.5f)
                curveTo(16.3f, 12.2f, 16.3f, 11.7f, 16f, 11.4f)
                curveTo(15.7f, 11.1f, 15.2f, 11.1f, 14.9f, 11.4f)
                lineTo(12.7f, 13.6f)
                verticalLineTo(7f)
                close()
            }
        }.build().also { _arrowDownloadArrow = it }

    val Sort: ImageVector
        get() = _sort ?: ImageVector.Builder(
            name = "sort",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel,
                strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero,
            ) {
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
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel,
                strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero,
            ) {
                moveTo(12f, 15.38f)
                lineTo(6.3f, 9.68f)
                lineToRelative(1.42f, -1.42f)
                lineTo(12f, 12.55f)
                lineToRelative(4.28f, -4.3f)
                lineToRelative(1.42f, 1.43f)
                lineTo(12f, 15.38f)
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
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel,
                strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero,
            ) {
                moveTo(5f, 21f)
                quadTo(4.18f, 21f, 3.59f, 20.41f)
                reflectiveQuadTo(3f, 19f)
                verticalLineTo(5f)
                quadTo(3f, 4.17f, 3.59f, 3.59f)
                reflectiveQuadTo(5f, 3f)
                horizontalLineTo(19f)
                quadToRelative(0.83f, 0f, 1.41f, 0.59f)
                reflectiveQuadTo(21f, 5f)
                verticalLineTo(19f)
                quadToRelative(0f, 0.82f, -0.59f, 1.41f)
                reflectiveQuadTo(19f, 21f)
                horizontalLineTo(5f)
                close()
                moveTo(5f, 19f)
                horizontalLineTo(19f)
                verticalLineTo(5f)
                horizontalLineTo(5f)
                verticalLineTo(19f)
                close()
                moveTo(6f, 17f)
                horizontalLineTo(18f)
                lineTo(14.25f, 12f)
                lineToRelative(-3f, 4f)
                lineTo(9f, 13f)
                lineTo(6f, 17f)
                close()
                moveTo(5f, 19f)
                verticalLineTo(5f)
                verticalLineTo(19f)
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
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel,
                strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero,
            ) {
                moveTo(4f, 20f)
                quadTo(3.18f, 20f, 2.59f, 19.41f)
                reflectiveQuadTo(2f, 18f)
                verticalLineTo(6f)
                quadTo(2f, 5.18f, 2.59f, 4.59f)
                reflectiveQuadTo(4f, 4f)
                horizontalLineTo(16f)
                quadToRelative(0.82f, 0f, 1.41f, 0.59f)
                quadTo(18f, 5.18f, 18f, 6f)
                verticalLineToRelative(4.5f)
                lineToRelative(4f, -4f)
                verticalLineToRelative(11f)
                lineToRelative(-4f, -4f)
                verticalLineTo(18f)
                quadToRelative(0f, 0.82f, -0.59f, 1.41f)
                reflectiveQuadTo(16f, 20f)
                horizontalLineTo(4f)
                close()
                moveTo(4f, 18f)
                horizontalLineTo(16f)
                verticalLineTo(6f)
                horizontalLineTo(4f)
                verticalLineTo(18f)
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
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel,
                strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero,
            ) {
                moveTo(7f, 17f)
                horizontalLineToRelative(7f)
                verticalLineTo(15f)
                horizontalLineTo(7f)
                verticalLineToRelative(2f)
                close()
                moveTo(7f, 13f)
                horizontalLineTo(17f)
                verticalLineTo(11f)
                horizontalLineTo(7f)
                verticalLineToRelative(2f)
                close()
                moveTo(7f, 9f)
                horizontalLineTo(17f)
                verticalLineTo(7f)
                horizontalLineTo(7f)
                verticalLineTo(9f)
                close()
                moveTo(5f, 21f)
                quadTo(4.18f, 21f, 3.59f, 20.41f)
                reflectiveQuadTo(3f, 19f)
                verticalLineTo(5f)
                quadTo(3f, 4.17f, 3.59f, 3.59f)
                reflectiveQuadTo(5f, 3f)
                horizontalLineTo(19f)
                quadToRelative(0.83f, 0f, 1.41f, 0.59f)
                reflectiveQuadTo(21f, 5f)
                verticalLineTo(19f)
                quadToRelative(0f, 0.82f, -0.59f, 1.41f)
                reflectiveQuadTo(19f, 21f)
                horizontalLineTo(5f)
                close()
                moveTo(5f, 19f)
                horizontalLineTo(19f)
                verticalLineTo(5f)
                horizontalLineTo(5f)
                verticalLineTo(19f)
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
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel,
                strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero,
            ) {
                moveTo(15.5f, 19.93f)
                lineTo(11.25f, 15.68f)
                lineToRelative(1.4f, -1.4f)
                lineToRelative(2.85f, 2.85f)
                lineToRelative(5.65f, -5.65f)
                lineToRelative(1.4f, 1.4f)
                lineTo(15.5f, 19.93f)
                close()
                moveTo(21f, 10f)
                horizontalLineTo(19f)
                verticalLineTo(5f)
                horizontalLineTo(17f)
                verticalLineTo(8f)
                horizontalLineTo(7f)
                verticalLineTo(5f)
                horizontalLineTo(5f)
                verticalLineTo(19f)
                horizontalLineToRelative(6f)
                verticalLineToRelative(2f)
                horizontalLineTo(5f)
                quadTo(4.18f, 21f, 3.59f, 20.41f)
                reflectiveQuadTo(3f, 19f)
                verticalLineTo(5f)
                quadTo(3f, 4.17f, 3.59f, 3.59f)
                reflectiveQuadTo(5f, 3f)
                horizontalLineTo(9.18f)
                quadTo(9.45f, 2.13f, 10.25f, 1.56f)
                reflectiveQuadTo(12f, 1f)
                quadToRelative(1f, 0f, 1.79f, 0.56f)
                reflectiveQuadTo(14.85f, 3f)
                horizontalLineTo(19f)
                quadToRelative(0.83f, 0f, 1.41f, 0.59f)
                reflectiveQuadTo(21f, 5f)
                verticalLineToRelative(5f)
                close()
                moveTo(12.71f, 4.71f)
                quadTo(13f, 4.42f, 13f, 4f)
                quadTo(13f, 3.57f, 12.71f, 3.29f)
                reflectiveQuadTo(12f, 3f)
                reflectiveQuadTo(11.29f, 3.29f)
                reflectiveQuadTo(11f, 4f)
                quadToRelative(0f, 0.42f, 0.29f, 0.71f)
                reflectiveQuadTo(12f, 5f)
                reflectiveQuadTo(12.71f, 4.71f)
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
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel,
                strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero,
            ) {
                moveTo(12f, 21f)
                quadTo(8.55f, 21f, 5.99f, 18.71f)
                quadTo(3.43f, 16.43f, 3.05f, 13f)
                horizontalLineTo(5.1f)
                quadToRelative(0.35f, 2.6f, 2.31f, 4.3f)
                reflectiveQuadTo(12f, 19f)
                quadToRelative(2.93f, 0f, 4.96f, -2.04f)
                quadTo(19f, 14.93f, 19f, 12f)
                quadTo(19f, 9.07f, 16.96f, 7.04f)
                reflectiveQuadTo(12f, 5f)
                quadTo(10.28f, 5f, 8.78f, 5.8f)
                reflectiveQuadTo(6.25f, 8f)
                horizontalLineTo(9f)
                verticalLineToRelative(2f)
                horizontalLineTo(3f)
                verticalLineTo(4f)
                horizontalLineTo(5f)
                verticalLineTo(6.35f)
                quadTo(6.28f, 4.75f, 8.11f, 3.88f)
                reflectiveQuadTo(12f, 3f)
                quadToRelative(1.88f, 0f, 3.51f, 0.71f)
                reflectiveQuadToRelative(2.85f, 1.93f)
                reflectiveQuadToRelative(1.93f, 2.85f)
                reflectiveQuadTo(21f, 12f)
                reflectiveQuadToRelative(-0.71f, 3.51f)
                reflectiveQuadToRelative(-1.93f, 2.85f)
                reflectiveQuadToRelative(-2.85f, 1.93f)
                reflectiveQuadTo(12f, 21f)
                close()
                moveToRelative(2.8f, -4.8f)
                lineTo(11f, 12.4f)
                verticalLineTo(7f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(4.6f)
                lineToRelative(3.2f, 3.2f)
                lineToRelative(-1.4f, 1.4f)
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
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel,
                strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero,
            ) {
                moveTo(4f, 20f)
                quadTo(3.18f, 20f, 2.59f, 19.41f)
                reflectiveQuadTo(2f, 18f)
                verticalLineTo(6f)
                quadTo(2f, 5.18f, 2.59f, 4.59f)
                reflectiveQuadTo(4f, 4f)
                horizontalLineToRelative(6f)
                lineToRelative(2f, 2f)
                horizontalLineToRelative(8f)
                quadToRelative(0.83f, 0f, 1.41f, 0.59f)
                quadTo(22f, 7.18f, 22f, 8f)
                verticalLineTo(18f)
                quadToRelative(0f, 0.82f, -0.59f, 1.41f)
                reflectiveQuadTo(20f, 20f)
                horizontalLineTo(4f)
                close()
                moveTo(4f, 18f)
                horizontalLineTo(20f)
                verticalLineTo(8f)
                horizontalLineTo(11.18f)
                lineToRelative(-2f, -2f)
                horizontalLineTo(4f)
                verticalLineTo(18f)
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
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel,
                strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero,
            ) {
                moveTo(7f, 21f)
                quadTo(6.18f, 21f, 5.59f, 20.41f)
                reflectiveQuadTo(5f, 19f)
                verticalLineTo(6f)
                horizontalLineTo(4f)
                verticalLineTo(4f)
                horizontalLineTo(9f)
                verticalLineTo(3f)
                horizontalLineToRelative(6f)
                verticalLineTo(4f)
                horizontalLineToRelative(5f)
                verticalLineTo(6f)
                horizontalLineTo(19f)
                verticalLineTo(19f)
                quadToRelative(0f, 0.82f, -0.59f, 1.41f)
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
                horizontalLineToRelative(2f)
                verticalLineTo(8f)
                horizontalLineTo(9f)
                verticalLineToRelative(9f)
                close()
                moveToRelative(4f, 0f)
                horizontalLineToRelative(2f)
                verticalLineTo(8f)
                horizontalLineTo(13f)
                verticalLineToRelative(9f)
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
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel,
                strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero,
            ) {
                moveTo(3f, 11f)
                verticalLineTo(3f)
                horizontalLineToRelative(8f)
                verticalLineToRelative(8f)
                horizontalLineTo(3f)
                close()
                moveTo(3f, 21f)
                verticalLineTo(13f)
                horizontalLineToRelative(8f)
                verticalLineToRelative(8f)
                horizontalLineTo(3f)
                close()
                moveTo(13f, 11f)
                verticalLineTo(3f)
                horizontalLineToRelative(8f)
                verticalLineToRelative(8f)
                horizontalLineTo(13f)
                close()
                moveToRelative(0f, 10f)
                verticalLineTo(13f)
                horizontalLineToRelative(8f)
                verticalLineToRelative(8f)
                horizontalLineTo(13f)
                close()
                moveTo(5f, 9f)
                horizontalLineTo(9f)
                verticalLineTo(5f)
                horizontalLineTo(5f)
                verticalLineTo(9f)
                close()
                moveTo(15f, 9f)
                horizontalLineToRelative(4f)
                verticalLineTo(5f)
                horizontalLineTo(15f)
                verticalLineTo(9f)
                close()
                moveToRelative(0f, 10f)
                horizontalLineToRelative(4f)
                verticalLineTo(15f)
                horizontalLineTo(15f)
                verticalLineToRelative(4f)
                close()
                moveTo(5f, 19f)
                horizontalLineTo(9f)
                verticalLineTo(15f)
                horizontalLineTo(5f)
                verticalLineToRelative(4f)
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
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel,
                strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero,
            ) {
                moveTo(9f, 18f)
                horizontalLineTo(20f)
                verticalLineTo(15.33f)
                horizontalLineTo(9f)
                verticalLineTo(18f)
                close()
                moveTo(4f, 8.67f)
                horizontalLineTo(7f)
                verticalLineTo(6f)
                horizontalLineTo(4f)
                verticalLineTo(8.67f)
                close()
                moveToRelative(0f, 4.68f)
                horizontalLineTo(7f)
                verticalLineTo(10.68f)
                horizontalLineTo(4f)
                verticalLineToRelative(2.68f)
                close()
                moveTo(4f, 18f)
                horizontalLineTo(7f)
                verticalLineTo(15.33f)
                horizontalLineTo(4f)
                verticalLineTo(18f)
                close()
                moveTo(9f, 13.35f)
                horizontalLineTo(20f)
                verticalLineTo(10.68f)
                horizontalLineTo(9f)
                verticalLineToRelative(2.68f)
                close()
                moveTo(9f, 8.67f)
                horizontalLineTo(20f)
                verticalLineTo(6f)
                horizontalLineTo(9f)
                verticalLineTo(8.67f)
                close()
                moveTo(4f, 20f)
                quadTo(3.18f, 20f, 2.59f, 19.41f)
                reflectiveQuadTo(2f, 18f)
                verticalLineTo(6f)
                quadTo(2f, 5.18f, 2.59f, 4.59f)
                reflectiveQuadTo(4f, 4f)
                horizontalLineTo(20f)
                quadToRelative(0.83f, 0f, 1.41f, 0.59f)
                quadTo(22f, 5.18f, 22f, 6f)
                verticalLineTo(18f)
                quadToRelative(0f, 0.82f, -0.59f, 1.41f)
                reflectiveQuadTo(20f, 20f)
                horizontalLineTo(4f)
                close()
            }
        }.build().also { _viewList = it }

    val Send: ImageVector
        get() = _send ?: ImageVector.Builder(
            name = "send",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel,
                strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero,
            ) {
                moveTo(3f, 20f)
                verticalLineTo(4f)
                lineToRelative(19f, 8f)
                lineTo(3f, 20f)
                close()
                moveTo(5f, 17f)
                lineTo(16.85f, 12f)
                lineTo(5f, 7f)
                verticalLineToRelative(3.5f)
                lineTo(11f, 12f)
                lineTo(5f, 13.5f)
                verticalLineTo(17f)
                close()
            }
        }.build().also { _send = it }

    val Cloud: ImageVector
        get() = _cloud ?: ImageVector.Builder(
            name = "cloud",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel,
                strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero,
            ) {
                moveTo(6.5f, 20f)
                quadTo(4.23f, 20f, 2.61f, 18.43f)
                reflectiveQuadTo(1f, 14.58f)
                quadTo(1f, 12.63f, 2.18f, 11.1f)
                reflectiveQuadTo(5.25f, 9.15f)
                quadTo(5.88f, 6.85f, 7.75f, 5.43f)
                reflectiveQuadTo(12f, 4f)
                quadToRelative(2.93f, 0f, 4.96f, 2.04f)
                reflectiveQuadTo(19f, 11f)
                quadToRelative(1.73f, 0.2f, 2.86f, 1.49f)
                reflectiveQuadTo(23f, 15.5f)
                quadToRelative(0f, 1.88f, -1.31f, 3.19f)
                reflectiveQuadTo(18.5f, 20f)
                horizontalLineTo(6.5f)
                close()
                moveToRelative(0f, -2f)
                horizontalLineToRelative(12f)
                quadToRelative(1.05f, 0f, 1.78f, -0.73f)
                reflectiveQuadTo(21f, 15.5f)
                reflectiveQuadTo(20.28f, 13.73f)
                reflectiveQuadTo(18.5f, 13f)
                horizontalLineTo(17f)
                verticalLineTo(11f)
                quadTo(17f, 8.92f, 15.54f, 7.46f)
                reflectiveQuadTo(12f, 6f)
                quadTo(9.93f, 6f, 8.46f, 7.46f)
                reflectiveQuadTo(7f, 11f)
                horizontalLineTo(6.5f)
                quadTo(5.05f, 11f, 4.03f, 12.02f)
                reflectiveQuadTo(3f, 14.5f)
                reflectiveQuadToRelative(1.03f, 2.48f)
                reflectiveQuadTo(6.5f, 18f)
                close()
            }
        }.build().also { _cloud = it }

    val Search: ImageVector
        get() = _search ?: ImageVector.Builder(
            name = "search",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel,
                strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero,
            ) {
                moveTo(19.6f, 21f)
                lineTo(13.3f, 14.7f)
                quadToRelative(-0.75f, 0.6f, -1.72f, 0.95f)
                reflectiveQuadTo(9.5f, 16f)
                quadTo(6.78f, 16f, 4.89f, 14.11f)
                quadTo(3f, 12.23f, 3f, 9.5f)
                quadTo(3f, 6.77f, 4.89f, 4.89f)
                reflectiveQuadTo(9.5f, 3f)
                reflectiveQuadToRelative(4.61f, 1.89f)
                reflectiveQuadTo(16f, 9.5f)
                quadToRelative(0f, 1.1f, -0.35f, 2.07f)
                reflectiveQuadTo(14.7f, 13.3f)
                lineTo(21f, 19.6f)
                lineTo(19.6f, 21f)
                close()
                moveTo(9.5f, 14f)
                quadToRelative(1.88f, 0f, 3.19f, -1.31f)
                reflectiveQuadTo(14f, 9.5f)
                reflectiveQuadTo(12.69f, 6.31f)
                reflectiveQuadTo(9.5f, 5f)
                reflectiveQuadTo(6.31f, 6.31f)
                reflectiveQuadTo(5f, 9.5f)
                reflectiveQuadToRelative(1.31f, 3.19f)
                reflectiveQuadTo(9.5f, 14f)
                close()
            }
        }.build().also { _search = it }

    val Computer: ImageVector
        get() = _computer ?: ImageVector.Builder(
            name = "computer",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel,
                strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero,
            ) {
                moveTo(8f, 21f)
                verticalLineTo(19f)
                horizontalLineToRelative(2f)
                verticalLineTo(17f)
                horizontalLineTo(4f)
                quadTo(3.18f, 17f, 2.59f, 16.41f)
                reflectiveQuadTo(2f, 15f)
                verticalLineTo(5f)
                quadTo(2f, 4.17f, 2.59f, 3.59f)
                reflectiveQuadTo(4f, 3f)
                horizontalLineTo(20f)
                quadToRelative(0.83f, 0f, 1.41f, 0.59f)
                reflectiveQuadTo(22f, 5f)
                verticalLineTo(15f)
                quadToRelative(0f, 0.82f, -0.59f, 1.41f)
                reflectiveQuadTo(20f, 17f)
                horizontalLineTo(14f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(2f)
                horizontalLineTo(8f)
                close()
                moveTo(4f, 15f)
                horizontalLineTo(20f)
                verticalLineTo(5f)
                horizontalLineTo(4f)
                verticalLineTo(15f)
                close()
            }
        }.build().also { _computer = it }

    val Smartphone: ImageVector
        get() = _smartphone ?: ImageVector.Builder(
            name = "smartphone",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel,
                strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero,
            ) {
                moveTo(7f, 23f)
                quadTo(6.18f, 23f, 5.59f, 22.41f)
                reflectiveQuadTo(5f, 21f)
                verticalLineTo(3f)
                quadTo(5f, 2.17f, 5.59f, 1.59f)
                reflectiveQuadTo(7f, 1f)
                horizontalLineTo(17f)
                quadToRelative(0.82f, 0f, 1.41f, 0.59f)
                reflectiveQuadTo(19f, 3f)
                verticalLineTo(6.1f)
                quadToRelative(0.45f, 0.18f, 0.73f, 0.55f)
                reflectiveQuadTo(20f, 7.5f)
                verticalLineToRelative(2f)
                quadToRelative(0f, 0.47f, -0.27f, 0.85f)
                reflectiveQuadTo(19f, 10.9f)
                verticalLineTo(21f)
                quadToRelative(0f, 0.82f, -0.59f, 1.41f)
                reflectiveQuadTo(17f, 23f)
                horizontalLineTo(7f)
                close()
                moveTo(7f, 21f)
                horizontalLineTo(17f)
                verticalLineTo(3f)
                horizontalLineTo(7f)
                verticalLineTo(21f)
                close()
                moveTo(12.71f, 5.71f)
                quadTo(13f, 5.43f, 13f, 5f)
                reflectiveQuadTo(12.71f, 4.29f)
                reflectiveQuadTo(12f, 4f)
                reflectiveQuadTo(11.29f, 4.29f)
                reflectiveQuadTo(11f, 5f)
                reflectiveQuadToRelative(0.29f, 0.71f)
                reflectiveQuadTo(12f, 6f)
                reflectiveQuadTo(12.71f, 5.71f)
                close()
            }
        }.build().also { _smartphone = it }

    val Tune: ImageVector
        get() = _tune ?: ImageVector.Builder(
            name = "tune",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel,
                strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero,
            ) {
                moveTo(11f, 21f)
                verticalLineTo(15f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(8f)
                verticalLineToRelative(2f)
                horizontalLineTo(13f)
                verticalLineToRelative(2f)
                horizontalLineTo(11f)
                close()
                moveTo(3f, 19f)
                verticalLineTo(17f)
                horizontalLineTo(9f)
                verticalLineToRelative(2f)
                horizontalLineTo(3f)
                close()
                moveTo(7f, 15f)
                verticalLineTo(13f)
                horizontalLineTo(3f)
                verticalLineTo(11f)
                horizontalLineTo(7f)
                verticalLineTo(9f)
                horizontalLineTo(9f)
                verticalLineToRelative(6f)
                horizontalLineTo(7f)
                close()
                moveToRelative(4f, -2f)
                verticalLineTo(11f)
                horizontalLineTo(21f)
                verticalLineToRelative(2f)
                horizontalLineTo(11f)
                close()
                moveTo(15f, 9f)
                verticalLineTo(3f)
                horizontalLineToRelative(2f)
                verticalLineTo(5f)
                horizontalLineToRelative(4f)
                verticalLineTo(7f)
                horizontalLineTo(17f)
                verticalLineTo(9f)
                horizontalLineTo(15f)
                close()
                moveTo(3f, 7f)
                verticalLineTo(5f)
                horizontalLineTo(13f)
                verticalLineTo(7f)
                horizontalLineTo(3f)
                close()
            }
        }.build().also { _tune = it }

    val AccountCircle: ImageVector
        get() = _accountCircle ?: ImageVector.Builder(
            name = "account_circle",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel,
                strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero,
            ) {
                moveTo(5.85f, 17.1f)
                quadTo(7.13f, 16.13f, 8.7f, 15.56f)
                reflectiveQuadTo(12f, 15f)
                reflectiveQuadToRelative(3.3f, 0.56f)
                reflectiveQuadToRelative(2.85f, 1.54f)
                quadToRelative(0.88f, -1.03f, 1.36f, -2.33f)
                reflectiveQuadTo(20f, 12f)
                quadTo(20f, 8.67f, 17.66f, 6.34f)
                reflectiveQuadTo(12f, 4f)
                quadTo(8.68f, 4f, 6.34f, 6.34f)
                reflectiveQuadTo(4f, 12f)
                quadToRelative(0f, 1.47f, 0.49f, 2.78f)
                quadToRelative(0.49f, 1.3f, 1.36f, 2.33f)
                close()
                moveTo(9.51f, 11.99f)
                quadTo(8.5f, 10.98f, 8.5f, 9.5f)
                quadTo(8.5f, 8.02f, 9.51f, 7.01f)
                reflectiveQuadTo(12f, 6f)
                reflectiveQuadToRelative(2.49f, 1.01f)
                reflectiveQuadTo(15.5f, 9.5f)
                reflectiveQuadToRelative(-1.01f, 2.49f)
                reflectiveQuadTo(12f, 13f)
                quadTo(10.53f, 13f, 9.51f, 11.99f)
                close()
                moveTo(12f, 22f)
                quadTo(9.93f, 22f, 8.1f, 21.21f)
                quadTo(6.28f, 20.43f, 4.93f, 19.08f)
                quadTo(3.58f, 17.73f, 2.79f, 15.9f)
                reflectiveQuadTo(2f, 12f)
                quadTo(2f, 9.92f, 2.79f, 8.1f)
                quadTo(3.58f, 6.27f, 4.93f, 4.93f)
                quadTo(6.28f, 3.57f, 8.1f, 2.79f)
                quadTo(9.93f, 2f, 12f, 2f)
                reflectiveQuadToRelative(3.9f, 0.79f)
                reflectiveQuadToRelative(3.17f, 2.14f)
                quadToRelative(1.35f, 1.35f, 2.14f, 3.17f)
                quadTo(22f, 9.92f, 22f, 12f)
                reflectiveQuadToRelative(-0.79f, 3.9f)
                reflectiveQuadToRelative(-2.14f, 3.17f)
                quadToRelative(-1.35f, 1.35f, -3.17f, 2.14f)
                reflectiveQuadTo(12f, 22f)
                close()
                moveToRelative(2.5f, -2.39f)
                quadToRelative(1.18f, -0.39f, 2.15f, -1.11f)
                quadTo(15.68f, 17.77f, 14.5f, 17.39f)
                reflectiveQuadTo(12f, 17f)
                reflectiveQuadTo(9.5f, 17.39f)
                quadTo(8.33f, 17.77f, 7.35f, 18.5f)
                quadToRelative(0.98f, 0.73f, 2.15f, 1.11f)
                reflectiveQuadTo(12f, 20f)
                reflectiveQuadToRelative(2.5f, -0.39f)
                close()
            }
        }.build().also { _accountCircle = it }

    val Devices: ImageVector
        get() = _devices ?: ImageVector.Builder(
            name = "devices",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel,
                strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero,
            ) {
                moveTo(2f, 20f)
                verticalLineTo(18f)
                horizontalLineTo(12f)
                verticalLineToRelative(2f)
                horizontalLineTo(2f)
                close()
                moveTo(5f, 17f)
                quadTo(4.18f, 17f, 3.59f, 16.41f)
                reflectiveQuadTo(3f, 15f)
                verticalLineTo(6f)
                quadTo(3f, 5.18f, 3.59f, 4.59f)
                reflectiveQuadTo(5f, 4f)
                horizontalLineTo(19f)
                quadToRelative(0.83f, 0f, 1.41f, 0.59f)
                quadTo(21f, 5.18f, 21f, 6f)
                horizontalLineTo(5f)
                verticalLineToRelative(9f)
                horizontalLineToRelative(7f)
                verticalLineToRelative(2f)
                horizontalLineTo(5f)
                close()
                moveToRelative(15f, 1f)
                verticalLineTo(10f)
                horizontalLineTo(16f)
                verticalLineToRelative(8f)
                horizontalLineToRelative(4f)
                close()
                moveToRelative(-4.5f, 2f)
                quadToRelative(-0.63f, 0f, -1.06f, -0.44f)
                reflectiveQuadTo(14f, 18.5f)
                verticalLineToRelative(-9f)
                quadTo(14f, 8.88f, 14.44f, 8.44f)
                reflectiveQuadTo(15.5f, 8f)
                horizontalLineToRelative(5f)
                quadToRelative(0.63f, 0f, 1.06f, 0.44f)
                reflectiveQuadTo(22f, 9.5f)
                verticalLineToRelative(9f)
                quadToRelative(0f, 0.63f, -0.44f, 1.06f)
                reflectiveQuadTo(20.5f, 20f)
                horizontalLineToRelative(-5f)
                close()
                moveTo(18f, 12.5f)
                quadToRelative(0.32f, 0f, 0.54f, -0.23f)
                reflectiveQuadToRelative(0.21f, -0.52f)
                quadToRelative(0f, -0.33f, -0.21f, -0.54f)
                reflectiveQuadTo(18f, 11f)
                quadToRelative(-0.3f, 0f, -0.52f, 0.21f)
                quadToRelative(-0.23f, 0.21f, -0.23f, 0.54f)
                quadToRelative(0f, 0.3f, 0.23f, 0.52f)
                reflectiveQuadTo(18f, 12.5f)
                close()
            }
        }.build().also { _devices = it }

    val QrCodeScanner: ImageVector
        get() = _qrCodeScanner ?: ImageVector.Builder(
            name = "qr_code_scanner",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel,
                strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero,
            ) {
                moveTo(2f, 7f)
                verticalLineTo(2f)
                horizontalLineTo(7f)
                verticalLineTo(4f)
                horizontalLineTo(4f)
                verticalLineTo(7f)
                horizontalLineTo(2f)
                close()
                moveTo(2f, 22f)
                verticalLineTo(17f)
                horizontalLineTo(4f)
                verticalLineToRelative(3f)
                horizontalLineTo(7f)
                verticalLineToRelative(2f)
                horizontalLineTo(2f)
                close()
                moveToRelative(15f, 0f)
                verticalLineTo(20f)
                horizontalLineToRelative(3f)
                verticalLineTo(17f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(5f)
                horizontalLineTo(17f)
                close()
                moveTo(20f, 7f)
                verticalLineTo(4f)
                horizontalLineTo(17f)
                verticalLineTo(2f)
                horizontalLineToRelative(5f)
                verticalLineTo(7f)
                horizontalLineTo(20f)
                close()
                moveTo(17.5f, 17.5f)
                horizontalLineTo(19f)
                verticalLineTo(19f)
                horizontalLineTo(17.5f)
                verticalLineTo(17.5f)
                close()
                moveToRelative(0f, -3f)
                horizontalLineTo(19f)
                verticalLineTo(16f)
                horizontalLineTo(17.5f)
                verticalLineTo(14.5f)
                close()
                moveTo(16f, 16f)
                horizontalLineToRelative(1.5f)
                verticalLineToRelative(1.5f)
                horizontalLineTo(16f)
                verticalLineTo(16f)
                close()
                moveToRelative(-1.5f, 1.5f)
                horizontalLineTo(16f)
                verticalLineTo(19f)
                horizontalLineTo(14.5f)
                verticalLineTo(17.5f)
                close()
                moveTo(13f, 16f)
                horizontalLineToRelative(1.5f)
                verticalLineToRelative(1.5f)
                horizontalLineTo(13f)
                verticalLineTo(16f)
                close()
                moveToRelative(3f, -3f)
                horizontalLineToRelative(1.5f)
                verticalLineToRelative(1.5f)
                horizontalLineTo(16f)
                verticalLineTo(13f)
                close()
                moveToRelative(-1.5f, 1.5f)
                horizontalLineTo(16f)
                verticalLineTo(16f)
                horizontalLineTo(14.5f)
                verticalLineTo(14.5f)
                close()
                moveTo(13f, 13f)
                horizontalLineToRelative(1.5f)
                verticalLineToRelative(1.5f)
                horizontalLineTo(13f)
                verticalLineTo(13f)
                close()
                moveTo(19f, 5f)
                verticalLineToRelative(6f)
                horizontalLineTo(13f)
                verticalLineTo(5f)
                horizontalLineToRelative(6f)
                close()
                moveToRelative(-8f, 8f)
                verticalLineToRelative(6f)
                horizontalLineTo(5f)
                verticalLineTo(13f)
                horizontalLineToRelative(6f)
                close()
                moveTo(11f, 5f)
                verticalLineToRelative(6f)
                horizontalLineTo(5f)
                verticalLineTo(5f)
                horizontalLineToRelative(6f)
                close()
                moveTo(9.5f, 17.5f)
                verticalLineToRelative(-3f)
                horizontalLineToRelative(-3f)
                verticalLineToRelative(3f)
                horizontalLineToRelative(3f)
                close()
                moveToRelative(0f, -8f)
                verticalLineToRelative(-3f)
                horizontalLineToRelative(-3f)
                verticalLineToRelative(3f)
                horizontalLineToRelative(3f)
                close()
                moveToRelative(8f, 0f)
                verticalLineToRelative(-3f)
                horizontalLineToRelative(-3f)
                verticalLineToRelative(3f)
                horizontalLineToRelative(3f)
                close()
            }
        }.build().also { _qrCodeScanner = it }

    val Clipboard: ImageVector
        get() = _clipboard ?: ImageVector.Builder(
            name = "clipboard",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel,
                strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero,
            ) {
                moveTo(19f, 2f)
                horizontalLineToRelative(-4.18f)
                curveTo(14.4f, 0.84f, 13.3f, 0f, 12f, 0f)
                curveToRelative(-1.3f, 0f, -2.4f, 0.84f, -2.82f, 2f)
                horizontalLineTo(5f)
                curveTo(3.9f, 2f, 3f, 2.9f, 3f, 4f)
                verticalLineToRelative(16f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(14f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineTo(4f)
                curveTo(21f, 2.9f, 20.1f, 2f, 19f, 2f)
                close()
                moveTo(12f, 2f)
                curveToRelative(0.55f, 0f, 1f, 0.45f, 1f, 1f)
                reflectiveCurveToRelative(-0.45f, 1f, -1f, 1f)
                reflectiveCurveToRelative(-1f, -0.45f, -1f, -1f)
                reflectiveCurveTo(11.45f, 2f, 12f, 2f)
                close()
                moveTo(19f, 20f)
                horizontalLineTo(5f)
                verticalLineTo(4f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(3f)
                horizontalLineToRelative(10f)
                verticalLineTo(4f)
                horizontalLineToRelative(2f)
                verticalLineTo(20f)
                close()
            }
        }.build().also { _clipboard = it }

    val Battery1: ImageVector
        get() = _battery1 ?: ImageVector.Builder(
            name = "battery_1",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel,
                strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero,
            ) {
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
            }
        }.build().also { _battery1 = it }

    val Battery2: ImageVector
        get() = _battery2 ?: ImageVector.Builder(
            name = "battery_2",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel,
                strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero,
            ) {
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
            }
        }.build().also { _battery2 = it }

    val Battery3: ImageVector
        get() = _battery3 ?: ImageVector.Builder(
            name = "battery_3",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel,
                strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero,
            ) {
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
            }
        }.build().also { _battery3 = it }

    val Battery4: ImageVector
        get() = _battery4 ?: ImageVector.Builder(
            name = "battery_4",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel,
                strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero,
            ) {
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
            }
        }.build().also { _battery4 = it }

    val Battery5: ImageVector
        get() = _battery5 ?: ImageVector.Builder(
            name = "battery_5",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel,
                strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero,
            ) {
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
            }
        }.build().also { _battery5 = it }

    val Battery6: ImageVector
        get() = _battery6 ?: ImageVector.Builder(
            name = "battery_6",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel,
                strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero,
            ) {
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
            }
        }.build().also { _battery6 = it }

    val BatteryFull: ImageVector
        get() = _batteryFull ?: ImageVector.Builder(
            name = "battery_full",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel,
                strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero,
            ) {
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
                moveTo(4f, 14f)
                verticalLineTo(10f)
                quadTo(4f, 9.57f, 4.29f, 9.29f)
                reflectiveQuadTo(5f, 9f)
                horizontalLineTo(16.5f)
                quadToRelative(0.43f, 0f, 0.71f, 0.29f)
                reflectiveQuadTo(17.5f, 10f)
                verticalLineToRelative(4f)
                quadToRelative(0f, 0.42f, -0.29f, 0.71f)
                reflectiveQuadTo(16.5f, 15f)
                horizontalLineTo(5f)
                quadTo(4.58f, 15f, 4.29f, 14.71f)
                reflectiveQuadTo(4f, 14f)
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

    val Pin: ImageVector
        get() = _pin ?: ImageVector.Builder(
            name = "pin",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel,
                strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero,
            ) {
                moveTo(4f, 20f)
                quadTo(3.18f, 20f, 2.59f, 19.41f)
                reflectiveQuadTo(2f, 18f)
                verticalLineTo(6f)
                quadTo(2f, 5.18f, 2.59f, 4.59f)
                reflectiveQuadTo(4f, 4f)
                horizontalLineTo(20f)
                quadToRelative(0.83f, 0f, 1.41f, 0.59f)
                quadTo(22f, 5.18f, 22f, 6f)
                verticalLineTo(18f)
                quadToRelative(0f, 0.82f, -0.59f, 1.41f)
                reflectiveQuadTo(20f, 20f)
                horizontalLineTo(4f)
                close()
                moveTo(4f, 18f)
                horizontalLineTo(20f)
                verticalLineTo(6f)
                horizontalLineTo(4f)
                verticalLineTo(18f)
                close()
                moveToRelative(0f, 0f)
                verticalLineTo(6f)
                verticalLineTo(18f)
                close()
                moveTo(7.49f, 14.84f)
                quadTo(7.65f, 14.68f, 7.65f, 14.43f)
                verticalLineTo(9.65f)
                quadTo(7.65f, 9.38f, 7.46f, 9.19f)
                reflectiveQuadTo(7f, 9f)
                quadTo(6.88f, 9f, 6.76f, 9.04f)
                reflectiveQuadTo(6.55f, 9.15f)
                lineTo(5.43f, 9.95f)
                quadTo(5.25f, 10.07f, 5.21f, 10.27f)
                quadToRelative(-0.04f, 0.2f, 0.09f, 0.4f)
                quadToRelative(0.13f, 0.2f, 0.34f, 0.24f)
                reflectiveQuadTo(6.05f, 10.83f)
                lineTo(6.5f, 10.5f)
                verticalLineToRelative(3.92f)
                quadToRelative(0f, 0.25f, 0.16f, 0.41f)
                reflectiveQuadTo(7.08f, 15f)
                reflectiveQuadTo(7.49f, 14.84f)
                close()
                moveTo(10.1f, 15f)
                horizontalLineTo(13f)
                quadToRelative(0.2f, 0f, 0.35f, -0.15f)
                reflectiveQuadTo(13.5f, 14.5f)
                reflectiveQuadTo(13.35f, 14.15f)
                reflectiveQuadTo(13f, 14f)
                horizontalLineTo(11.15f)
                lineTo(11.1f, 13.95f)
                quadToRelative(0.52f, -0.5f, 0.86f, -0.85f)
                reflectiveQuadTo(12.5f, 12.55f)
                quadToRelative(0.45f, -0.45f, 0.68f, -0.9f)
                reflectiveQuadTo(13.4f, 10.7f)
                quadToRelative(0f, -0.72f, -0.55f, -1.21f)
                reflectiveQuadTo(11.45f, 9f)
                quadTo(10.95f, 9f, 10.5f, 9.24f)
                quadTo(10.05f, 9.48f, 9.78f, 9.9f)
                quadTo(9.65f, 10.07f, 9.75f, 10.27f)
                quadToRelative(0.1f, 0.2f, 0.3f, 0.28f)
                quadToRelative(0.2f, 0.08f, 0.4f, 0f)
                quadToRelative(0.2f, -0.07f, 0.35f, -0.22f)
                quadToRelative(0.13f, -0.13f, 0.29f, -0.2f)
                reflectiveQuadToRelative(0.36f, -0.08f)
                quadToRelative(0.38f, 0f, 0.61f, 0.2f)
                reflectiveQuadToRelative(0.24f, 0.5f)
                quadToRelative(0f, 0.27f, -0.1f, 0.51f)
                reflectiveQuadToRelative(-0.45f, 0.59f)
                quadToRelative(-0.13f, 0.13f, -0.32f, 0.32f)
                quadToRelative(-0.2f, 0.2f, -0.47f, 0.47f)
                lineToRelative(-1.2f, 1.2f)
                quadTo(9.7f, 13.9f, 9.6f, 14.2f)
                verticalLineToRelative(0.3f)
                quadToRelative(0f, 0.2f, 0.15f, 0.35f)
                reflectiveQuadTo(10.1f, 15f)
                close()
                moveTo(17f, 15f)
                quadToRelative(0.9f, 0f, 1.45f, -0.5f)
                reflectiveQuadTo(19f, 13.2f)
                quadToRelative(0f, -0.45f, -0.25f, -0.8f)
                reflectiveQuadToRelative(-0.7f, -0.55f)
                verticalLineTo(11.8f)
                quadTo(18.4f, 11.6f, 18.6f, 11.29f)
                reflectiveQuadToRelative(0.2f, -0.74f)
                quadToRelative(0f, -0.67f, -0.52f, -1.11f)
                reflectiveQuadTo(16.95f, 9f)
                quadToRelative(-0.5f, 0f, -0.92f, 0.24f)
                quadTo(15.6f, 9.48f, 15.33f, 9.82f)
                quadTo(15.2f, 10f, 15.3f, 10.17f)
                reflectiveQuadToRelative(0.3f, 0.28f)
                quadToRelative(0.2f, 0.07f, 0.4f, 0.01f)
                reflectiveQuadToRelative(0.35f, -0.21f)
                quadToRelative(0.13f, -0.13f, 0.27f, -0.19f)
                quadTo(16.78f, 10f, 16.95f, 10f)
                quadToRelative(0.33f, 0f, 0.54f, 0.19f)
                reflectiveQuadToRelative(0.21f, 0.46f)
                quadToRelative(0f, 0.35f, -0.25f, 0.55f)
                reflectiveQuadTo(16.8f, 11.4f)
                quadToRelative(-0.2f, 0f, -0.35f, 0.15f)
                reflectiveQuadTo(16.3f, 11.9f)
                reflectiveQuadToRelative(0.15f, 0.35f)
                reflectiveQuadTo(16.8f, 12.4f)
                quadToRelative(0.5f, 0f, 0.8f, 0.2f)
                reflectiveQuadToRelative(0.3f, 0.55f)
                quadToRelative(0f, 0.33f, -0.28f, 0.56f)
                reflectiveQuadTo(17f, 13.95f)
                quadToRelative(-0.3f, 0f, -0.5f, -0.1f)
                reflectiveQuadTo(16.15f, 13.52f)
                quadTo(16.03f, 13.35f, 15.84f, 13.29f)
                reflectiveQuadTo(15.45f, 13.3f)
                quadToRelative(-0.22f, 0.1f, -0.33f, 0.29f)
                quadToRelative(-0.1f, 0.19f, 0f, 0.39f)
                quadToRelative(0.28f, 0.5f, 0.75f, 0.76f)
                reflectiveQuadTo(17f, 15f)
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


    val BatteryCharging: ImageVector
        get() = _batteryCharging ?: ImageVector.Builder(
            name = "battery_charging",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel,
                strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero,
            ) {
                moveTo(4f, 18f)
                quadTo(2.75f, 18f, 1.88f, 17.13f)
                reflectiveQuadTo(1f, 15f)
                verticalLineTo(9f)
                quadTo(1f, 7.75f, 1.88f, 6.88f)
                reflectiveQuadTo(4f, 6f)
                horizontalLineTo(15.45f)
                quadToRelative(0.42f, 0f, 0.71f, 0.29f)
                reflectiveQuadTo(16.45f, 7f)
                reflectiveQuadTo(16.16f, 7.71f)
                reflectiveQuadTo(15.45f, 8f)
                horizontalLineTo(4f)
                quadTo(3.58f, 8f, 3.29f, 8.29f)
                reflectiveQuadTo(3f, 9f)
                verticalLineToRelative(6f)
                quadToRelative(0f, 0.42f, 0.29f, 0.71f)
                reflectiveQuadTo(4f, 16f)
                horizontalLineTo(14.45f)
                quadToRelative(0.42f, 0f, 0.71f, 0.29f)
                reflectiveQuadTo(15.45f, 17f)
                reflectiveQuadToRelative(-0.29f, 0.71f)
                reflectiveQuadTo(14.45f, 18f)
                horizontalLineTo(4f)
                close()
                moveTo(4f, 14f)
                verticalLineTo(10f)
                quadTo(4f, 9.57f, 4.29f, 9.29f)
                reflectiveQuadTo(5f, 9f)
                horizontalLineToRelative(8.58f)
                quadToRelative(0.65f, 0f, 0.91f, 0.56f)
                reflectiveQuadToRelative(-0.14f, 1.06f)
                lineToRelative(-2.9f, 3.63f)
                quadTo(11.18f, 14.6f, 10.76f, 14.8f)
                reflectiveQuadTo(9.9f, 15f)
                horizontalLineTo(5f)
                quadTo(4.58f, 15f, 4.29f, 14.71f)
                reflectiveQuadTo(4f, 14f)
                close()
                moveToRelative(14.15f, 3.07f)
                quadToRelative(-0.13f, 0.15f, -0.31f, 0.07f)
                quadTo(17.65f, 17.08f, 17.7f, 16.88f)
                lineTo(18.38f, 13f)
                horizontalLineTo(16.05f)
                quadToRelative(-0.32f, 0f, -0.46f, -0.28f)
                reflectiveQuadTo(15.65f, 12.2f)
                lineToRelative(4.2f, -5.27f)
                quadTo(19.98f, 6.77f, 20.16f, 6.85f)
                quadToRelative(0.19f, 0.08f, 0.14f, 0.28f)
                lineTo(19.63f, 11f)
                horizontalLineToRelative(2.33f)
                quadToRelative(0.32f, 0f, 0.46f, 0.27f)
                reflectiveQuadTo(22.35f, 11.8f)
                lineToRelative(-4.2f, 5.28f)
                close()
            }
        }.build().also { _batteryCharging = it }

    val MoreVert: ImageVector
        get() = _moreVert ?: ImageVector.Builder(
            name = "more_vert",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 16f)
                quadToRelative(0.825f, 0f, 1.413f, 0.588f)
                reflectiveQuadTo(14f, 18f)
                reflectiveQuadToRelative(-0.588f, 1.413f)
                reflectiveQuadTo(12f, 20f)
                reflectiveQuadToRelative(-1.413f, -0.588f)
                reflectiveQuadTo(10f, 18f)
                reflectiveQuadToRelative(0.588f, -1.413f)
                reflectiveQuadTo(12f, 16f)
                close()
                moveTo(12f, 10f)
                quadToRelative(0.825f, 0f, 1.413f, 0.588f)
                reflectiveQuadTo(14f, 12f)
                reflectiveQuadToRelative(-0.588f, 1.413f)
                reflectiveQuadTo(12f, 14f)
                reflectiveQuadToRelative(-1.413f, -0.588f)
                reflectiveQuadTo(10f, 12f)
                reflectiveQuadToRelative(0.588f, -1.413f)
                reflectiveQuadTo(12f, 10f)
                close()
                moveTo(12f, 4f)
                quadToRelative(0.825f, 0f, 1.413f, 0.588f)
                reflectiveQuadTo(14f, 6f)
                reflectiveQuadToRelative(-0.588f, 1.413f)
                reflectiveQuadTo(12f, 8f)
                reflectiveQuadToRelative(-1.413f, -0.588f)
                reflectiveQuadTo(10f, 6f)
                reflectiveQuadToRelative(0.588f, -1.413f)
                reflectiveQuadTo(12f, 4f)
                close()
            }
        }.build().also { _moreVert = it }

    val FilterList: ImageVector
        get() = _filterList ?: ImageVector.Builder(
            name = "filter_list",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(10f, 18f)
                horizontalLineToRelative(4f)
                verticalLineToRelative(-2f)
                horizontalLineToRelative(-4f)
                verticalLineTo(2f)
                close()
                moveTo(3f, 6f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(18f)
                verticalLineTo(6f)
                horizontalLineTo(3f)
                close()
                moveTo(6f, 13f)
                horizontalLineToRelative(12f)
                verticalLineToRelative(-2f)
                horizontalLineTo(6f)
                verticalLineToRelative(2f)
                close()
            }
        }.build().also { _filterList = it }

    val MusicNote: ImageVector
        get() = _musicNote ?: ImageVector.Builder(
            name = "music_note",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel,
                strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero,
            ) {
                moveTo(12f, 3f)
                verticalLineToRelative(10.55f)
                curveTo(11.41f, 13.21f, 10.74f, 13f, 10f, 13f)
                curveToRelative(-2.21f, 0f, -4f, 1.79f, -4f, 4f)
                reflectiveCurveToRelative(1.79f, 4f, 4f, 4f)
                reflectiveCurveToRelative(4f, -1.79f, 4f, -4f)
                verticalLineTo(7f)
                horizontalLineToRelative(4f)
                verticalLineTo(3f)
                horizontalLineToRelative(-6f)
                close()
            }
        }.build().also { _musicNote = it }

    val PlayArrow: ImageVector
        get() = _playArrow ?: ImageVector.Builder(
            name = "play_arrow",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel,
                strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero,
            ) {
                moveTo(8f, 5f)
                verticalLineToRelative(14f)
                lineToRelative(11f, -7f)
                close()
            }
        }.build().also { _playArrow = it }

    val Pause: ImageVector
        get() = _pause ?: ImageVector.Builder(
            name = "pause",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel,
                strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero,
            ) {
                moveTo(6f, 19f)
                horizontalLineToRelative(4f)
                verticalLineTo(5f)
                horizontalLineTo(6f)
                verticalLineToRelative(14f)
                close()
                moveTo(14f, 5f)
                verticalLineToRelative(14f)
                horizontalLineToRelative(4f)
                verticalLineTo(5f)
                horizontalLineToRelative(-4f)
                close()
            }
        }.build().also { _pause = it }

    private var _musicNote: ImageVector? = null
    private var _playArrow: ImageVector? = null
    private var _pause: ImageVector? = null
    private var _google: ImageVector? = null
    private var _wifi: ImageVector? = null
    private var _clipboard: ImageVector? = null
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
    private var _send: ImageVector? = null
    private var _cloud: ImageVector? = null
    private var _search: ImageVector? = null
    private var _computer: ImageVector? = null
    private var _smartphone: ImageVector? = null
    private var _tune: ImageVector? = null
    private var _accountCircle: ImageVector? = null
    private var _devices: ImageVector? = null
    private var _qrCodeScanner: ImageVector? = null
    private var _moreVert: ImageVector? = null
    private var _filterList: ImageVector? = null
    private var _iosShare: ImageVector? = null
    private var _fileUpload: ImageVector? = null
    private var _fileDownload: ImageVector? = null
    private var _cloudDownload: ImageVector? = null
    private var _cloudUpload: ImageVector? = null
    private var _arrowUploadReady: ImageVector? = null
    private var _arrowUploadCircle: ImageVector? = null
    private var _arrowUploadArrow: ImageVector? = null
    private var _arrowDownloadArrow: ImageVector? = null
}
