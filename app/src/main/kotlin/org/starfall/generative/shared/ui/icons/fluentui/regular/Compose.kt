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

val FluentuiSystemIconsCompose: ImageVector
    get() {
        if (_FluentuiSystemIconsCompose != null) return _FluentuiSystemIconsCompose!!

        _FluentuiSystemIconsCompose = ImageVector.Builder(
            name = "compose",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(13.25f, 4.00003f)
                curveTo(13.6642f, 4.00003f, 14f, 4.33582f, 14f, 4.75003f)
                curveTo(13.9999f, 5.1642f, 13.6642f, 5.50003f, 13.25f, 5.50003f)
                horizontalLineTo(6.25f)
                curveTo(5.2835f, 5.50003f, 4.5f, 6.28353f, 4.5f, 7.25003f)
                verticalLineTo(17.75f)
                curveTo(4.50005f, 18.7165f, 5.28353f, 19.5f, 6.25f, 19.5f)
                horizontalLineTo(16.75f)
                curveTo(17.7165f, 19.5f, 18.4999f, 18.7165f, 18.5f, 17.75f)
                verticalLineTo(10.75f)
                curveTo(18.5f, 10.3358f, 18.8358f, 10f, 19.25f, 10f)
                curveTo(19.6642f, 10f, 20f, 10.3358f, 20f, 10.75f)
                verticalLineTo(17.75f)
                curveTo(19.9999f, 19.5449f, 18.5449f, 21f, 16.75f, 21f)
                horizontalLineTo(6.25f)
                curveTo(4.4551f, 21f, 3.00005f, 19.5449f, 3f, 17.75f)
                verticalLineTo(7.25003f)
                curveTo(3f, 5.4551f, 4.45507f, 4.00003f, 6.25f, 4.00003f)
                horizontalLineTo(13.25f)
                close()
                moveTo(19.7197f, 3.21976f)
                curveTo(20.0126f, 2.92687f, 20.4874f, 2.92687f, 20.7803f, 3.21976f)
                curveTo(21.0731f, 3.51265f, 21.0732f, 3.98742f, 20.7803f, 4.2803f)
                lineTo(10.5908f, 14.4697f)
                lineTo(9f, 15f)
                lineTo(9.53027f, 13.4092f)
                lineTo(19.7197f, 3.21976f)
                close()
            }
        }.build()

        return _FluentuiSystemIconsCompose!!
    }

private var _FluentuiSystemIconsCompose: ImageVector? = null
