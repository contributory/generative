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

val FluentuiSystemIconsFolderOpen: ImageVector
    get() {
        if (_FluentuiSystemIconsFolderOpen != null) return _FluentuiSystemIconsFolderOpen!!

        _FluentuiSystemIconsFolderOpen = ImageVector.Builder(
            name = "folder-open",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(3.50014f, 6.25f)
                curveTo(3.50014f, 5.2835f, 4.28364f, 4.5f, 5.25014f, 4.5f)
                horizontalLineTo(8.12882f)
                curveTo(8.32773f, 4.5f, 8.5185f, 4.57902f, 8.65915f, 4.71967f)
                lineTo(10.7198f, 6.78033f)
                curveTo(10.8605f, 6.92098f, 11.0512f, 7f, 11.2501f, 7f)
                horizontalLineTo(16.7501f)
                curveTo(17.7166f, 7f, 18.5001f, 7.7835f, 18.5001f, 8.75f)
                curveTo(18.5001f, 8.83602f, 18.5146f, 8.91866f, 18.5413f, 8.99561f)
                lineTo(8.71867f, 8.99561f)
                curveTo(7.37893f, 8.99561f, 6.14095f, 9.71035f, 5.47108f, 10.8706f)
                lineTo(3.50014f, 14.2844f)
                verticalLineTo(6.25f)
                close()
                moveTo(2.00036f, 17.7883f)
                curveTo(2.02089f, 19.5656f, 3.468f, 21f, 5.25014f, 21f)
                horizontalLineTo(11.0001f)
                curveTo(11.0292f, 21f, 11.058f, 20.9983f, 11.0862f, 20.9951f)
                horizontalLineTo(16.2812f)
                curveTo(17.6209f, 20.9951f, 18.8589f, 20.2804f, 19.5288f, 19.1201f)
                lineTo(22.5596f, 13.8706f)
                curveTo(23.7748f, 11.7657f, 22.3337f, 9.14964f, 19.9567f, 9.00214f)
                curveTo(19.9848f, 8.92334f, 20.0001f, 8.83846f, 20.0001f, 8.75f)
                curveTo(20.0001f, 6.95507f, 18.5451f, 5.5f, 16.7501f, 5.5f)
                horizontalLineTo(11.5608f)
                lineTo(9.71981f, 3.65901f)
                curveTo(9.29786f, 3.23705f, 8.72556f, 3f, 8.12882f, 3f)
                horizontalLineTo(5.25014f)
                curveTo(3.45522f, 3f, 2.00014f, 4.45507f, 2.00014f, 6.25f)
                verticalLineTo(17.71f)
                curveTo(1.9999f, 17.7362f, 1.99997f, 17.7623f, 2.00036f, 17.7883f)
                close()
                moveTo(8.71867f, 10.4956f)
                lineTo(19.745f, 10.4956f)
                curveTo(21.0921f, 10.4956f, 21.9341f, 11.9539f, 21.2605f, 13.1206f)
                lineTo(18.2297f, 18.3701f)
                curveTo(17.8278f, 19.0663f, 17.085f, 19.4951f, 16.2812f, 19.4951f)
                horizontalLineTo(5.25485f)
                curveTo(3.9077f, 19.4951f, 3.06573f, 18.0368f, 3.7393f, 16.8701f)
                lineTo(6.77011f, 11.6206f)
                curveTo(7.17204f, 10.9245f, 7.91482f, 10.4956f, 8.71867f, 10.4956f)
                close()
            }
        }.build()

        return _FluentuiSystemIconsFolderOpen!!
    }

private var _FluentuiSystemIconsFolderOpen: ImageVector? = null
