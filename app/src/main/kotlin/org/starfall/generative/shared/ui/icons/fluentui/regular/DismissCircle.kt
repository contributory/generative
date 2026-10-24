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

val FluentuiSystemIconsDismissCircle: ImageVector
    get() {
        if (_FluentuiSystemIconsDismissCircle != null) return _FluentuiSystemIconsDismissCircle!!

        _FluentuiSystemIconsDismissCircle = ImageVector.Builder(
            name = "dismiss-circle",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(12f, 2f)
                curveTo(17.5228f, 2f, 22f, 6.47715f, 22f, 12f)
                curveTo(22f, 17.5228f, 17.5228f, 22f, 12f, 22f)
                curveTo(6.47715f, 22f, 2f, 17.5228f, 2f, 12f)
                curveTo(2f, 6.47715f, 6.47715f, 2f, 12f, 2f)
                close()
                moveTo(12f, 3.5f)
                curveTo(7.30558f, 3.5f, 3.5f, 7.30558f, 3.5f, 12f)
                curveTo(3.5f, 16.6944f, 7.30558f, 20.5f, 12f, 20.5f)
                curveTo(16.6944f, 20.5f, 20.5f, 16.6944f, 20.5f, 12f)
                curveTo(20.5f, 7.30558f, 16.6944f, 3.5f, 12f, 3.5f)
                close()
                moveTo(15.4462f, 8.39705f)
                lineTo(15.5303f, 8.46967f)
                curveTo(15.7966f, 8.73594f, 15.8208f, 9.1526f, 15.6029f, 9.44621f)
                lineTo(15.5303f, 9.53033f)
                lineTo(13.061f, 12f)
                lineTo(15.5303f, 14.4697f)
                curveTo(15.7966f, 14.7359f, 15.8208f, 15.1526f, 15.6029f, 15.4462f)
                lineTo(15.5303f, 15.5303f)
                curveTo(15.2641f, 15.7966f, 14.8474f, 15.8208f, 14.5538f, 15.6029f)
                lineTo(14.4697f, 15.5303f)
                lineTo(12f, 13.061f)
                lineTo(9.53033f, 15.5303f)
                curveTo(9.26406f, 15.7966f, 8.8474f, 15.8208f, 8.55379f, 15.6029f)
                lineTo(8.46967f, 15.5303f)
                curveTo(8.2034f, 15.2641f, 8.1792f, 14.8474f, 8.39705f, 14.5538f)
                lineTo(8.46967f, 14.4697f)
                lineTo(10.939f, 12f)
                lineTo(8.46967f, 9.53033f)
                curveTo(8.2034f, 9.26406f, 8.1792f, 8.8474f, 8.39705f, 8.55379f)
                lineTo(8.46967f, 8.46967f)
                curveTo(8.73594f, 8.2034f, 9.1526f, 8.1792f, 9.44621f, 8.39705f)
                lineTo(9.53033f, 8.46967f)
                lineTo(12f, 10.939f)
                lineTo(14.4697f, 8.46967f)
                curveTo(14.7359f, 8.2034f, 15.1526f, 8.1792f, 15.4462f, 8.39705f)
                close()
            }
        }.build()

        return _FluentuiSystemIconsDismissCircle!!
    }

private var _FluentuiSystemIconsDismissCircle: ImageVector? = null
