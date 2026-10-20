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

val FluentuiSystemIconsCamera: ImageVector
    get() {
        if (_FluentuiSystemIconsCamera != null) return _FluentuiSystemIconsCamera!!

        _FluentuiSystemIconsCamera = ImageVector.Builder(
            name = "camera",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(13.9247f, 2.50391f)
                curveTo(14.7225f, 2.50391f, 15.4607f, 2.92638f, 15.8647f, 3.61428f)
                lineTo(16.6793f, 5.00086f)
                horizontalLineTo(18.75f)
                curveTo(20.5449f, 5.00086f, 22f, 6.45593f, 22f, 8.25086f)
                verticalLineTo(17.7509f)
                curveTo(22f, 19.5458f, 20.5449f, 21.0009f, 18.75f, 21.0009f)
                horizontalLineTo(5.25f)
                curveTo(3.45507f, 21.0009f, 2f, 19.5458f, 2f, 17.7509f)
                verticalLineTo(8.25086f)
                curveTo(2f, 6.45593f, 3.45507f, 5.00086f, 5.25f, 5.00086f)
                horizontalLineTo(7.33042f)
                lineTo(8.205f, 3.57656f)
                curveTo(8.61425f, 2.91006f, 9.34026f, 2.50391f, 10.1224f, 2.50391f)
                horizontalLineTo(13.9247f)
                close()
                moveTo(13.9247f, 4.00391f)
                horizontalLineTo(10.1224f)
                curveTo(9.89892f, 4.00391f, 9.6892f, 4.10337f, 9.54793f, 4.2717f)
                lineTo(9.48325f, 4.36146f)
                lineTo(8.38913f, 6.14331f)
                curveTo(8.25271f, 6.36547f, 8.01071f, 6.50086f, 7.75f, 6.50086f)
                horizontalLineTo(5.25f)
                curveTo(4.2835f, 6.50086f, 3.5f, 7.28436f, 3.5f, 8.25086f)
                verticalLineTo(17.7509f)
                curveTo(3.5f, 18.7174f, 4.2835f, 19.5009f, 5.25f, 19.5009f)
                horizontalLineTo(18.75f)
                curveTo(19.7165f, 19.5009f, 20.5f, 18.7174f, 20.5f, 17.7509f)
                verticalLineTo(8.25086f)
                curveTo(20.5f, 7.28436f, 19.7165f, 6.50086f, 18.75f, 6.50086f)
                horizontalLineTo(16.25f)
                curveTo(15.9841f, 6.50086f, 15.738f, 6.36003f, 15.6033f, 6.13073f)
                lineTo(14.5714f, 4.37403f)
                curveTo(14.4367f, 4.14473f, 14.1906f, 4.00391f, 13.9247f, 4.00391f)
                close()
                moveTo(12f, 8.00086f)
                curveTo(14.4853f, 8.00086f, 16.5f, 10.0156f, 16.5f, 12.5009f)
                curveTo(16.5f, 14.9861f, 14.4853f, 17.0009f, 12f, 17.0009f)
                curveTo(9.51472f, 17.0009f, 7.5f, 14.9861f, 7.5f, 12.5009f)
                curveTo(7.5f, 10.0156f, 9.51472f, 8.00086f, 12f, 8.00086f)
                close()
                moveTo(12f, 9.50086f)
                curveTo(10.3431f, 9.50086f, 9f, 10.844f, 9f, 12.5009f)
                curveTo(9f, 14.1577f, 10.3431f, 15.5009f, 12f, 15.5009f)
                curveTo(13.6569f, 15.5009f, 15f, 14.1577f, 15f, 12.5009f)
                curveTo(15f, 10.844f, 13.6569f, 9.50086f, 12f, 9.50086f)
                close()
            }
        }.build()

        return _FluentuiSystemIconsCamera!!
    }

private var _FluentuiSystemIconsCamera: ImageVector? = null
