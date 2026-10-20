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

val FluentuiSystemIconsArrowForward: ImageVector
    get() {
        if (_FluentuiSystemIconsArrowForward != null) return _FluentuiSystemIconsArrowForward!!

        _FluentuiSystemIconsArrowForward = ImageVector.Builder(
            name = "arrow-forward",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(14.7197f, 6.28033f)
                curveTo(14.4268f, 5.98744f, 14.4268f, 5.51256f, 14.7197f, 5.21967f)
                curveTo(15.0126f, 4.92678f, 15.4874f, 4.92678f, 15.7803f, 5.21967f)
                lineTo(20.7803f, 10.2197f)
                curveTo(21.0732f, 10.5126f, 21.0732f, 10.9874f, 20.7803f, 11.2803f)
                lineTo(15.7803f, 16.2803f)
                curveTo(15.4874f, 16.5732f, 15.0126f, 16.5732f, 14.7197f, 16.2803f)
                curveTo(14.4268f, 15.9874f, 14.4268f, 15.5126f, 14.7197f, 15.2197f)
                lineTo(18.4393f, 11.5f)
                horizontalLineTo(10.75f)
                curveTo(7.29822f, 11.5f, 4.5f, 14.2982f, 4.5f, 17.75f)
                verticalLineTo(18.25f)
                curveTo(4.5f, 18.6642f, 4.16421f, 19f, 3.75f, 19f)
                curveTo(3.33579f, 19f, 3f, 18.6642f, 3f, 18.25f)
                verticalLineTo(17.75f)
                curveTo(3f, 13.4698f, 6.46979f, 10f, 10.75f, 10f)
                horizontalLineTo(18.4393f)
                lineTo(14.7197f, 6.28033f)
                close()
            }
        }.build()

        return _FluentuiSystemIconsArrowForward!!
    }

private var _FluentuiSystemIconsArrowForward: ImageVector? = null
