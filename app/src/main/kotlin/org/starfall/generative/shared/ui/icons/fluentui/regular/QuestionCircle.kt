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

val FluentuiSystemIconsQuestionCircle: ImageVector
    get() {
        if (_FluentuiSystemIconsQuestionCircle != null) return _FluentuiSystemIconsQuestionCircle!!

        _FluentuiSystemIconsQuestionCircle = ImageVector.Builder(
            name = "question-circle",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(12f, 2f)
                curveTo(17.523f, 2f, 22f, 6.478f, 22f, 12f)
                curveTo(22f, 17.522f, 17.523f, 22f, 12f, 22f)
                curveTo(6.477f, 22f, 2f, 17.522f, 2f, 12f)
                curveTo(2f, 6.478f, 6.477f, 2f, 12f, 2f)
                close()
                moveTo(12f, 3.667f)
                curveTo(7.405f, 3.667f, 3.667f, 7.405f, 3.667f, 12f)
                curveTo(3.667f, 16.595f, 7.405f, 20.333f, 12f, 20.333f)
                curveTo(16.595f, 20.333f, 20.333f, 16.595f, 20.333f, 12f)
                curveTo(20.333f, 7.405f, 16.595f, 3.667f, 12f, 3.667f)
                close()
                moveTo(12f, 15.5f)
                curveTo(12.5523f, 15.5f, 13f, 15.9477f, 13f, 16.5f)
                curveTo(13f, 17.0523f, 12.5523f, 17.5f, 12f, 17.5f)
                curveTo(11.4477f, 17.5f, 11f, 17.0523f, 11f, 16.5f)
                curveTo(11f, 15.9477f, 11.4477f, 15.5f, 12f, 15.5f)
                close()
                moveTo(12f, 6.75f)
                curveTo(13.5188f, 6.75f, 14.75f, 7.98122f, 14.75f, 9.5f)
                curveTo(14.75f, 10.5108f, 14.4525f, 11.074f, 13.6989f, 11.8586f)
                lineTo(13.5303f, 12.0303f)
                curveTo(12.9084f, 12.6522f, 12.75f, 12.9163f, 12.75f, 13.5f)
                curveTo(12.75f, 13.9142f, 12.4142f, 14.25f, 12f, 14.25f)
                curveTo(11.5858f, 14.25f, 11.25f, 13.9142f, 11.25f, 13.5f)
                curveTo(11.25f, 12.4892f, 11.5475f, 11.926f, 12.3011f, 11.1414f)
                lineTo(12.4697f, 10.9697f)
                curveTo(13.0916f, 10.3478f, 13.25f, 10.0837f, 13.25f, 9.5f)
                curveTo(13.25f, 8.80964f, 12.6904f, 8.25f, 12f, 8.25f)
                curveTo(11.3528f, 8.25f, 10.8205f, 8.74187f, 10.7565f, 9.37219f)
                lineTo(10.75f, 9.5f)
                curveTo(10.75f, 9.91421f, 10.4142f, 10.25f, 10f, 10.25f)
                curveTo(9.58579f, 10.25f, 9.25f, 9.91421f, 9.25f, 9.5f)
                curveTo(9.25f, 7.98122f, 10.4812f, 6.75f, 12f, 6.75f)
                close()
            }
        }.build()

        return _FluentuiSystemIconsQuestionCircle!!
    }

private var _FluentuiSystemIconsQuestionCircle: ImageVector? = null
