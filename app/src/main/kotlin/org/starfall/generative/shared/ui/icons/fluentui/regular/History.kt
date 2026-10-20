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

val FluentuiSystemIconsHistory: ImageVector
    get() {
        if (_FluentuiSystemIconsHistory != null) return _FluentuiSystemIconsHistory!!

        _FluentuiSystemIconsHistory = ImageVector.Builder(
            name = "history",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(12f, 4.5f)
                curveTo(16.1421f, 4.5f, 19.5f, 7.85786f, 19.5f, 12f)
                curveTo(19.5f, 16.1421f, 16.1421f, 19.5f, 12f, 19.5f)
                curveTo(7.85786f, 19.5f, 4.5f, 16.1421f, 4.5f, 12f)
                curveTo(4.5f, 11.6236f, 4.52772f, 11.2538f, 4.58123f, 10.8923f)
                curveTo(4.64845f, 10.4382f, 4.31609f, 10f, 3.85708f, 10f)
                curveTo(3.48623f, 10f, 3.161f, 10.2562f, 3.10471f, 10.6228f)
                curveTo(3.03576f, 11.0718f, 3f, 11.5317f, 3f, 12f)
                curveTo(3f, 16.9706f, 7.02944f, 21f, 12f, 21f)
                curveTo(16.9706f, 21f, 21f, 16.9706f, 21f, 12f)
                curveTo(21f, 7.02944f, 16.9706f, 3f, 12f, 3f)
                curveTo(9.69494f, 3f, 7.59227f, 3.86656f, 6f, 5.29168f)
                verticalLineTo(4.25f)
                curveTo(6f, 3.83579f, 5.66421f, 3.5f, 5.25f, 3.5f)
                curveTo(4.83579f, 3.5f, 4.5f, 3.83579f, 4.5f, 4.25f)
                verticalLineTo(7.25f)
                curveTo(4.5f, 7.66421f, 4.83579f, 8f, 5.25f, 8f)
                horizontalLineTo(8.25f)
                curveTo(8.66421f, 8f, 9f, 7.66421f, 9f, 7.25f)
                curveTo(9f, 6.83579f, 8.66421f, 6.5f, 8.25f, 6.5f)
                horizontalLineTo(6.90093f)
                curveTo(8.23907f, 5.25883f, 10.0309f, 4.5f, 12f, 4.5f)
                close()
                moveTo(12.5f, 7.75f)
                curveTo(12.5f, 7.33579f, 12.1642f, 7f, 11.75f, 7f)
                curveTo(11.3358f, 7f, 11f, 7.33579f, 11f, 7.75f)
                verticalLineTo(12.25f)
                curveTo(11f, 12.6642f, 11.3358f, 13f, 11.75f, 13f)
                horizontalLineTo(14.75f)
                curveTo(15.1642f, 13f, 15.5f, 12.6642f, 15.5f, 12.25f)
                curveTo(15.5f, 11.8358f, 15.1642f, 11.5f, 14.75f, 11.5f)
                horizontalLineTo(12.5f)
                verticalLineTo(7.75f)
                close()
            }
        }.build()

        return _FluentuiSystemIconsHistory!!
    }

private var _FluentuiSystemIconsHistory: ImageVector? = null
