package org.starfall.multigateway.shared.ui.icons.fluentui.regular

/*
MIT License

Copyright (c) 2020 Microsoft Corporation

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val FluentuiSystemIconsFolderAdd: ImageVector
    get() {
        if (_FluentuiSystemIconsFolderAdd != null) return _FluentuiSystemIconsFolderAdd!!

        _FluentuiSystemIconsFolderAdd = ImageVector.Builder(
            name = "folder-add",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(3.5f, 6.25f)
                verticalLineTo(8f)
                horizontalLineTo(8.12868f)
                curveTo(8.32759f, 8f, 8.51836f, 7.92098f, 8.65901f, 7.78033f)
                lineTo(10.1893f, 6.25f)
                lineTo(8.65901f, 4.71967f)
                curveTo(8.51836f, 4.57902f, 8.32759f, 4.5f, 8.12868f, 4.5f)
                horizontalLineTo(5.25f)
                curveTo(4.2835f, 4.5f, 3.5f, 5.2835f, 3.5f, 6.25f)
                close()
                moveTo(2f, 6.25f)
                curveTo(2f, 4.45507f, 3.45507f, 3f, 5.25f, 3f)
                horizontalLineTo(8.12868f)
                curveTo(8.72542f, 3f, 9.29771f, 3.23705f, 9.71967f, 3.65901f)
                lineTo(11.5607f, 5.5f)
                horizontalLineTo(18.75f)
                curveTo(20.5449f, 5.5f, 22f, 6.95507f, 22f, 8.75f)
                verticalLineTo(12.8096f)
                curveTo(21.5557f, 12.3832f, 21.051f, 12.0194f, 20.5f, 11.7322f)
                verticalLineTo(8.75f)
                curveTo(20.5f, 7.7835f, 19.7165f, 7f, 18.75f, 7f)
                horizontalLineTo(11.5607f)
                lineTo(9.71967f, 8.84099f)
                curveTo(9.29771f, 9.26295f, 8.72542f, 9.5f, 8.12868f, 9.5f)
                horizontalLineTo(3.5f)
                verticalLineTo(17.75f)
                curveTo(3.5f, 18.7165f, 4.2835f, 19.5f, 5.25f, 19.5f)
                horizontalLineTo(11.3135f)
                curveTo(11.4858f, 20.0335f, 11.7253f, 20.5368f, 12.0218f, 21f)
                horizontalLineTo(5.25f)
                curveTo(3.45507f, 21f, 2f, 19.5449f, 2f, 17.75f)
                verticalLineTo(6.25f)
                close()
                moveTo(23f, 17.5f)
                curveTo(23f, 14.4624f, 20.5376f, 12f, 17.5f, 12f)
                curveTo(14.4624f, 12f, 12f, 14.4624f, 12f, 17.5f)
                curveTo(12f, 20.5376f, 14.4624f, 23f, 17.5f, 23f)
                curveTo(20.5376f, 23f, 23f, 20.5376f, 23f, 17.5f)
                close()
                moveTo(18.0006f, 18f)
                lineTo(18.0011f, 20.5035f)
                curveTo(18.0011f, 20.7797f, 17.7773f, 21.0035f, 17.5011f, 21.0035f)
                curveTo(17.225f, 21.0035f, 17.0011f, 20.7797f, 17.0011f, 20.5035f)
                lineTo(17.0006f, 18f)
                horizontalLineTo(14.4956f)
                curveTo(14.2197f, 18f, 13.9961f, 17.7762f, 13.9961f, 17.5f)
                curveTo(13.9961f, 17.2239f, 14.2197f, 17f, 14.4956f, 17f)
                horizontalLineTo(17.0005f)
                lineTo(17f, 14.4993f)
                curveTo(17f, 14.2231f, 17.2239f, 13.9993f, 17.5f, 13.9993f)
                curveTo(17.7761f, 13.9993f, 18f, 14.2231f, 18f, 14.4993f)
                lineTo(18.0005f, 17f)
                horizontalLineTo(20.4966f)
                curveTo(20.7725f, 17f, 20.9961f, 17.2239f, 20.9961f, 17.5f)
                curveTo(20.9961f, 17.7762f, 20.7725f, 18f, 20.4966f, 18f)
                horizontalLineTo(18.0006f)
                close()
            }
        }.build()

        return _FluentuiSystemIconsFolderAdd!!
    }

private var _FluentuiSystemIconsFolderAdd: ImageVector? = null
