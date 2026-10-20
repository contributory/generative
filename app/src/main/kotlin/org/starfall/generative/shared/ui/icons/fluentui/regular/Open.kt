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

val FluentuiSystemIconsOpen: ImageVector
    get() {
        if (_FluentuiSystemIconsOpen != null) return _FluentuiSystemIconsOpen!!

        _FluentuiSystemIconsOpen = ImageVector.Builder(
            name = "open",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(6.24992f, 4.5f)
                curveTo(5.28344f, 4.5f, 4.49996f, 5.2835f, 4.49996f, 6.25f)
                verticalLineTo(17.75f)
                curveTo(4.49996f, 18.7165f, 5.28344f, 19.5f, 6.24992f, 19.5f)
                horizontalLineTo(17.7496f)
                curveTo(18.7161f, 19.5f, 19.4996f, 18.7165f, 19.4996f, 17.75f)
                verticalLineTo(13.75f)
                curveTo(19.4996f, 13.3358f, 19.8354f, 13f, 20.2496f, 13f)
                curveTo(20.6638f, 13f, 20.9995f, 13.3358f, 20.9995f, 13.75f)
                verticalLineTo(17.75f)
                curveTo(20.9995f, 19.5449f, 19.5445f, 21f, 17.7496f, 21f)
                horizontalLineTo(6.24992f)
                curveTo(4.45504f, 21f, 3f, 19.5449f, 3f, 17.75f)
                verticalLineTo(6.25f)
                curveTo(3f, 4.45507f, 4.45504f, 3f, 6.24992f, 3f)
                horizontalLineTo(10.2498f)
                curveTo(10.664f, 3f, 10.9998f, 3.33579f, 10.9998f, 3.75f)
                curveTo(10.9998f, 4.16421f, 10.664f, 4.5f, 10.2498f, 4.5f)
                horizontalLineTo(6.24992f)
                close()
                moveTo(12.9997f, 3.75f)
                curveTo(12.9997f, 3.33579f, 13.3355f, 3f, 13.7497f, 3f)
                horizontalLineTo(20.25f)
                curveTo(20.6642f, 3f, 21f, 3.33579f, 21f, 3.75f)
                verticalLineTo(10.25f)
                curveTo(21f, 10.6642f, 20.6642f, 11f, 20.25f, 11f)
                curveTo(19.8358f, 11f, 19.5f, 10.6642f, 19.5f, 10.25f)
                verticalLineTo(5.56074f)
                lineTo(14.28f, 10.7804f)
                curveTo(13.9871f, 11.0732f, 13.5123f, 11.0732f, 13.2194f, 10.7803f)
                curveTo(12.9265f, 10.4874f, 12.9265f, 10.0125f, 13.2194f, 9.71964f)
                lineTo(18.4395f, 4.5f)
                horizontalLineTo(13.7497f)
                curveTo(13.3355f, 4.5f, 12.9997f, 4.16421f, 12.9997f, 3.75f)
                close()
            }
        }.build()

        return _FluentuiSystemIconsOpen!!
    }

private var _FluentuiSystemIconsOpen: ImageVector? = null
