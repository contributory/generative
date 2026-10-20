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

val FluentuiSystemIconsInfo: ImageVector
    get() {
        if (_FluentuiSystemIconsInfo != null) return _FluentuiSystemIconsInfo!!

        _FluentuiSystemIconsInfo = ImageVector.Builder(
            name = "info",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(12.0016f, 1.99902f)
                curveTo(17.5253f, 1.99902f, 22.0031f, 6.47687f, 22.0031f, 12.0006f)
                curveTo(22.0031f, 17.5243f, 17.5253f, 22.0021f, 12.0016f, 22.0021f)
                curveTo(6.47785f, 22.0021f, 2f, 17.5243f, 2f, 12.0006f)
                curveTo(2f, 6.47687f, 6.47785f, 1.99902f, 12.0016f, 1.99902f)
                close()
                moveTo(12.0016f, 3.49902f)
                curveTo(7.30627f, 3.49902f, 3.5f, 7.3053f, 3.5f, 12.0006f)
                curveTo(3.5f, 16.6959f, 7.30627f, 20.5021f, 12.0016f, 20.5021f)
                curveTo(16.6968f, 20.5021f, 20.5031f, 16.6959f, 20.5031f, 12.0006f)
                curveTo(20.5031f, 7.3053f, 16.6968f, 3.49902f, 12.0016f, 3.49902f)
                close()
                moveTo(12f, 10.5f)
                curveTo(12.4142f, 10.5f, 12.75f, 10.8358f, 12.75f, 11.25f)
                verticalLineTo(16.25f)
                curveTo(12.75f, 16.6642f, 12.4142f, 17f, 12f, 17f)
                curveTo(11.5858f, 17f, 11.25f, 16.6642f, 11.25f, 16.25f)
                verticalLineTo(11.25f)
                curveTo(11.25f, 10.8358f, 11.5858f, 10.5f, 12f, 10.5f)
                close()
                moveTo(12f, 9f)
                curveTo(12.5523f, 9f, 13f, 8.55229f, 13f, 8f)
                curveTo(13f, 7.44772f, 12.5523f, 7f, 12f, 7f)
                curveTo(11.4477f, 7f, 11f, 7.44772f, 11f, 8f)
                curveTo(11f, 8.55229f, 11.4477f, 9f, 12f, 9f)
                close()
            }
        }.build()

        return _FluentuiSystemIconsInfo!!
    }

private var _FluentuiSystemIconsInfo: ImageVector? = null
