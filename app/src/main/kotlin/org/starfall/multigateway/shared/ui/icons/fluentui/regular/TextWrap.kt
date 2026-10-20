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

val FluentuiSystemIconsTextWrap: ImageVector
    get() {
        if (_FluentuiSystemIconsTextWrap != null) return _FluentuiSystemIconsTextWrap!!

        _FluentuiSystemIconsTextWrap = ImageVector.Builder(
            name = "text-wrap",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(2.75f, 5f)
                curveTo(2.33579f, 5f, 2f, 5.33579f, 2f, 5.75f)
                curveTo(2f, 6.16421f, 2.33579f, 6.5f, 2.75f, 6.5f)
                horizontalLineTo(21.25f)
                curveTo(21.6642f, 6.5f, 22f, 6.16421f, 22f, 5.75f)
                curveTo(22f, 5.33579f, 21.6642f, 5f, 21.25f, 5f)
                horizontalLineTo(2.75f)
                close()
                moveTo(2.75f, 11.5f)
                curveTo(2.33579f, 11.5f, 2f, 11.8358f, 2f, 12.25f)
                curveTo(2f, 12.6642f, 2.33579f, 13f, 2.75f, 13f)
                horizontalLineTo(19f)
                curveTo(20.3807f, 13f, 21.5f, 14.1193f, 21.5f, 15.5f)
                curveTo(21.5f, 16.8807f, 20.3807f, 18f, 19f, 18f)
                horizontalLineTo(14.5607f)
                lineTo(15.2803f, 17.2803f)
                curveTo(15.5732f, 16.9874f, 15.5732f, 16.5126f, 15.2803f, 16.2197f)
                curveTo(14.9874f, 15.9268f, 14.5126f, 15.9268f, 14.2197f, 16.2197f)
                lineTo(12.2197f, 18.2197f)
                curveTo(11.9268f, 18.5126f, 11.9268f, 18.9874f, 12.2197f, 19.2803f)
                lineTo(14.2197f, 21.2803f)
                curveTo(14.5126f, 21.5732f, 14.9874f, 21.5732f, 15.2803f, 21.2803f)
                curveTo(15.5732f, 20.9874f, 15.5732f, 20.5126f, 15.2803f, 20.2197f)
                lineTo(14.5607f, 19.5f)
                horizontalLineTo(19f)
                curveTo(21.2091f, 19.5f, 23f, 17.7091f, 23f, 15.5f)
                curveTo(23f, 13.2909f, 21.2091f, 11.5f, 19f, 11.5f)
                horizontalLineTo(2.75f)
                close()
                moveTo(2f, 18.75f)
                curveTo(2f, 18.3358f, 2.33579f, 18f, 2.75f, 18f)
                horizontalLineTo(9.25f)
                curveTo(9.66421f, 18f, 10f, 18.3358f, 10f, 18.75f)
                curveTo(10f, 19.1642f, 9.66421f, 19.5f, 9.25f, 19.5f)
                horizontalLineTo(2.75f)
                curveTo(2.33579f, 19.5f, 2f, 19.1642f, 2f, 18.75f)
                close()
            }
        }.build()

        return _FluentuiSystemIconsTextWrap!!
    }

private var _FluentuiSystemIconsTextWrap: ImageVector? = null
