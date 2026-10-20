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

val FluentuiSystemIconsArrowClockwise: ImageVector
    get() {
        if (_FluentuiSystemIconsArrowClockwise != null) return _FluentuiSystemIconsArrowClockwise!!

        _FluentuiSystemIconsArrowClockwise = ImageVector.Builder(
            name = "arrow-clockwise",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(12f, 4.5f)
                curveTo(7.85786f, 4.5f, 4.5f, 7.85786f, 4.5f, 12f)
                curveTo(4.5f, 16.1421f, 7.85786f, 19.5f, 12f, 19.5f)
                curveTo(16.1421f, 19.5f, 19.5f, 16.1421f, 19.5f, 12f)
                curveTo(19.5f, 11.6236f, 19.4723f, 11.2538f, 19.4188f, 10.8923f)
                curveTo(19.3515f, 10.4382f, 19.6839f, 10f, 20.1429f, 10f)
                curveTo(20.5138f, 10f, 20.839f, 10.2562f, 20.8953f, 10.6228f)
                curveTo(20.9642f, 11.0718f, 21f, 11.5317f, 21f, 12f)
                curveTo(21f, 16.9706f, 16.9706f, 21f, 12f, 21f)
                curveTo(7.02944f, 21f, 3f, 16.9706f, 3f, 12f)
                curveTo(3f, 7.02944f, 7.02944f, 3f, 12f, 3f)
                curveTo(14.3051f, 3f, 16.4077f, 3.86656f, 18f, 5.29168f)
                verticalLineTo(4.25f)
                curveTo(18f, 3.83579f, 18.3358f, 3.5f, 18.75f, 3.5f)
                curveTo(19.1642f, 3.5f, 19.5f, 3.83579f, 19.5f, 4.25f)
                verticalLineTo(7.25f)
                curveTo(19.5f, 7.66421f, 19.1642f, 8f, 18.75f, 8f)
                horizontalLineTo(15.75f)
                curveTo(15.3358f, 8f, 15f, 7.66421f, 15f, 7.25f)
                curveTo(15f, 6.83579f, 15.3358f, 6.5f, 15.75f, 6.5f)
                horizontalLineTo(17.0991f)
                curveTo(15.7609f, 5.25883f, 13.9691f, 4.5f, 12f, 4.5f)
                close()
            }
        }.build()

        return _FluentuiSystemIconsArrowClockwise!!
    }

private var _FluentuiSystemIconsArrowClockwise: ImageVector? = null
