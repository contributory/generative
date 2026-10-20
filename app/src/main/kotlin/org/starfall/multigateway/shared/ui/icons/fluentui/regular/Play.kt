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

val FluentuiSystemIconsPlay: ImageVector
    get() {
        if (_FluentuiSystemIconsPlay != null) return _FluentuiSystemIconsPlay!!

        _FluentuiSystemIconsPlay = ImageVector.Builder(
            name = "play",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(7.60846f, 4.61489f)
                curveTo(7.1087f, 4.34296f, 6.5f, 4.70472f, 6.5f, 5.27368f)
                verticalLineTo(18.726f)
                curveTo(6.5f, 19.2949f, 7.1087f, 19.6567f, 7.60846f, 19.3848f)
                lineTo(19.97f, 12.6586f)
                curveTo(20.4921f, 12.3746f, 20.4921f, 11.6251f, 19.97f, 11.341f)
                lineTo(7.60846f, 4.61489f)
                close()
                moveTo(5f, 5.27368f)
                curveTo(5f, 3.56682f, 6.82609f, 2.48151f, 8.32538f, 3.2973f)
                lineTo(20.687f, 10.0235f)
                curveTo(22.2531f, 10.8756f, 22.2531f, 13.124f, 20.687f, 13.9762f)
                lineTo(8.32538f, 20.7024f)
                curveTo(6.82609f, 21.5181f, 5f, 20.4328f, 5f, 18.726f)
                verticalLineTo(5.27368f)
                close()
            }
        }.build()

        return _FluentuiSystemIconsPlay!!
    }

private var _FluentuiSystemIconsPlay: ImageVector? = null
