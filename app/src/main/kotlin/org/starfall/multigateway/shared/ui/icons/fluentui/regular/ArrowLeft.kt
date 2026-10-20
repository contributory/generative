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

val FluentuiSystemIconsArrowLeft: ImageVector
    get() {
        if (_FluentuiSystemIconsArrowLeft != null) return _FluentuiSystemIconsArrowLeft!!

        _FluentuiSystemIconsArrowLeft = ImageVector.Builder(
            name = "arrow-left",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(10.7327f, 19.7905f)
                curveTo(11.0326f, 20.0762f, 11.5074f, 20.0646f, 11.7931f, 19.7647f)
                curveTo(12.0788f, 19.4648f, 12.0672f, 18.99f, 11.7673f, 18.7043f)
                lineTo(5.51587f, 12.7497f)
                lineTo(20.25f, 12.7497f)
                curveTo(20.6642f, 12.7497f, 21f, 12.4139f, 21f, 11.9997f)
                curveTo(21f, 11.5855f, 20.6642f, 11.2497f, 20.25f, 11.2497f)
                lineTo(5.51577f, 11.2497f)
                lineTo(11.7673f, 5.29502f)
                curveTo(12.0672f, 5.00933f, 12.0787f, 4.5346f, 11.7931f, 4.23467f)
                curveTo(11.5074f, 3.93475f, 11.0326f, 3.9232f, 10.7327f, 4.20889f)
                lineTo(3.31379f, 11.2756f)
                curveTo(3.14486f, 11.4365f, 3.04491f, 11.6417f, 3.01393f, 11.8551f)
                curveTo(3.00479f, 11.9019f, 3f, 11.9503f, 3f, 11.9997f)
                curveTo(3f, 12.0493f, 3.00481f, 12.0977f, 3.01398f, 12.1446f)
                curveTo(3.04502f, 12.3579f, 3.14496f, 12.563f, 3.31379f, 12.7238f)
                lineTo(10.7327f, 19.7905f)
                close()
            }
        }.build()

        return _FluentuiSystemIconsArrowLeft!!
    }

private var _FluentuiSystemIconsArrowLeft: ImageVector? = null
