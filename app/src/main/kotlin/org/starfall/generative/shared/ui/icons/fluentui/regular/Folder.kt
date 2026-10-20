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

val FluentuiSystemIconsFolder: ImageVector
    get() {
        if (_FluentuiSystemIconsFolder != null) return _FluentuiSystemIconsFolder!!

        _FluentuiSystemIconsFolder = ImageVector.Builder(
            name = "folder",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(3.5f, 6.25f)
                verticalLineTo(8f)
                horizontalLineTo(8.12868f)
                curveTo(8.32759f, 8f, 8.51836f, 7.92098f, 8.65901f, 7.78033f)
                lineTo(10.1893f, 6.25f)
                lineTo(8.65901f, 4.71967f)
                curveTo(8.51836f, 4.57902f, 8.32759f, 4.5f, 8.12868f, 4.5f)
                horizontalLineTo(5.25f)
                curveTo(4.2835f, 4.5f, 3.5f, 5.2835f, 3.5f, 6.25f)
                close()
                moveTo(2f, 6.25f)
                curveTo(2f, 4.45507f, 3.45507f, 3f, 5.25f, 3f)
                horizontalLineTo(8.12868f)
                curveTo(8.72542f, 3f, 9.29771f, 3.23705f, 9.71967f, 3.65901f)
                lineTo(11.5607f, 5.5f)
                horizontalLineTo(18.75f)
                curveTo(20.5449f, 5.5f, 22f, 6.95507f, 22f, 8.75f)
                verticalLineTo(17.75f)
                curveTo(22f, 19.5449f, 20.5449f, 21f, 18.75f, 21f)
                horizontalLineTo(5.25f)
                curveTo(3.45507f, 21f, 2f, 19.5449f, 2f, 17.75f)
                verticalLineTo(6.25f)
                close()
                moveTo(3.5f, 9.5f)
                verticalLineTo(17.75f)
                curveTo(3.5f, 18.7165f, 4.2835f, 19.5f, 5.25f, 19.5f)
                horizontalLineTo(18.75f)
                curveTo(19.7165f, 19.5f, 20.5f, 18.7165f, 20.5f, 17.75f)
                verticalLineTo(8.75f)
                curveTo(20.5f, 7.7835f, 19.7165f, 7f, 18.75f, 7f)
                horizontalLineTo(11.5607f)
                lineTo(9.71967f, 8.84099f)
                curveTo(9.29771f, 9.26295f, 8.72542f, 9.5f, 8.12868f, 9.5f)
                horizontalLineTo(3.5f)
                close()
            }
        }.build()

        return _FluentuiSystemIconsFolder!!
    }

private var _FluentuiSystemIconsFolder: ImageVector? = null
