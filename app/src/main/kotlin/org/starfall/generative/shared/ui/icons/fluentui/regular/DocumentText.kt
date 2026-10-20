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

val FluentuiSystemIconsDocumentText: ImageVector
    get() {
        if (_FluentuiSystemIconsDocumentText != null) return _FluentuiSystemIconsDocumentText!!

        _FluentuiSystemIconsDocumentText = ImageVector.Builder(
            name = "document-text",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(8.75f, 11.5f)
                curveTo(8.33579f, 11.5f, 8f, 11.8358f, 8f, 12.25f)
                curveTo(8f, 12.6642f, 8.33579f, 13f, 8.75f, 13f)
                horizontalLineTo(15.25f)
                curveTo(15.6642f, 13f, 16f, 12.6642f, 16f, 12.25f)
                curveTo(16f, 11.8358f, 15.6642f, 11.5f, 15.25f, 11.5f)
                horizontalLineTo(8.75f)
                close()
                moveTo(8.75f, 14.25f)
                curveTo(8.33579f, 14.25f, 8f, 14.5858f, 8f, 15f)
                curveTo(8f, 15.4142f, 8.33579f, 15.75f, 8.75f, 15.75f)
                horizontalLineTo(15.25f)
                curveTo(15.6642f, 15.75f, 16f, 15.4142f, 16f, 15f)
                curveTo(16f, 14.5858f, 15.6642f, 14.25f, 15.25f, 14.25f)
                horizontalLineTo(8.75f)
                close()
                moveTo(8.75f, 17f)
                curveTo(8.33579f, 17f, 8f, 17.3358f, 8f, 17.75f)
                curveTo(8f, 18.1642f, 8.33579f, 18.5f, 8.75f, 18.5f)
                horizontalLineTo(15.25f)
                curveTo(15.6642f, 18.5f, 16f, 18.1642f, 16f, 17.75f)
                curveTo(16f, 17.3358f, 15.6642f, 17f, 15.25f, 17f)
                horizontalLineTo(8.75f)
                close()
                moveTo(13.585f, 2.586f)
                lineTo(19.414f, 8.414f)
                curveTo(19.789f, 8.789f, 20f, 9.298f, 20f, 9.828f)
                verticalLineTo(20f)
                curveTo(20f, 21.104f, 19.104f, 22f, 18f, 22f)
                horizontalLineTo(6f)
                curveTo(4.896f, 22f, 4f, 21.104f, 4f, 20f)
                verticalLineTo(4f)
                curveTo(4f, 2.896f, 4.896f, 2f, 6f, 2f)
                horizontalLineTo(12.172f)
                curveTo(12.6865f, 2f, 13.2194f, 2.22035f, 13.585f, 2.586f)
                close()
                moveTo(18f, 20.5f)
                curveTo(18.276f, 20.5f, 18.5f, 20.275f, 18.5f, 20f)
                verticalLineTo(10f)
                horizontalLineTo(14f)
                curveTo(12.896f, 10f, 12f, 9.104f, 12f, 8f)
                verticalLineTo(3.5f)
                horizontalLineTo(6f)
                curveTo(5.724f, 3.5f, 5.5f, 3.725f, 5.5f, 4f)
                verticalLineTo(20f)
                curveTo(5.5f, 20.275f, 5.724f, 20.5f, 6f, 20.5f)
                horizontalLineTo(18f)
                close()
                moveTo(17.378f, 8.5f)
                lineTo(13.5f, 4.621f)
                verticalLineTo(8f)
                curveTo(13.5f, 8.275f, 13.724f, 8.5f, 14f, 8.5f)
                horizontalLineTo(17.378f)
                close()
            }
        }.build()

        return _FluentuiSystemIconsDocumentText!!
    }

private var _FluentuiSystemIconsDocumentText: ImageVector? = null
