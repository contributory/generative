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

val FluentuiSystemIconsDesktop: ImageVector
    get() {
        if (_FluentuiSystemIconsDesktop != null) return _FluentuiSystemIconsDesktop!!

        _FluentuiSystemIconsDesktop = ImageVector.Builder(
            name = "desktop",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(6.75f, 22.0004f)
                curveTo(6.33579f, 22.0004f, 6f, 21.6647f, 6f, 21.2504f)
                curveTo(6f, 20.8707f, 6.28215f, 20.557f, 6.64823f, 20.5073f)
                lineTo(6.75f, 20.5004f)
                lineTo(8.499f, 20.5f)
                verticalLineTo(18.002f)
                lineTo(4.25f, 18.0023f)
                curveTo(3.05914f, 18.0023f, 2.08436f, 17.0771f, 2.00519f, 15.9063f)
                lineTo(2f, 15.7523f)
                verticalLineTo(5.25f)
                curveTo(2f, 4.05914f, 2.92516f, 3.08436f, 4.09595f, 3.00519f)
                lineTo(4.25f, 3f)
                horizontalLineTo(19.7488f)
                curveTo(20.9397f, 3f, 21.9145f, 3.92516f, 21.9936f, 5.09595f)
                lineTo(21.9988f, 5.25f)
                verticalLineTo(15.7523f)
                curveTo(21.9988f, 16.9431f, 21.0737f, 17.9179f, 19.9029f, 17.9971f)
                lineTo(19.7488f, 18.0023f)
                lineTo(15.499f, 18.002f)
                verticalLineTo(20.5f)
                lineTo(17.25f, 20.5004f)
                curveTo(17.6642f, 20.5004f, 18f, 20.8362f, 18f, 21.2504f)
                curveTo(18f, 21.6301f, 17.7178f, 21.9439f, 17.3518f, 21.9936f)
                lineTo(17.25f, 22.0004f)
                horizontalLineTo(6.75f)
                close()
                moveTo(13.998f, 18.002f)
                horizontalLineTo(9.998f)
                lineTo(9.999f, 20.5004f)
                horizontalLineTo(13.999f)
                lineTo(13.998f, 18.002f)
                close()
                moveTo(19.7488f, 4.5f)
                horizontalLineTo(4.25f)
                curveTo(3.8703f, 4.5f, 3.55651f, 4.78215f, 3.50685f, 5.14823f)
                lineTo(3.5f, 5.25f)
                verticalLineTo(15.7523f)
                curveTo(3.5f, 16.132f, 3.78215f, 16.4458f, 4.14823f, 16.4954f)
                lineTo(4.25f, 16.5023f)
                horizontalLineTo(19.7488f)
                curveTo(20.1285f, 16.5023f, 20.4423f, 16.2201f, 20.492f, 15.854f)
                lineTo(20.4988f, 15.7523f)
                verticalLineTo(5.25f)
                curveTo(20.4988f, 4.8703f, 20.2167f, 4.55651f, 19.8506f, 4.50685f)
                lineTo(19.7488f, 4.5f)
                close()
            }
        }.build()

        return _FluentuiSystemIconsDesktop!!
    }

private var _FluentuiSystemIconsDesktop: ImageVector? = null
