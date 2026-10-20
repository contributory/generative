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

val FluentuiSystemIconsDismiss: ImageVector
    get() {
        if (_FluentuiSystemIconsDismiss != null) return _FluentuiSystemIconsDismiss!!

        _FluentuiSystemIconsDismiss = ImageVector.Builder(
            name = "dismiss",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(4.39705f, 4.55379f)
                lineTo(4.46967f, 4.46967f)
                curveTo(4.73594f, 4.2034f, 5.1526f, 4.1792f, 5.44621f, 4.39705f)
                lineTo(5.53033f, 4.46967f)
                lineTo(12f, 10.939f)
                lineTo(18.4697f, 4.46967f)
                curveTo(18.7626f, 4.17678f, 19.2374f, 4.17678f, 19.5303f, 4.46967f)
                curveTo(19.8232f, 4.76256f, 19.8232f, 5.23744f, 19.5303f, 5.53033f)
                lineTo(13.061f, 12f)
                lineTo(19.5303f, 18.4697f)
                curveTo(19.7966f, 18.7359f, 19.8208f, 19.1526f, 19.6029f, 19.4462f)
                lineTo(19.5303f, 19.5303f)
                curveTo(19.2641f, 19.7966f, 18.8474f, 19.8208f, 18.5538f, 19.6029f)
                lineTo(18.4697f, 19.5303f)
                lineTo(12f, 13.061f)
                lineTo(5.53033f, 19.5303f)
                curveTo(5.23744f, 19.8232f, 4.76256f, 19.8232f, 4.46967f, 19.5303f)
                curveTo(4.17678f, 19.2374f, 4.17678f, 18.7626f, 4.46967f, 18.4697f)
                lineTo(10.939f, 12f)
                lineTo(4.46967f, 5.53033f)
                curveTo(4.2034f, 5.26406f, 4.1792f, 4.8474f, 4.39705f, 4.55379f)
                lineTo(4.46967f, 4.46967f)
                lineTo(4.39705f, 4.55379f)
                close()
            }
        }.build()

        return _FluentuiSystemIconsDismiss!!
    }

private var _FluentuiSystemIconsDismiss: ImageVector? = null
