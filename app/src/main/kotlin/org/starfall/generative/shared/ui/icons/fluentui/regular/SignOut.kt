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

val FluentuiSystemIconsSignOut: ImageVector
    get() {
        if (_FluentuiSystemIconsSignOut != null) return _FluentuiSystemIconsSignOut!!

        _FluentuiSystemIconsSignOut = ImageVector.Builder(
            name = "sign-out",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(8.50215f, 11.4995f)
                curveTo(9.05562f, 11.4995f, 9.5043f, 11.9482f, 9.5043f, 12.5017f)
                curveTo(9.5043f, 13.0552f, 9.05562f, 13.5038f, 8.50215f, 13.5038f)
                curveTo(7.94868f, 13.5038f, 7.5f, 13.0552f, 7.5f, 12.5017f)
                curveTo(7.5f, 11.9482f, 7.94868f, 11.4995f, 8.50215f, 11.4995f)
                close()
                moveTo(12f, 4.3537f)
                verticalLineTo(10.4995f)
                lineTo(12.0005f, 11.0045f)
                lineTo(19.442f, 11.0035f)
                lineTo(17.7196f, 9.27977f)
                curveTo(17.4534f, 9.01346f, 17.4292f, 8.59679f, 17.6471f, 8.30321f)
                lineTo(17.7198f, 8.21911f)
                curveTo(17.9861f, 7.95289f, 18.4027f, 7.92875f, 18.6963f, 8.14666f)
                lineTo(18.7804f, 8.21929f)
                lineTo(21.777f, 11.2169f)
                curveTo(22.043f, 11.483f, 22.0674f, 11.8992f, 21.85f, 12.1928f)
                lineTo(21.7775f, 12.2769f)
                lineTo(18.7809f, 15.2803f)
                curveTo(18.4884f, 15.5736f, 18.0135f, 15.5741f, 17.7203f, 15.2816f)
                curveTo(17.4537f, 15.0156f, 17.429f, 14.599f, 17.6465f, 14.3051f)
                lineTo(17.7191f, 14.2209f)
                lineTo(19.432f, 12.5035f)
                lineTo(12.0005f, 12.5045f)
                lineTo(12f, 19.2495f)
                curveTo(12f, 19.7159f, 11.5788f, 20.0692f, 11.1196f, 19.9881f)
                lineTo(2.61955f, 18.4868f)
                curveTo(2.26121f, 18.4235f, 2f, 18.1121f, 2f, 17.7482f)
                verticalLineTo(5.74953f)
                curveTo(2f, 5.38222f, 2.26601f, 5.06896f, 2.62847f, 5.00944f)
                lineTo(11.1285f, 3.61361f)
                curveTo(11.5851f, 3.53863f, 12f, 3.89097f, 12f, 4.3537f)
                close()
                moveTo(10.5f, 5.2369f)
                lineTo(3.5f, 6.38641f)
                verticalLineTo(17.1191f)
                lineTo(10.5f, 18.3555f)
                verticalLineTo(5.2369f)
                close()
                moveTo(13f, 18.5008f)
                lineTo(13.7652f, 18.501f)
                lineTo(13.867f, 18.4941f)
                curveTo(14.2335f, 18.4443f, 14.5158f, 18.1299f, 14.5152f, 17.7497f)
                lineTo(14.508f, 13.4995f)
                horizontalLineTo(13f)
                verticalLineTo(18.5008f)
                close()
                moveTo(13.002f, 9.99953f)
                lineTo(13f, 8.72487f)
                verticalLineTo(4.99952f)
                lineTo(13.7453f, 4.99953f)
                curveTo(14.1245f, 4.99953f, 14.4381f, 5.28105f, 14.4883f, 5.64664f)
                lineTo(14.4953f, 5.74829f)
                lineTo(14.502f, 9.99953f)
                horizontalLineTo(13.002f)
                close()
            }
        }.build()

        return _FluentuiSystemIconsSignOut!!
    }

private var _FluentuiSystemIconsSignOut: ImageVector? = null
