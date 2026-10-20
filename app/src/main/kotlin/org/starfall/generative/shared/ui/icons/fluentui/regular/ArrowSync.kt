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

val FluentuiSystemIconsArrowSync: ImageVector
    get() {
        if (_FluentuiSystemIconsArrowSync != null) return _FluentuiSystemIconsArrowSync!!

        _FluentuiSystemIconsArrowSync = ImageVector.Builder(
            name = "arrow-sync",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(16.2506f, 5.18011f)
                curveTo(15.9994f, 5.50947f, 16.0627f, 5.9801f, 16.3921f, 6.23128f)
                curveTo(18.1804f, 7.59515f, 19.25f, 9.70821f, 19.25f, 12f)
                curveTo(19.25f, 15.736f, 16.4242f, 18.812f, 12.7933f, 19.2071f)
                lineTo(13.4697f, 18.5303f)
                curveTo(13.7626f, 18.2374f, 13.7626f, 17.7626f, 13.4697f, 17.4697f)
                curveTo(13.2034f, 17.2034f, 12.7867f, 17.1792f, 12.4931f, 17.3971f)
                lineTo(12.409f, 17.4697f)
                lineTo(10.409f, 19.4697f)
                curveTo(10.1427f, 19.7359f, 10.1185f, 20.1526f, 10.3364f, 20.4462f)
                lineTo(10.409f, 20.5303f)
                lineTo(12.409f, 22.5303f)
                curveTo(12.7019f, 22.8232f, 13.1768f, 22.8232f, 13.4697f, 22.5303f)
                curveTo(13.7359f, 22.2641f, 13.7601f, 21.8474f, 13.5423f, 21.5538f)
                lineTo(13.4697f, 21.4697f)
                lineTo(12.7194f, 20.7208f)
                curveTo(17.2154f, 20.355f, 20.75f, 16.5903f, 20.75f, 12f)
                curveTo(20.75f, 9.23526f, 19.4582f, 6.68321f, 17.3017f, 5.03856f)
                curveTo(16.9724f, 4.78738f, 16.5017f, 4.85075f, 16.2506f, 5.18011f)
                close()
                moveTo(10.5303f, 1.46967f)
                curveTo(10.2374f, 1.76256f, 10.2374f, 2.23744f, 10.5303f, 2.53033f)
                lineTo(11.2796f, 3.27923f)
                curveTo(6.78409f, 3.6456f, 3.25f, 7.41008f, 3.25f, 12f)
                curveTo(3.25f, 14.6445f, 4.43126f, 17.0974f, 6.43081f, 18.7491f)
                curveTo(6.75016f, 19.0129f, 7.22289f, 18.9679f, 7.48669f, 18.6485f)
                curveTo(7.75048f, 18.3292f, 7.70545f, 17.8564f, 7.3861f, 17.5926f)
                curveTo(5.72793f, 16.2229f, 4.75f, 14.1922f, 4.75f, 12f)
                curveTo(4.75f, 8.26436f, 7.57532f, 5.18861f, 11.2057f, 4.79301f)
                lineTo(10.5303f, 5.46967f)
                curveTo(10.2374f, 5.76256f, 10.2374f, 6.23744f, 10.5303f, 6.53033f)
                curveTo(10.8232f, 6.82322f, 11.2981f, 6.82322f, 11.591f, 6.53033f)
                lineTo(13.591f, 4.53033f)
                curveTo(13.8839f, 4.23744f, 13.8839f, 3.76256f, 13.591f, 3.46967f)
                lineTo(11.591f, 1.46967f)
                curveTo(11.2981f, 1.17678f, 10.8232f, 1.17678f, 10.5303f, 1.46967f)
                close()
            }
        }.build()

        return _FluentuiSystemIconsArrowSync!!
    }

private var _FluentuiSystemIconsArrowSync: ImageVector? = null
