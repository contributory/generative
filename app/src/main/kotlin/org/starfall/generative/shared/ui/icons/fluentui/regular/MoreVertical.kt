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

val FluentuiSystemIconsMoreVertical: ImageVector
    get() {
        if (_FluentuiSystemIconsMoreVertical != null) return _FluentuiSystemIconsMoreVertical!!

        _FluentuiSystemIconsMoreVertical = ImageVector.Builder(
            name = "more-vertical",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(12f, 7.75f)
                curveTo(11.0335f, 7.75f, 10.25f, 6.9665f, 10.25f, 6f)
                curveTo(10.25f, 5.0335f, 11.0335f, 4.25f, 12f, 4.25f)
                curveTo(12.9665f, 4.25f, 13.75f, 5.0335f, 13.75f, 6f)
                curveTo(13.75f, 6.9665f, 12.9665f, 7.75f, 12f, 7.75f)
                close()
                moveTo(12f, 13.75f)
                curveTo(11.0335f, 13.75f, 10.25f, 12.9665f, 10.25f, 12f)
                curveTo(10.25f, 11.0335f, 11.0335f, 10.25f, 12f, 10.25f)
                curveTo(12.9665f, 10.25f, 13.75f, 11.0335f, 13.75f, 12f)
                curveTo(13.75f, 12.9665f, 12.9665f, 13.75f, 12f, 13.75f)
                close()
                moveTo(10.25f, 18f)
                curveTo(10.25f, 18.9665f, 11.0335f, 19.75f, 12f, 19.75f)
                curveTo(12.9665f, 19.75f, 13.75f, 18.9665f, 13.75f, 18f)
                curveTo(13.75f, 17.0335f, 12.9665f, 16.25f, 12f, 16.25f)
                curveTo(11.0335f, 16.25f, 10.25f, 17.0335f, 10.25f, 18f)
                close()
            }
        }.build()

        return _FluentuiSystemIconsMoreVertical!!
    }

private var _FluentuiSystemIconsMoreVertical: ImageVector? = null
