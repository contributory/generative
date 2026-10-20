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

val FluentuiSystemIconsArrowDownload: ImageVector
    get() {
        if (_FluentuiSystemIconsArrowDownload != null) return _FluentuiSystemIconsArrowDownload!!

        _FluentuiSystemIconsArrowDownload = ImageVector.Builder(
            name = "arrow-download",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(18.2498f, 20.5009f)
                curveTo(18.664f, 20.5008f, 19f, 20.8365f, 19f, 21.2507f)
                curveTo(19f, 21.6649f, 18.6644f, 22.0008f, 18.2502f, 22.0009f)
                lineTo(5.25022f, 22.0047f)
                curveTo(4.836f, 22.0048f, 4.5f, 21.6691f, 4.5f, 21.2549f)
                curveTo(4.5f, 20.8407f, 4.83557f, 20.5048f, 5.24978f, 20.5047f)
                lineTo(18.2498f, 20.5009f)
                close()
                moveTo(11.6482f, 2.01271f)
                lineTo(11.75f, 2.00586f)
                curveTo(12.1297f, 2.00586f, 12.4435f, 2.28801f, 12.4932f, 2.65409f)
                lineTo(12.5f, 2.75586f)
                lineTo(12.499f, 16.4409f)
                lineTo(16.2208f, 12.7205f)
                curveTo(16.4871f, 12.4543f, 16.9038f, 12.4301f, 17.1974f, 12.648f)
                lineTo(17.2815f, 12.7206f)
                curveTo(17.5477f, 12.9869f, 17.5719f, 13.4036f, 17.354f, 13.6972f)
                lineTo(17.2814f, 13.7813f)
                lineTo(12.2837f, 18.7779f)
                curveTo(12.0176f, 19.044f, 11.6012f, 19.0683f, 11.3076f, 18.8507f)
                lineTo(11.2235f, 18.7782f)
                lineTo(6.22003f, 13.7816f)
                curveTo(5.92694f, 13.4889f, 5.92661f, 13.014f, 6.21931f, 12.7209f)
                curveTo(6.48539f, 12.4545f, 6.90204f, 12.43f, 7.1958f, 12.6477f)
                lineTo(7.27997f, 12.7202f)
                lineTo(10.999f, 16.4339f)
                lineTo(11f, 2.75586f)
                curveTo(11f, 2.37616f, 11.2822f, 2.06237f, 11.6482f, 2.01271f)
                lineTo(11.75f, 2.00586f)
                lineTo(11.6482f, 2.01271f)
                close()
            }
        }.build()

        return _FluentuiSystemIconsArrowDownload!!
    }

private var _FluentuiSystemIconsArrowDownload: ImageVector? = null
