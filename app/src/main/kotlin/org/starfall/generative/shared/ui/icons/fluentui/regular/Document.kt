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

val FluentuiSystemIconsDocument: ImageVector
    get() {
        if (_FluentuiSystemIconsDocument != null) return _FluentuiSystemIconsDocument!!

        _FluentuiSystemIconsDocument = ImageVector.Builder(
            name = "document",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(6f, 2f)
                curveTo(4.89543f, 2f, 4f, 2.89543f, 4f, 4f)
                verticalLineTo(20f)
                curveTo(4f, 21.1046f, 4.89543f, 22f, 6f, 22f)
                horizontalLineTo(18f)
                curveTo(19.1046f, 22f, 20f, 21.1046f, 20f, 20f)
                verticalLineTo(9.82777f)
                curveTo(20f, 9.29733f, 19.7893f, 8.78863f, 19.4142f, 8.41355f)
                lineTo(13.5864f, 2.58579f)
                curveTo(13.2114f, 2.21071f, 12.7027f, 2f, 12.1722f, 2f)
                horizontalLineTo(6f)
                close()
                moveTo(5.5f, 4f)
                curveTo(5.5f, 3.72386f, 5.72386f, 3.5f, 6f, 3.5f)
                horizontalLineTo(12f)
                verticalLineTo(8f)
                curveTo(12f, 9.10457f, 12.8954f, 10f, 14f, 10f)
                horizontalLineTo(18.5f)
                verticalLineTo(20f)
                curveTo(18.5f, 20.2761f, 18.2761f, 20.5f, 18f, 20.5f)
                horizontalLineTo(6f)
                curveTo(5.72386f, 20.5f, 5.5f, 20.2761f, 5.5f, 20f)
                verticalLineTo(4f)
                close()
                moveTo(17.3793f, 8.5f)
                horizontalLineTo(14f)
                curveTo(13.7239f, 8.5f, 13.5f, 8.27614f, 13.5f, 8f)
                verticalLineTo(4.62066f)
                lineTo(17.3793f, 8.5f)
                close()
            }
        }.build()

        return _FluentuiSystemIconsDocument!!
    }

private var _FluentuiSystemIconsDocument: ImageVector? = null
