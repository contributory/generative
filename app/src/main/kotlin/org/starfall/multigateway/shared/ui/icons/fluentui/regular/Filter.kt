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

val FluentuiSystemIconsFilter: ImageVector
    get() {
        if (_FluentuiSystemIconsFilter != null) return _FluentuiSystemIconsFilter!!

        _FluentuiSystemIconsFilter = ImageVector.Builder(
            name = "filter",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(13.5f, 16f)
                curveTo(13.9142f, 16f, 14.25f, 16.3358f, 14.25f, 16.75f)
                curveTo(14.25f, 17.1642f, 13.9142f, 17.5f, 13.5f, 17.5f)
                horizontalLineTo(10.5f)
                curveTo(10.0858f, 17.5f, 9.75f, 17.1642f, 9.75f, 16.75f)
                curveTo(9.75f, 16.3358f, 10.0858f, 16f, 10.5f, 16f)
                horizontalLineTo(13.5f)
                close()
                moveTo(16.5f, 11f)
                curveTo(16.9142f, 11f, 17.25f, 11.3358f, 17.25f, 11.75f)
                curveTo(17.25f, 12.1642f, 16.9142f, 12.5f, 16.5f, 12.5f)
                horizontalLineTo(7.5f)
                curveTo(7.08579f, 12.5f, 6.75f, 12.1642f, 6.75f, 11.75f)
                curveTo(6.75f, 11.3358f, 7.08579f, 11f, 7.5f, 11f)
                horizontalLineTo(16.5f)
                close()
                moveTo(19.5f, 6f)
                curveTo(19.9142f, 6f, 20.25f, 6.33579f, 20.25f, 6.75f)
                curveTo(20.25f, 7.16421f, 19.9142f, 7.5f, 19.5f, 7.5f)
                horizontalLineTo(4.5f)
                curveTo(4.08579f, 7.5f, 3.75f, 7.16421f, 3.75f, 6.75f)
                curveTo(3.75f, 6.33579f, 4.08579f, 6f, 4.5f, 6f)
                horizontalLineTo(19.5f)
                close()
            }
        }.build()

        return _FluentuiSystemIconsFilter!!
    }

private var _FluentuiSystemIconsFilter: ImageVector? = null
