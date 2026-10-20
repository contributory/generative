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

val FluentuiSystemIconsCloud: ImageVector
    get() {
        if (_FluentuiSystemIconsCloud != null) return _FluentuiSystemIconsCloud!!

        _FluentuiSystemIconsCloud = ImageVector.Builder(
            name = "cloud",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(12f, 5.5f)
                curveTo(9.58676f, 5.5f, 7.61684f, 7.39994f, 7.50502f, 9.78512f)
                curveTo(7.48627f, 10.1852f, 7.15641f, 10.5f, 6.75585f, 10.5f)
                horizontalLineTo(6.5f)
                curveTo(4.84315f, 10.5f, 3.5f, 11.8431f, 3.5f, 13.5f)
                curveTo(3.5f, 15.1569f, 4.84315f, 16.5f, 6.5f, 16.5f)
                horizontalLineTo(17.5f)
                curveTo(19.1569f, 16.5f, 20.5f, 15.1569f, 20.5f, 13.5f)
                curveTo(20.5f, 11.8431f, 19.1569f, 10.5f, 17.5f, 10.5f)
                horizontalLineTo(17.2442f)
                curveTo(16.8436f, 10.5f, 16.5137f, 10.1852f, 16.495f, 9.78512f)
                curveTo(16.3832f, 7.39994f, 14.4132f, 5.5f, 12f, 5.5f)
                close()
                moveTo(6.07981f, 9.01936f)
                curveTo(6.54808f, 6.17188f, 9.02022f, 4f, 12f, 4f)
                curveTo(14.9798f, 4f, 17.4519f, 6.17189f, 17.9202f, 9.01936f)
                curveTo(20.2085f, 9.23125f, 22f, 11.1564f, 22f, 13.5f)
                curveTo(22f, 15.9853f, 19.9853f, 18f, 17.5f, 18f)
                horizontalLineTo(6.5f)
                curveTo(4.01472f, 18f, 2f, 15.9853f, 2f, 13.5f)
                curveTo(2f, 11.1564f, 3.79155f, 9.23125f, 6.07981f, 9.01936f)
                close()
            }
        }.build()

        return _FluentuiSystemIconsCloud!!
    }

private var _FluentuiSystemIconsCloud: ImageVector? = null
