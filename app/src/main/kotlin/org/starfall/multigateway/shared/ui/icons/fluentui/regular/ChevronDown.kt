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

val FluentuiSystemIconsChevronDown: ImageVector
    get() {
        if (_FluentuiSystemIconsChevronDown != null) return _FluentuiSystemIconsChevronDown!!

        _FluentuiSystemIconsChevronDown = ImageVector.Builder(
            name = "chevron-down",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(4.21967f, 8.46967f)
                curveTo(4.51256f, 8.17678f, 4.98744f, 8.17678f, 5.28033f, 8.46967f)
                lineTo(12f, 15.1893f)
                lineTo(18.7197f, 8.46967f)
                curveTo(19.0126f, 8.17678f, 19.4874f, 8.17678f, 19.7803f, 8.46967f)
                curveTo(20.0732f, 8.76256f, 20.0732f, 9.23744f, 19.7803f, 9.53033f)
                lineTo(12.5303f, 16.7803f)
                curveTo(12.2374f, 17.0732f, 11.7626f, 17.0732f, 11.4697f, 16.7803f)
                lineTo(4.21967f, 9.53033f)
                curveTo(3.92678f, 9.23744f, 3.92678f, 8.76256f, 4.21967f, 8.46967f)
                close()
            }
        }.build()

        return _FluentuiSystemIconsChevronDown!!
    }

private var _FluentuiSystemIconsChevronDown: ImageVector? = null
