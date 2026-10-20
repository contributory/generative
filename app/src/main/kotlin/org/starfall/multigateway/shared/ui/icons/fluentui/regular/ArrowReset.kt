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

val FluentuiSystemIconsArrowReset: ImageVector
    get() {
        if (_FluentuiSystemIconsArrowReset != null) return _FluentuiSystemIconsArrowReset!!

        _FluentuiSystemIconsArrowReset = ImageVector.Builder(
            name = "arrow-reset",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(6.78033f, 2.71967f)
                curveTo(7.07322f, 3.01256f, 7.07322f, 3.48744f, 6.78033f, 3.78033f)
                lineTo(4.56066f, 6f)
                horizontalLineTo(13.25f)
                curveTo(17.5302f, 6f, 21f, 9.46979f, 21f, 13.75f)
                curveTo(21f, 18.0302f, 17.5302f, 21.5f, 13.25f, 21.5f)
                curveTo(8.96979f, 21.5f, 5.5f, 18.0302f, 5.5f, 13.75f)
                curveTo(5.5f, 13.3358f, 5.83579f, 13f, 6.25f, 13f)
                curveTo(6.66421f, 13f, 7f, 13.3358f, 7f, 13.75f)
                curveTo(7f, 17.2018f, 9.79822f, 20f, 13.25f, 20f)
                curveTo(16.7018f, 20f, 19.5f, 17.2018f, 19.5f, 13.75f)
                curveTo(19.5f, 10.2982f, 16.7018f, 7.5f, 13.25f, 7.5f)
                horizontalLineTo(4.56066f)
                lineTo(6.78033f, 9.71967f)
                curveTo(7.07322f, 10.0126f, 7.07322f, 10.4874f, 6.78033f, 10.7803f)
                curveTo(6.48744f, 11.0732f, 6.01256f, 11.0732f, 5.71967f, 10.7803f)
                lineTo(2.21967f, 7.28033f)
                curveTo(1.92678f, 6.98744f, 1.92678f, 6.51256f, 2.21967f, 6.21967f)
                lineTo(5.71967f, 2.71967f)
                curveTo(6.01256f, 2.42678f, 6.48744f, 2.42678f, 6.78033f, 2.71967f)
                close()
            }
        }.build()

        return _FluentuiSystemIconsArrowReset!!
    }

private var _FluentuiSystemIconsArrowReset: ImageVector? = null
