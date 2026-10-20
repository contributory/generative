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

val FluentuiSystemIconsLightbulb: ImageVector
    get() {
        if (_FluentuiSystemIconsLightbulb != null) return _FluentuiSystemIconsLightbulb!!

        _FluentuiSystemIconsLightbulb = ImageVector.Builder(
            name = "lightbulb",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(12f, 2.00098f)
                curveTo(16.0041f, 2.00098f, 19.25f, 5.24691f, 19.25f, 9.25098f)
                curveTo(19.25f, 11.347f, 18.3493f, 13.2707f, 16.5869f, 14.9929f)
                curveTo(16.51f, 15.0681f, 16.4504f, 15.1586f, 16.4118f, 15.2582f)
                lineTo(16.3804f, 15.3605f)
                lineTo(15.2493f, 20.2561f)
                curveTo(15.0266f, 21.22f, 14.2035f, 21.9183f, 13.2302f, 21.993f)
                lineTo(13.057f, 21.9996f)
                horizontalLineTo(10.9433f)
                curveTo(9.95374f, 21.9996f, 9.08791f, 21.3545f, 8.79629f, 20.4228f)
                lineTo(8.75088f, 20.2555f)
                lineTo(7.62132f, 15.3607f)
                curveTo(7.58904f, 15.2208f, 7.51728f, 15.0931f, 7.41456f, 14.9928f)
                curveTo(5.73515f, 13.3526f, 4.83778f, 11.5297f, 4.75613f, 9.54923f)
                lineTo(4.75f, 9.25098f)
                lineTo(4.75388f, 9.01166f)
                curveTo(4.88014f, 5.11837f, 8.07601f, 2.00098f, 12f, 2.00098f)
                close()
                moveTo(14.115f, 18.499f)
                horizontalLineTo(9.884f)
                lineTo(10.2125f, 19.9182f)
                curveTo(10.2831f, 20.2245f, 10.5357f, 20.4504f, 10.8401f, 20.4925f)
                lineTo(10.9433f, 20.4996f)
                horizontalLineTo(13.057f)
                curveTo(13.3713f, 20.4996f, 13.6481f, 20.3044f, 13.7577f, 20.0174f)
                lineTo(13.7878f, 19.9184f)
                lineTo(14.115f, 18.499f)
                close()
                moveTo(12f, 3.50098f)
                curveTo(8.89821f, 3.50098f, 6.37006f, 5.95699f, 6.25415f, 9.03042f)
                lineTo(6.25f, 9.25098f)
                lineTo(6.25672f, 9.52799f)
                curveTo(6.33286f, 11.0913f, 7.05722f, 12.5471f, 8.46262f, 13.9197f)
                curveTo(8.72675f, 14.1777f, 8.92265f, 14.496f, 9.03422f, 14.846f)
                lineTo(9.08291f, 15.0235f)
                lineTo(9.538f, 16.999f)
                horizontalLineTo(14.461f)
                lineTo(14.9189f, 15.0228f)
                curveTo(15.0019f, 14.6634f, 15.1718f, 14.3309f, 15.4124f, 14.0539f)
                lineTo(15.5386f, 13.9201f)
                curveTo(16.9432f, 12.5475f, 17.6672f, 11.0916f, 17.7433f, 9.52803f)
                lineTo(17.75f, 9.25098f)
                lineTo(17.7458f, 9.03042f)
                curveTo(17.6299f, 5.95699f, 15.1018f, 3.50098f, 12f, 3.50098f)
                close()
            }
        }.build()

        return _FluentuiSystemIconsLightbulb!!
    }

private var _FluentuiSystemIconsLightbulb: ImageVector? = null
