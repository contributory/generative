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

val FluentuiSystemIconsErrorCircle: ImageVector
    get() {
        if (_FluentuiSystemIconsErrorCircle != null) return _FluentuiSystemIconsErrorCircle!!

        _FluentuiSystemIconsErrorCircle = ImageVector.Builder(
            name = "error-circle",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(12f, 2f)
                curveTo(17.523f, 2f, 22f, 6.478f, 22f, 12f)
                curveTo(22f, 17.522f, 17.523f, 22f, 12f, 22f)
                curveTo(6.477f, 22f, 2f, 17.522f, 2f, 12f)
                curveTo(2f, 6.478f, 6.477f, 2f, 12f, 2f)
                close()
                moveTo(12f, 3.667f)
                curveTo(7.405f, 3.667f, 3.667f, 7.405f, 3.667f, 12f)
                curveTo(3.667f, 16.595f, 7.405f, 20.333f, 12f, 20.333f)
                curveTo(16.595f, 20.333f, 20.333f, 16.595f, 20.333f, 12f)
                curveTo(20.333f, 7.405f, 16.595f, 3.667f, 12f, 3.667f)
                close()
                moveTo(11.9987f, 14.5022f)
                curveTo(12.5502f, 14.5022f, 12.9973f, 14.9494f, 12.9973f, 15.5009f)
                curveTo(12.9973f, 16.0524f, 12.5502f, 16.4996f, 11.9987f, 16.4996f)
                curveTo(11.4471f, 16.4996f, 11f, 16.0524f, 11f, 15.5009f)
                curveTo(11f, 14.9494f, 11.4471f, 14.5022f, 11.9987f, 14.5022f)
                close()
                moveTo(11.9945f, 7f)
                curveTo(12.3742f, 6.9997f, 12.6882f, 7.2816f, 12.7381f, 7.64764f)
                lineTo(12.7451f, 7.7494f)
                lineTo(12.7487f, 12.251f)
                curveTo(12.749f, 12.6652f, 12.4135f, 13.0013f, 11.9993f, 13.0016f)
                curveTo(11.6196f, 13.0019f, 11.3055f, 12.72f, 11.2556f, 12.354f)
                lineTo(11.2487f, 12.2522f)
                lineTo(11.2451f, 7.7506f)
                curveTo(11.2447f, 7.33639f, 11.5802f, 7.00033f, 11.9945f, 7f)
                close()
            }
        }.build()

        return _FluentuiSystemIconsErrorCircle!!
    }

private var _FluentuiSystemIconsErrorCircle: ImageVector? = null
