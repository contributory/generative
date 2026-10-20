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

val FluentuiSystemIconsChevronRight: ImageVector
    get() {
        if (_FluentuiSystemIconsChevronRight != null) return _FluentuiSystemIconsChevronRight!!

        _FluentuiSystemIconsChevronRight = ImageVector.Builder(
            name = "chevron-right",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(8.46967f, 4.21967f)
                curveTo(8.17678f, 4.51256f, 8.17678f, 4.98744f, 8.46967f, 5.28033f)
                lineTo(15.1893f, 12f)
                lineTo(8.46967f, 18.7197f)
                curveTo(8.17678f, 19.0126f, 8.17678f, 19.4874f, 8.46967f, 19.7803f)
                curveTo(8.76256f, 20.0732f, 9.23744f, 20.0732f, 9.53033f, 19.7803f)
                lineTo(16.7803f, 12.5303f)
                curveTo(17.0732f, 12.2374f, 17.0732f, 11.7626f, 16.7803f, 11.4697f)
                lineTo(9.53033f, 4.21967f)
                curveTo(9.23744f, 3.92678f, 8.76256f, 3.92678f, 8.46967f, 4.21967f)
                close()
            }
        }.build()

        return _FluentuiSystemIconsChevronRight!!
    }

private var _FluentuiSystemIconsChevronRight: ImageVector? = null
