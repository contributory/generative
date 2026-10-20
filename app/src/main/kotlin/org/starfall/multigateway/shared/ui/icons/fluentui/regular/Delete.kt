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

val FluentuiSystemIconsDelete: ImageVector
    get() {
        if (_FluentuiSystemIconsDelete != null) return _FluentuiSystemIconsDelete!!

        _FluentuiSystemIconsDelete = ImageVector.Builder(
            name = "delete",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(10f, 5f)
                horizontalLineTo(14f)
                curveTo(14f, 3.89543f, 13.1046f, 3f, 12f, 3f)
                curveTo(10.8954f, 3f, 10f, 3.89543f, 10f, 5f)
                close()
                moveTo(8.5f, 5f)
                curveTo(8.5f, 3.067f, 10.067f, 1.5f, 12f, 1.5f)
                curveTo(13.933f, 1.5f, 15.5f, 3.067f, 15.5f, 5f)
                horizontalLineTo(21.25f)
                curveTo(21.6642f, 5f, 22f, 5.33579f, 22f, 5.75f)
                curveTo(22f, 6.16421f, 21.6642f, 6.5f, 21.25f, 6.5f)
                horizontalLineTo(19.9309f)
                lineTo(18.7589f, 18.6112f)
                curveTo(18.5729f, 20.5334f, 16.9575f, 22f, 15.0263f, 22f)
                horizontalLineTo(8.97369f)
                curveTo(7.04254f, 22f, 5.42715f, 20.5334f, 5.24113f, 18.6112f)
                lineTo(4.06908f, 6.5f)
                horizontalLineTo(2.75f)
                curveTo(2.33579f, 6.5f, 2f, 6.16421f, 2f, 5.75f)
                curveTo(2f, 5.33579f, 2.33579f, 5f, 2.75f, 5f)
                horizontalLineTo(8.5f)
                close()
                moveTo(10.5f, 9.75f)
                curveTo(10.5f, 9.33579f, 10.1642f, 9f, 9.75f, 9f)
                curveTo(9.33579f, 9f, 9f, 9.33579f, 9f, 9.75f)
                verticalLineTo(17.25f)
                curveTo(9f, 17.6642f, 9.33579f, 18f, 9.75f, 18f)
                curveTo(10.1642f, 18f, 10.5f, 17.6642f, 10.5f, 17.25f)
                verticalLineTo(9.75f)
                close()
                moveTo(14.25f, 9f)
                curveTo(14.6642f, 9f, 15f, 9.33579f, 15f, 9.75f)
                verticalLineTo(17.25f)
                curveTo(15f, 17.6642f, 14.6642f, 18f, 14.25f, 18f)
                curveTo(13.8358f, 18f, 13.5f, 17.6642f, 13.5f, 17.25f)
                verticalLineTo(9.75f)
                curveTo(13.5f, 9.33579f, 13.8358f, 9f, 14.25f, 9f)
                close()
                moveTo(6.73416f, 18.4667f)
                curveTo(6.84577f, 19.62f, 7.815f, 20.5f, 8.97369f, 20.5f)
                horizontalLineTo(15.0263f)
                curveTo(16.185f, 20.5f, 17.1542f, 19.62f, 17.2658f, 18.4667f)
                lineTo(18.4239f, 6.5f)
                horizontalLineTo(5.57608f)
                lineTo(6.73416f, 18.4667f)
                close()
            }
        }.build()

        return _FluentuiSystemIconsDelete!!
    }

private var _FluentuiSystemIconsDelete: ImageVector? = null
