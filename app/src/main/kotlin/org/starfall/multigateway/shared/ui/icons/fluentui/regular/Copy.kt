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

val FluentuiSystemIconsCopy: ImageVector
    get() {
        if (_FluentuiSystemIconsCopy != null) return _FluentuiSystemIconsCopy!!

        _FluentuiSystemIconsCopy = ImageVector.Builder(
            name = "copy",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(5.5028f, 4.62704f)
                lineTo(5.5f, 6.75f)
                verticalLineTo(17.2542f)
                curveTo(5.5f, 19.0491f, 6.95507f, 20.5042f, 8.75f, 20.5042f)
                lineTo(17.3663f, 20.5045f)
                curveTo(17.0573f, 21.3782f, 16.224f, 22.0042f, 15.2444f, 22.0042f)
                horizontalLineTo(8.75f)
                curveTo(6.12665f, 22.0042f, 4f, 19.8776f, 4f, 17.2542f)
                verticalLineTo(6.75f)
                curveTo(4f, 5.76929f, 4.62745f, 4.93512f, 5.5028f, 4.62704f)
                close()
                moveTo(17.75f, 2f)
                curveTo(18.9926f, 2f, 20f, 3.00736f, 20f, 4.25f)
                verticalLineTo(17.25f)
                curveTo(20f, 18.4926f, 18.9926f, 19.5f, 17.75f, 19.5f)
                horizontalLineTo(8.75f)
                curveTo(7.50736f, 19.5f, 6.5f, 18.4926f, 6.5f, 17.25f)
                verticalLineTo(4.25f)
                curveTo(6.5f, 3.00736f, 7.50736f, 2f, 8.75f, 2f)
                horizontalLineTo(17.75f)
                close()
                moveTo(17.75f, 3.5f)
                horizontalLineTo(8.75f)
                curveTo(8.33579f, 3.5f, 8f, 3.83579f, 8f, 4.25f)
                verticalLineTo(17.25f)
                curveTo(8f, 17.6642f, 8.33579f, 18f, 8.75f, 18f)
                horizontalLineTo(17.75f)
                curveTo(18.1642f, 18f, 18.5f, 17.6642f, 18.5f, 17.25f)
                verticalLineTo(4.25f)
                curveTo(18.5f, 3.83579f, 18.1642f, 3.5f, 17.75f, 3.5f)
                close()
            }
        }.build()

        return _FluentuiSystemIconsCopy!!
    }

private var _FluentuiSystemIconsCopy: ImageVector? = null
