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

val FluentuiSystemIconsPin: ImageVector
    get() {
        if (_FluentuiSystemIconsPin != null) return _FluentuiSystemIconsPin!!

        _FluentuiSystemIconsPin = ImageVector.Builder(
            name = "pin",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(16.2425f, 2.93189f)
                lineTo(21.0682f, 7.75765f)
                curveTo(22.3955f, 9.08491f, 22.0324f, 11.3224f, 20.3535f, 12.1619f)
                lineTo(15.4826f, 14.5973f)
                curveTo(15.3073f, 14.685f, 15.1732f, 14.8379f, 15.1092f, 15.0232f)
                lineTo(13.6699f, 19.1895f)
                curveTo(13.3684f, 20.0622f, 12.2574f, 20.3181f, 11.6045f, 19.6653f)
                lineTo(8.50002f, 16.5607f)
                lineTo(4.06074f, 21.0001f)
                horizontalLineTo(3f)
                lineTo(3.00008f, 19.9394f)
                lineTo(7.43936f, 15.5001f)
                lineTo(4.33487f, 12.3956f)
                curveTo(3.682f, 11.7427f, 3.93791f, 10.6317f, 4.81061f, 10.3302f)
                lineTo(8.97688f, 8.89096f)
                curveTo(9.16223f, 8.82694f, 9.31512f, 8.69287f, 9.40281f, 8.51748f)
                lineTo(11.8382f, 3.6466f)
                curveTo(12.6777f, 1.96772f, 14.9152f, 1.60462f, 16.2425f, 2.93189f)
                close()
                moveTo(20.0076f, 8.81831f)
                lineTo(15.1818f, 3.99255f)
                curveTo(14.5785f, 3.38924f, 13.5614f, 3.55429f, 13.1799f, 4.31742f)
                lineTo(10.7445f, 9.18829f)
                curveTo(10.4814f, 9.71446f, 10.0227f, 10.1167f, 9.46666f, 10.3087f)
                lineTo(5.67812f, 11.6175f)
                lineTo(12.3826f, 18.322f)
                lineTo(13.6914f, 14.5335f)
                curveTo(13.8835f, 13.9774f, 14.2857f, 13.5188f, 14.8118f, 13.2557f)
                lineTo(19.6827f, 10.8202f)
                curveTo(20.4458f, 10.4387f, 20.6109f, 9.42161f, 20.0076f, 8.81831f)
                close()
            }
        }.build()

        return _FluentuiSystemIconsPin!!
    }

private var _FluentuiSystemIconsPin: ImageVector? = null
