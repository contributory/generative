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

val FluentuiSystemIconsNavigation: ImageVector
    get() {
        if (_FluentuiSystemIconsNavigation != null) return _FluentuiSystemIconsNavigation!!

        _FluentuiSystemIconsNavigation = ImageVector.Builder(
            name = "navigation",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(2.75254f, 17.9997f)
                horizontalLineTo(21.2525f)
                curveTo(21.6668f, 17.9997f, 22.0025f, 18.3355f, 22.0025f, 18.7497f)
                curveTo(22.0025f, 19.1294f, 21.7204f, 19.4432f, 21.3543f, 19.4928f)
                lineTo(21.2525f, 19.4997f)
                horizontalLineTo(2.75254f)
                curveTo(2.33832f, 19.4997f, 2.00254f, 19.1639f, 2.00254f, 18.7497f)
                curveTo(2.00254f, 18.37f, 2.28469f, 18.0562f, 2.65077f, 18.0065f)
                lineTo(2.75254f, 17.9997f)
                horizontalLineTo(21.2525f)
                horizontalLineTo(2.75254f)
                close()
                moveTo(2.75254f, 11.5027f)
                horizontalLineTo(21.2525f)
                curveTo(21.6668f, 11.5027f, 22.0025f, 11.8385f, 22.0025f, 12.2527f)
                curveTo(22.0025f, 12.6324f, 21.7204f, 12.9462f, 21.3543f, 12.9959f)
                lineTo(21.2525f, 13.0027f)
                horizontalLineTo(2.75254f)
                curveTo(2.33832f, 13.0027f, 2.00254f, 12.6669f, 2.00254f, 12.2527f)
                curveTo(2.00254f, 11.873f, 2.28469f, 11.5592f, 2.65077f, 11.5095f)
                lineTo(2.75254f, 11.5027f)
                horizontalLineTo(21.2525f)
                horizontalLineTo(2.75254f)
                close()
                moveTo(2.75168f, 5.00293f)
                horizontalLineTo(21.2517f)
                curveTo(21.6659f, 5.00293f, 22.0017f, 5.33872f, 22.0017f, 5.75293f)
                curveTo(22.0017f, 6.13263f, 21.7195f, 6.44642f, 21.3535f, 6.49608f)
                lineTo(21.2517f, 6.50293f)
                horizontalLineTo(2.75168f)
                curveTo(2.33746f, 6.50293f, 2.00168f, 6.16714f, 2.00168f, 5.75293f)
                curveTo(2.00168f, 5.37323f, 2.28383f, 5.05944f, 2.64991f, 5.00978f)
                lineTo(2.75168f, 5.00293f)
                horizontalLineTo(21.2517f)
                horizontalLineTo(2.75168f)
                close()
            }
        }.build()

        return _FluentuiSystemIconsNavigation!!
    }

private var _FluentuiSystemIconsNavigation: ImageVector? = null
