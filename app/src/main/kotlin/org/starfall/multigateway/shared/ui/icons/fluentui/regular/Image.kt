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

val FluentuiSystemIconsImage: ImageVector
    get() {
        if (_FluentuiSystemIconsImage != null) return _FluentuiSystemIconsImage!!

        _FluentuiSystemIconsImage = ImageVector.Builder(
            name = "image",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(17.75f, 3f)
                curveTo(19.5449f, 3f, 21f, 4.45507f, 21f, 6.25f)
                verticalLineTo(17.75f)
                curveTo(21f, 19.5449f, 19.5449f, 21f, 17.75f, 21f)
                horizontalLineTo(6.25f)
                curveTo(4.45507f, 21f, 3f, 19.5449f, 3f, 17.75f)
                verticalLineTo(6.25f)
                curveTo(3f, 4.45507f, 4.45507f, 3f, 6.25f, 3f)
                horizontalLineTo(17.75f)
                close()
                moveTo(18.3305f, 19.4014f)
                lineTo(12.5247f, 13.7148f)
                curveTo(12.2596f, 13.4553f, 11.8501f, 13.4316f, 11.5588f, 13.644f)
                lineTo(11.4752f, 13.7148f)
                lineTo(5.66845f, 19.4011f)
                curveTo(5.8504f, 19.4651f, 6.04613f, 19.5f, 6.25f, 19.5f)
                horizontalLineTo(17.75f)
                curveTo(17.9535f, 19.5f, 18.1489f, 19.4653f, 18.3305f, 19.4014f)
                lineTo(12.5247f, 13.7148f)
                lineTo(18.3305f, 19.4014f)
                close()
                moveTo(17.75f, 4.5f)
                horizontalLineTo(6.25f)
                curveTo(5.2835f, 4.5f, 4.5f, 5.2835f, 4.5f, 6.25f)
                verticalLineTo(17.75f)
                curveTo(4.5f, 17.9584f, 4.53643f, 18.1583f, 4.60326f, 18.3437f)
                lineTo(10.4258f, 12.643f)
                curveTo(11.2589f, 11.8273f, 12.5675f, 11.7885f, 13.4458f, 12.5266f)
                lineTo(13.5742f, 12.6431f)
                lineTo(19.3964f, 18.3447f)
                curveTo(19.4634f, 18.159f, 19.5f, 17.9588f, 19.5f, 17.75f)
                verticalLineTo(6.25f)
                curveTo(19.5f, 5.2835f, 18.7165f, 4.5f, 17.75f, 4.5f)
                close()
                moveTo(15.2521f, 6.5f)
                curveTo(16.4959f, 6.5f, 17.5042f, 7.50831f, 17.5042f, 8.75212f)
                curveTo(17.5042f, 9.99592f, 16.4959f, 11.0042f, 15.2521f, 11.0042f)
                curveTo(14.0083f, 11.0042f, 13f, 9.99592f, 13f, 8.75212f)
                curveTo(13f, 7.50831f, 14.0083f, 6.5f, 15.2521f, 6.5f)
                close()
                moveTo(15.2521f, 8f)
                curveTo(14.8367f, 8f, 14.5f, 8.33673f, 14.5f, 8.75212f)
                curveTo(14.5f, 9.1675f, 14.8367f, 9.50423f, 15.2521f, 9.50423f)
                curveTo(15.6675f, 9.50423f, 16.0042f, 9.1675f, 16.0042f, 8.75212f)
                curveTo(16.0042f, 8.33673f, 15.6675f, 8f, 15.2521f, 8f)
                close()
            }
        }.build()

        return _FluentuiSystemIconsImage!!
    }

private var _FluentuiSystemIconsImage: ImageVector? = null
