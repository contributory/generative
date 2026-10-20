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

val FluentuiSystemIconsCheckmark: ImageVector
    get() {
        if (_FluentuiSystemIconsCheckmark != null) return _FluentuiSystemIconsCheckmark!!

        _FluentuiSystemIconsCheckmark = ImageVector.Builder(
            name = "checkmark",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(4.53033f, 12.9697f)
                curveTo(4.23744f, 12.6768f, 3.76256f, 12.6768f, 3.46967f, 12.9697f)
                curveTo(3.17678f, 13.2626f, 3.17678f, 13.7374f, 3.46967f, 14.0303f)
                lineTo(7.96967f, 18.5303f)
                curveTo(8.26256f, 18.8232f, 8.73744f, 18.8232f, 9.03033f, 18.5303f)
                lineTo(20.0303f, 7.53033f)
                curveTo(20.3232f, 7.23744f, 20.3232f, 6.76256f, 20.0303f, 6.46967f)
                curveTo(19.7374f, 6.17678f, 19.2626f, 6.17678f, 18.9697f, 6.46967f)
                lineTo(8.5f, 16.9393f)
                lineTo(4.53033f, 12.9697f)
                close()
            }
        }.build()

        return _FluentuiSystemIconsCheckmark!!
    }

private var _FluentuiSystemIconsCheckmark: ImageVector? = null
