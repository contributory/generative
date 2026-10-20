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

val FluentuiSystemIconsLockClosed: ImageVector
    get() {
        if (_FluentuiSystemIconsLockClosed != null) return _FluentuiSystemIconsLockClosed!!

        _FluentuiSystemIconsLockClosed = ImageVector.Builder(
            name = "lock-closed",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(12f, 1f)
                curveTo(14.7614f, 1f, 17f, 3.23858f, 17f, 6f)
                verticalLineTo(8.00977f)
                curveTo(18.6781f, 8.13743f, 20f, 9.5392f, 20f, 11.25f)
                verticalLineTo(18.75f)
                curveTo(20f, 20.5449f, 18.5449f, 22f, 16.75f, 22f)
                horizontalLineTo(7.25f)
                curveTo(5.45507f, 22f, 4f, 20.5449f, 4f, 18.75f)
                verticalLineTo(11.25f)
                curveTo(4f, 9.5392f, 5.3219f, 8.13743f, 7f, 8.00977f)
                verticalLineTo(6f)
                curveTo(7f, 3.23858f, 9.23858f, 1f, 12f, 1f)
                close()
                moveTo(7.25f, 9.5f)
                curveTo(6.2835f, 9.5f, 5.5f, 10.2835f, 5.5f, 11.25f)
                verticalLineTo(18.75f)
                curveTo(5.5f, 19.7165f, 6.2835f, 20.5f, 7.25f, 20.5f)
                horizontalLineTo(16.75f)
                curveTo(17.7165f, 20.5f, 18.5f, 19.7165f, 18.5f, 18.75f)
                verticalLineTo(11.25f)
                curveTo(18.5f, 10.2835f, 17.7165f, 9.5f, 16.75f, 9.5f)
                horizontalLineTo(7.25f)
                close()
                moveTo(12f, 13.75f)
                curveTo(12.6904f, 13.75f, 13.25f, 14.3096f, 13.25f, 15f)
                curveTo(13.25f, 15.6904f, 12.6904f, 16.25f, 12f, 16.25f)
                curveTo(11.3096f, 16.25f, 10.75f, 15.6904f, 10.75f, 15f)
                curveTo(10.75f, 14.3096f, 11.3096f, 13.75f, 12f, 13.75f)
                close()
                moveTo(12f, 2.5f)
                curveTo(10.067f, 2.5f, 8.5f, 4.067f, 8.5f, 6f)
                verticalLineTo(8f)
                horizontalLineTo(15.5f)
                verticalLineTo(6f)
                curveTo(15.5f, 4.067f, 13.933f, 2.5f, 12f, 2.5f)
                close()
            }
        }.build()

        return _FluentuiSystemIconsLockClosed!!
    }

private var _FluentuiSystemIconsLockClosed: ImageVector? = null
