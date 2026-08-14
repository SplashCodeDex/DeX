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

    val Pin: ImageVector
        get() = _pin ?: ImageVector.Builder(
            name = "pin",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
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
                moveTo(7f, 15f)
                horizontalLineTo(9f)
                verticalLineTo(9f)
                horizontalLineTo(7f)
                verticalLineTo(10f)
                horizontalLineTo(8f)
                verticalLineTo(15f)
                close()
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
}
