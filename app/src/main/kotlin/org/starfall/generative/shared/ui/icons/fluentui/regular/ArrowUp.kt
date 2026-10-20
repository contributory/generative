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

val FluentuiSystemIconsArrowUp: ImageVector
    get() {
        if (_FluentuiSystemIconsArrowUp != null) return _FluentuiSystemIconsArrowUp!!

        _FluentuiSystemIconsArrowUp = ImageVector.Builder(
            name = "arrow-up",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(4.20889f, 10.7327f)
                curveTo(3.9232f, 11.0326f, 3.93475f, 11.5074f, 4.23467f, 11.7931f)
                curveTo(4.5346f, 12.0788f, 5.00933f, 12.0672f, 5.29502f, 11.7673f)
                lineTo(11.2495f, 5.516f)
                verticalLineTo(20.25f)
                curveTo(11.2495f, 20.6642f, 11.5853f, 21f, 11.9995f, 21f)
                curveTo(12.4137f, 21f, 12.7495f, 20.6642f, 12.7495f, 20.25f)
                verticalLineTo(5.51565f)
                lineTo(18.7043f, 11.7673f)
                curveTo(18.99f, 12.0672f, 19.4648f, 12.0788f, 19.7647f, 11.7931f)
                curveTo(20.0646f, 11.5074f, 20.0762f, 11.0326f, 19.7905f, 10.7327f)
                lineTo(12.7238f, 3.31379f)
                curveTo(12.5627f, 3.14474f, 12.3573f, 3.04477f, 12.1438f, 3.01386f)
                curveTo(12.0971f, 3.00477f, 12.0489f, 3f, 11.9995f, 3f)
                curveTo(11.9498f, 3f, 11.9012f, 3.00483f, 11.8543f, 3.01406f)
                curveTo(11.6412f, 3.04518f, 11.4363f, 3.14509f, 11.2756f, 3.31379f)
                lineTo(4.20889f, 10.7327f)
                close()
            }
        }.build()

        return _FluentuiSystemIconsArrowUp!!
    }

private var _FluentuiSystemIconsArrowUp: ImageVector? = null
