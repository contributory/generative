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

val FluentuiSystemIconsArrowDown: ImageVector
    get() {
        if (_FluentuiSystemIconsArrowDown != null) return _FluentuiSystemIconsArrowDown!!

        _FluentuiSystemIconsArrowDown = ImageVector.Builder(
            name = "arrow-down",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(19.7905f, 13.2673f)
                curveTo(20.0762f, 12.9674f, 20.0646f, 12.4926f, 19.7647f, 12.2069f)
                curveTo(19.4648f, 11.9212f, 18.99f, 11.9328f, 18.7043f, 12.2327f)
                lineTo(12.7498f, 18.484f)
                verticalLineTo(3.75f)
                curveTo(12.7498f, 3.33579f, 12.4141f, 3f, 11.9998f, 3f)
                curveTo(11.5856f, 3f, 11.2498f, 3.33579f, 11.2498f, 3.75f)
                verticalLineTo(18.4844f)
                lineTo(5.29502f, 12.2327f)
                curveTo(5.00933f, 11.9328f, 4.5346f, 11.9212f, 4.23467f, 12.2069f)
                curveTo(3.93475f, 12.4926f, 3.9232f, 12.9674f, 4.20889f, 13.2673f)
                lineTo(11.2756f, 20.6862f)
                curveTo(11.4366f, 20.8553f, 11.642f, 20.9552f, 11.8556f, 20.9861f)
                curveTo(11.9023f, 20.9952f, 11.9505f, 21f, 11.9998f, 21f)
                curveTo(12.0495f, 21f, 12.0981f, 20.9952f, 12.1451f, 20.9859f)
                curveTo(12.3582f, 20.9548f, 12.5631f, 20.8549f, 12.7238f, 20.6862f)
                lineTo(19.7905f, 13.2673f)
                close()
            }
        }.build()

        return _FluentuiSystemIconsArrowDown!!
    }

private var _FluentuiSystemIconsArrowDown: ImageVector? = null
