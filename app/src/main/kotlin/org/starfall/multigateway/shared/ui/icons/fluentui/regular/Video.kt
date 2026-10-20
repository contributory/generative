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

val FluentuiSystemIconsVideo: ImageVector
    get() {
        if (_FluentuiSystemIconsVideo != null) return _FluentuiSystemIconsVideo!!

        _FluentuiSystemIconsVideo = ImageVector.Builder(
            name = "video",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(5.25f, 5f)
                curveTo(3.45507f, 5f, 2f, 6.45507f, 2f, 8.25f)
                verticalLineTo(15.75f)
                curveTo(2f, 17.5449f, 3.45507f, 19f, 5.25f, 19f)
                horizontalLineTo(12.75f)
                curveTo(14.5449f, 19f, 16f, 17.5449f, 16f, 15.75f)
                verticalLineTo(15.438f)
                lineTo(19.2575f, 17.6872f)
                curveTo(20.4183f, 18.4887f, 22.0018f, 17.6578f, 22.0018f, 16.2471f)
                verticalLineTo(7.75204f)
                curveTo(22.0018f, 6.34137f, 20.4183f, 5.51044f, 19.2575f, 6.31197f)
                lineTo(16f, 8.56118f)
                verticalLineTo(8.25f)
                curveTo(16f, 6.45507f, 14.5449f, 5f, 12.75f, 5f)
                horizontalLineTo(5.25f)
                close()
                moveTo(16f, 10.384f)
                lineTo(20.1098f, 7.54632f)
                curveTo(20.2756f, 7.43181f, 20.5018f, 7.55051f, 20.5018f, 7.75204f)
                verticalLineTo(16.2471f)
                curveTo(20.5018f, 16.4486f, 20.2756f, 16.5673f, 20.1098f, 16.4528f)
                lineTo(16f, 13.6151f)
                verticalLineTo(10.384f)
                close()
                moveTo(3.5f, 8.25f)
                curveTo(3.5f, 7.2835f, 4.2835f, 6.5f, 5.25f, 6.5f)
                horizontalLineTo(12.75f)
                curveTo(13.7165f, 6.5f, 14.5f, 7.2835f, 14.5f, 8.25f)
                verticalLineTo(15.75f)
                curveTo(14.5f, 16.7165f, 13.7165f, 17.5f, 12.75f, 17.5f)
                horizontalLineTo(5.25f)
                curveTo(4.2835f, 17.5f, 3.5f, 16.7165f, 3.5f, 15.75f)
                verticalLineTo(8.25f)
                close()
            }
        }.build()

        return _FluentuiSystemIconsVideo!!
    }

private var _FluentuiSystemIconsVideo: ImageVector? = null
