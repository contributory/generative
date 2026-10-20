package org.starfall.generative.shared.ui.icons.fluentui.regular

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

val FluentuiSystemIconsImageOff: ImageVector
    get() {
        if (_FluentuiSystemIconsImageOff != null) return _FluentuiSystemIconsImageOff!!

        _FluentuiSystemIconsImageOff = ImageVector.Builder(
            name = "image-off",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(3.28034f, 2.21968f)
                curveTo(2.98745f, 1.92678f, 2.51257f, 1.92677f, 2.21968f, 2.21966f)
                curveTo(1.92678f, 2.51255f, 1.92677f, 2.98743f, 2.21966f, 3.28032f)
                lineTo(3.48326f, 4.54395f)
                curveTo(3.17684f, 5.03983f, 3f, 5.62426f, 3f, 6.25f)
                verticalLineTo(17.75f)
                curveTo(3f, 19.5449f, 4.45507f, 21f, 6.25f, 21f)
                horizontalLineTo(17.75f)
                curveTo(18.3757f, 21f, 18.96f, 20.8232f, 19.4559f, 20.5169f)
                lineTo(20.7194f, 21.7805f)
                curveTo(21.0123f, 22.0734f, 21.4872f, 22.0734f, 21.7801f, 21.7805f)
                curveTo(22.073f, 21.4876f, 22.073f, 21.0127f, 21.7801f, 20.7198f)
                lineTo(3.28034f, 2.21968f)
                close()
                moveTo(11.1189f, 12.1797f)
                curveTo(10.8678f, 12.2864f, 10.6322f, 12.4409f, 10.4258f, 12.643f)
                lineTo(4.60326f, 18.3437f)
                curveTo(4.53643f, 18.1583f, 4.5f, 17.9584f, 4.5f, 17.75f)
                verticalLineTo(6.25f)
                curveTo(4.5f, 6.0437f, 4.5357f, 5.84574f, 4.60125f, 5.66196f)
                lineTo(11.1189f, 12.1797f)
                close()
                moveTo(19.5f, 6.25f)
                verticalLineTo(16.3183f)
                lineTo(20.9993f, 17.8176f)
                curveTo(20.9998f, 17.7951f, 21f, 17.7726f, 21f, 17.75f)
                verticalLineTo(6.25f)
                curveTo(21f, 4.45507f, 19.5449f, 3f, 17.75f, 3f)
                horizontalLineTo(6.25f)
                curveTo(6.2275f, 3f, 6.20505f, 3.00023f, 6.18265f, 3.00068f)
                lineTo(7.68194f, 4.5f)
                horizontalLineTo(17.75f)
                curveTo(18.7165f, 4.5f, 19.5f, 5.2835f, 19.5f, 6.25f)
                close()
                moveTo(18.3305f, 19.4014f)
                curveTo(18.1489f, 19.4653f, 17.9535f, 19.5f, 17.75f, 19.5f)
                horizontalLineTo(6.25f)
                curveTo(6.04613f, 19.5f, 5.8504f, 19.4651f, 5.66845f, 19.4011f)
                lineTo(11.4752f, 13.7148f)
                lineTo(11.5588f, 13.644f)
                curveTo(11.8501f, 13.4316f, 12.2596f, 13.4553f, 12.5247f, 13.7148f)
                lineTo(18.3305f, 19.4014f)
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

        return _FluentuiSystemIconsImageOff!!
    }

private var _FluentuiSystemIconsImageOff: ImageVector? = null
