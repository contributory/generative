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

val FluentuiSystemIconsArrowUpload: ImageVector
    get() {
        if (_FluentuiSystemIconsArrowUpload != null) return _FluentuiSystemIconsArrowUpload!!

        _FluentuiSystemIconsArrowUpload = ImageVector.Builder(
            name = "arrow-upload",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(18.2498f, 3.50962f)
                curveTo(18.664f, 3.50974f, 19f, 3.17405f, 19f, 2.75984f)
                curveTo(19f, 2.34563f, 18.6644f, 2.00974f, 18.2502f, 2.00962f)
                lineTo(5.25022f, 2.00586f)
                curveTo(4.836f, 2.00574f, 4.5f, 2.34143f, 4.5f, 2.75564f)
                curveTo(4.5f, 3.16986f, 4.83557f, 3.50574f, 5.24978f, 3.50586f)
                lineTo(18.2498f, 3.50962f)
                close()
                moveTo(11.6482f, 21.9978f)
                lineTo(11.75f, 22.0047f)
                curveTo(12.1297f, 22.0047f, 12.4435f, 21.7225f, 12.4932f, 21.3564f)
                lineTo(12.5f, 21.2547f)
                lineTo(12.499f, 7.56968f)
                lineTo(16.2208f, 11.29f)
                curveTo(16.4871f, 11.5562f, 16.9038f, 11.5804f, 17.1974f, 11.3625f)
                lineTo(17.2815f, 11.2899f)
                curveTo(17.5477f, 11.0236f, 17.5719f, 10.6069f, 17.354f, 10.3133f)
                lineTo(17.2814f, 10.2292f)
                lineTo(12.2837f, 5.23262f)
                curveTo(12.0176f, 4.96653f, 11.6012f, 4.94223f, 11.3076f, 5.15979f)
                lineTo(11.2235f, 5.23232f)
                lineTo(6.22003f, 10.2289f)
                curveTo(5.92694f, 10.5216f, 5.92661f, 10.9965f, 6.21931f, 11.2896f)
                curveTo(6.48539f, 11.556f, 6.90204f, 11.5805f, 7.1958f, 11.3629f)
                lineTo(7.27997f, 11.2903f)
                lineTo(10.999f, 7.57668f)
                lineTo(11f, 21.2547f)
                curveTo(11f, 21.6344f, 11.2822f, 21.9482f, 11.6482f, 21.9978f)
                close()
            }
        }.build()

        return _FluentuiSystemIconsArrowUpload!!
    }

private var _FluentuiSystemIconsArrowUpload: ImageVector? = null
