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

val FluentuiSystemIconsChat: ImageVector
    get() {
        if (_FluentuiSystemIconsChat != null) return _FluentuiSystemIconsChat!!

        _FluentuiSystemIconsChat = ImageVector.Builder(
            name = "chat",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(12f, 2f)
                curveTo(17.5228f, 2f, 22f, 6.47715f, 22f, 12f)
                curveTo(22f, 17.5228f, 17.5228f, 22f, 12f, 22f)
                curveTo(10.3817f, 22f, 8.81782f, 21.6146f, 7.41286f, 20.888f)
                lineTo(3.58704f, 21.9553f)
                curveTo(2.92212f, 22.141f, 2.23258f, 21.7525f, 2.04691f, 21.0876f)
                curveTo(1.98546f, 20.8676f, 1.98549f, 20.6349f, 2.04695f, 20.4151f)
                lineTo(3.11461f, 16.5922f)
                curveTo(2.38637f, 15.186f, 2f, 13.6203f, 2f, 12f)
                curveTo(2f, 6.47715f, 6.47715f, 2f, 12f, 2f)
                close()
                moveTo(12f, 3.5f)
                curveTo(7.30558f, 3.5f, 3.5f, 7.30558f, 3.5f, 12f)
                curveTo(3.5f, 13.4696f, 3.87277f, 14.8834f, 4.57303f, 16.1375f)
                lineTo(4.72368f, 16.4072f)
                lineTo(3.61096f, 20.3914f)
                lineTo(7.59755f, 19.2792f)
                lineTo(7.86709f, 19.4295f)
                curveTo(9.12006f, 20.1281f, 10.5322f, 20.5f, 12f, 20.5f)
                curveTo(16.6944f, 20.5f, 20.5f, 16.6944f, 20.5f, 12f)
                curveTo(20.5f, 7.30558f, 16.6944f, 3.5f, 12f, 3.5f)
                close()
                moveTo(8.75f, 13f)
                horizontalLineTo(13.2483f)
                curveTo(13.6625f, 13f, 13.9983f, 13.3358f, 13.9983f, 13.75f)
                curveTo(13.9983f, 14.1297f, 13.7161f, 14.4435f, 13.35f, 14.4932f)
                lineTo(13.2483f, 14.5f)
                horizontalLineTo(8.75f)
                curveTo(8.33579f, 14.5f, 8f, 14.1642f, 8f, 13.75f)
                curveTo(8f, 13.3703f, 8.28215f, 13.0565f, 8.64823f, 13.0068f)
                lineTo(8.75f, 13f)
                horizontalLineTo(13.2483f)
                horizontalLineTo(8.75f)
                close()
                moveTo(8.75f, 9.5f)
                horizontalLineTo(15.2545f)
                curveTo(15.6687f, 9.5f, 16.0045f, 9.83579f, 16.0045f, 10.25f)
                curveTo(16.0045f, 10.6297f, 15.7223f, 10.9435f, 15.3563f, 10.9932f)
                lineTo(15.2545f, 11f)
                horizontalLineTo(8.75f)
                curveTo(8.33579f, 11f, 8f, 10.6642f, 8f, 10.25f)
                curveTo(8f, 9.8703f, 8.28215f, 9.55651f, 8.64823f, 9.50685f)
                lineTo(8.75f, 9.5f)
                horizontalLineTo(15.2545f)
                horizontalLineTo(8.75f)
                close()
            }
        }.build()

        return _FluentuiSystemIconsChat!!
    }

private var _FluentuiSystemIconsChat: ImageVector? = null
