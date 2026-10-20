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

val FluentuiSystemIconsAdd: ImageVector
    get() {
        if (_FluentuiSystemIconsAdd != null) return _FluentuiSystemIconsAdd!!

        _FluentuiSystemIconsAdd = ImageVector.Builder(
            name = "add",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(12f, 3.25f)
                curveTo(12.4142f, 3.25f, 12.75f, 3.58579f, 12.75f, 4f)
                verticalLineTo(11.25f)
                horizontalLineTo(20f)
                curveTo(20.4142f, 11.25f, 20.75f, 11.5858f, 20.75f, 12f)
                curveTo(20.75f, 12.4142f, 20.4142f, 12.75f, 20f, 12.75f)
                horizontalLineTo(12.75f)
                verticalLineTo(20f)
                curveTo(12.75f, 20.4142f, 12.4142f, 20.75f, 12f, 20.75f)
                curveTo(11.5858f, 20.75f, 11.25f, 20.4142f, 11.25f, 20f)
                verticalLineTo(12.75f)
                horizontalLineTo(4f)
                curveTo(3.58579f, 12.75f, 3.25f, 12.4142f, 3.25f, 12f)
                curveTo(3.25f, 11.5858f, 3.58579f, 11.25f, 4f, 11.25f)
                horizontalLineTo(11.25f)
                verticalLineTo(4f)
                curveTo(11.25f, 3.58579f, 11.5858f, 3.25f, 12f, 3.25f)
                close()
            }
        }.build()

        return _FluentuiSystemIconsAdd!!
    }

private var _FluentuiSystemIconsAdd: ImageVector? = null
