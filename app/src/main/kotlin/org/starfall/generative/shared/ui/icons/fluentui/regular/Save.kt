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

val FluentuiSystemIconsSave: ImageVector
    get() {
        if (_FluentuiSystemIconsSave != null) return _FluentuiSystemIconsSave!!

        _FluentuiSystemIconsSave = ImageVector.Builder(
            name = "save",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(3f, 5.75f)
                curveTo(3f, 4.23122f, 4.23122f, 3f, 5.75f, 3f)
                horizontalLineTo(15.7145f)
                curveTo(16.5764f, 3f, 17.4031f, 3.34241f, 18.0126f, 3.9519f)
                lineTo(20.0481f, 5.98744f)
                curveTo(20.6576f, 6.59693f, 21f, 7.42358f, 21f, 8.28553f)
                verticalLineTo(18.25f)
                curveTo(21f, 19.7688f, 19.7688f, 21f, 18.25f, 21f)
                horizontalLineTo(5.75f)
                curveTo(4.23122f, 21f, 3f, 19.7688f, 3f, 18.25f)
                verticalLineTo(5.75f)
                close()
                moveTo(5.75f, 4.5f)
                curveTo(5.05964f, 4.5f, 4.5f, 5.05964f, 4.5f, 5.75f)
                verticalLineTo(18.25f)
                curveTo(4.5f, 18.9404f, 5.05964f, 19.5f, 5.75f, 19.5f)
                horizontalLineTo(6f)
                verticalLineTo(14.25f)
                curveTo(6f, 13.0074f, 7.00736f, 12f, 8.25f, 12f)
                horizontalLineTo(15.75f)
                curveTo(16.9926f, 12f, 18f, 13.0074f, 18f, 14.25f)
                verticalLineTo(19.5f)
                horizontalLineTo(18.25f)
                curveTo(18.9404f, 19.5f, 19.5f, 18.9404f, 19.5f, 18.25f)
                verticalLineTo(8.28553f)
                curveTo(19.5f, 7.8214f, 19.3156f, 7.37629f, 18.9874f, 7.0481f)
                lineTo(16.9519f, 5.01256f)
                curveTo(16.6918f, 4.75246f, 16.3582f, 4.58269f, 16f, 4.52344f)
                verticalLineTo(7.25f)
                curveTo(16f, 8.49264f, 14.9926f, 9.5f, 13.75f, 9.5f)
                horizontalLineTo(9.25f)
                curveTo(8.00736f, 9.5f, 7f, 8.49264f, 7f, 7.25f)
                verticalLineTo(4.5f)
                horizontalLineTo(5.75f)
                close()
                moveTo(16.5f, 19.5f)
                verticalLineTo(14.25f)
                curveTo(16.5f, 13.8358f, 16.1642f, 13.5f, 15.75f, 13.5f)
                horizontalLineTo(8.25f)
                curveTo(7.83579f, 13.5f, 7.5f, 13.8358f, 7.5f, 14.25f)
                verticalLineTo(19.5f)
                horizontalLineTo(16.5f)
                close()
                moveTo(8.5f, 4.5f)
                verticalLineTo(7.25f)
                curveTo(8.5f, 7.66421f, 8.83579f, 8f, 9.25f, 8f)
                horizontalLineTo(13.75f)
                curveTo(14.1642f, 8f, 14.5f, 7.66421f, 14.5f, 7.25f)
                verticalLineTo(4.5f)
                horizontalLineTo(8.5f)
                close()
            }
        }.build()

        return _FluentuiSystemIconsSave!!
    }

private var _FluentuiSystemIconsSave: ImageVector? = null
