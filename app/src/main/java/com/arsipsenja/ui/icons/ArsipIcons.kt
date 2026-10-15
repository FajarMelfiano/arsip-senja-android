package com.arsipsenja.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Ikon vektor bergaya Lucide (outline 2dp, ujung membulat).
 * Dihasilkan dari sumber ikon Lucide (ISC License) oleh skrip
 * generator internal — jangan diedit manual tanpa mengulang generator.
 */
object ArsipIcons {

    /**
     * Pembungkus yang menerima literal angka bulat (generator ikon
     * mengeluarkan koordinat Int; PathNode hanya menerima Float) dan
     * mengakumulasikannya sebagai daftar PathNode untuk addPath.
     */
    private class JalurAngka {
        val nodes = mutableListOf<PathNode>()

        fun moveTo(x: Number, y: Number) {
            nodes += PathNode.MoveTo(x.toFloat(), y.toFloat())
        }

        fun lineTo(x: Number, y: Number) {
            nodes += PathNode.LineTo(x.toFloat(), y.toFloat())
        }

        fun cubicTo(
            x1: Number,
            y1: Number,
            x2: Number,
            y2: Number,
            x3: Number,
            y3: Number,
        ) {
            nodes += PathNode.CurveTo(
                x1.toFloat(), y1.toFloat(),
                x2.toFloat(), y2.toFloat(),
                x3.toFloat(), y3.toFloat(),
            )
        }

        fun close() {
            nodes += PathNode.Close
        }
    }

    val ArrowLeft: ImageVector by lazy {
        arsipIcon("arrow-left") {
            moveTo(12, 19)
            lineTo(5, 12)
            lineTo(12, 5)
            moveTo(19, 12)
            lineTo(5, 12)
        }
    }

    val ChevronDown: ImageVector by lazy {
        arsipIcon("chevron-down") {
            moveTo(6, 9)
            lineTo(12, 15)
            lineTo(18, 9)
        }
    }

    val ChevronRight: ImageVector by lazy {
        arsipIcon("chevron-right") {
            moveTo(9, 18)
            lineTo(15, 12)
            lineTo(9, 6)
        }
    }

    val ChevronUp: ImageVector by lazy {
        arsipIcon("chevron-up") {
            moveTo(18, 15)
            lineTo(12, 9)
            lineTo(6, 15)
        }
    }

    val X: ImageVector by lazy {
        arsipIcon("x") {
            moveTo(18, 6)
            lineTo(6, 18)
            moveTo(6, 6)
            lineTo(18, 18)
        }
    }

    val Play: ImageVector by lazy {
        arsipIcon("play") {
            moveTo(6, 3)
            lineTo(20, 12)
            lineTo(6, 21)
            close()
        }
    }

    val Pause: ImageVector by lazy {
        arsipIcon("pause") {
            moveTo(14, 4)
            lineTo(18, 4)
            lineTo(18, 20)
            lineTo(14, 20)
            close()
            moveTo(6, 4)
            lineTo(10, 4)
            lineTo(10, 20)
            lineTo(6, 20)
            close()
        }
    }

    val Plus: ImageVector by lazy {
        arsipIcon("plus") {
            moveTo(5, 12)
            lineTo(19, 12)
            moveTo(12, 5)
            lineTo(12, 19)
        }
    }

    val Check: ImageVector by lazy {
        arsipIcon("check") {
            moveTo(20, 6)
            lineTo(9, 17)
            lineTo(4, 12)
        }
    }

    val Search: ImageVector by lazy {
        arsipIcon("search") {
            moveTo(21, 21)
            lineTo(16.66, 16.66)
            moveTo(19, 11)
            cubicTo(19, 15.418, 15.418, 19, 11, 19)
            cubicTo(6.582, 19, 3, 15.418, 3, 11)
            cubicTo(3, 6.582, 6.582, 3, 11, 3)
            cubicTo(15.418, 3, 19, 6.582, 19, 11)
            close()
        }
    }

    val Settings: ImageVector by lazy {
        arsipIcon("settings") {
            moveTo(9.671, 4.136)
            cubicTo(9.785, 2.935, 10.794, 2.017, 12, 2.017)
            cubicTo(13.207, 2.017, 14.216, 2.935, 14.33, 4.136)
            cubicTo(14.397, 4.896, 14.831, 5.575, 15.491, 5.957)
            cubicTo(16.152, 6.338, 16.957, 6.373, 17.649, 6.051)
            cubicTo(18.745, 5.553, 20.04, 5.969, 20.643, 7.011)
            cubicTo(21.245, 8.054, 20.958, 9.383, 19.979, 10.084)
            cubicTo(19.355, 10.522, 18.983, 11.237, 18.983, 11.999)
            cubicTo(18.983, 12.762, 19.355, 13.477, 19.979, 13.915)
            cubicTo(20.958, 14.616, 21.245, 15.945, 20.643, 16.988)
            cubicTo(20.04, 18.03, 18.745, 18.446, 17.649, 17.948)
            cubicTo(16.957, 17.626, 16.152, 17.661, 15.491, 18.042)
            cubicTo(14.831, 18.424, 14.397, 19.103, 14.33, 19.863)
            cubicTo(14.216, 21.064, 13.207, 21.982, 12.001, 21.982)
            cubicTo(10.794, 21.982, 9.785, 21.064, 9.671, 19.863)
            cubicTo(9.604, 19.103, 9.17, 18.423, 8.509, 18.042)
            cubicTo(7.848, 17.66, 7.043, 17.625, 6.351, 17.948)
            cubicTo(5.255, 18.446, 3.96, 18.03, 3.357, 16.988)
            cubicTo(2.755, 15.945, 3.042, 14.616, 4.021, 13.915)
            cubicTo(4.645, 13.477, 5.017, 12.762, 5.017, 11.999)
            cubicTo(5.017, 11.237, 4.645, 10.522, 4.021, 10.084)
            cubicTo(3.044, 9.383, 2.757, 8.054, 3.359, 7.013)
            cubicTo(3.961, 5.971, 5.254, 5.555, 6.35, 6.051)
            cubicTo(7.042, 6.373, 7.847, 6.338, 8.508, 5.957)
            cubicTo(9.168, 5.575, 9.602, 4.896, 9.669, 4.136)
            moveTo(15, 12)
            cubicTo(15, 13.657, 13.657, 15, 12, 15)
            cubicTo(10.343, 15, 9, 13.657, 9, 12)
            cubicTo(9, 10.343, 10.343, 9, 12, 9)
            cubicTo(13.657, 9, 15, 10.343, 15, 12)
            close()
        }
    }

    val EllipsisVertical: ImageVector by lazy {
        arsipIcon("ellipsis-vertical") {
            moveTo(13, 12)
            cubicTo(13, 12.552, 12.552, 13, 12, 13)
            cubicTo(11.448, 13, 11, 12.552, 11, 12)
            cubicTo(11, 11.448, 11.448, 11, 12, 11)
            cubicTo(12.552, 11, 13, 11.448, 13, 12)
            close()
            moveTo(13, 5)
            cubicTo(13, 5.552, 12.552, 6, 12, 6)
            cubicTo(11.448, 6, 11, 5.552, 11, 5)
            cubicTo(11, 4.448, 11.448, 4, 12, 4)
            cubicTo(12.552, 4, 13, 4.448, 13, 5)
            close()
            moveTo(13, 19)
            cubicTo(13, 19.552, 12.552, 20, 12, 20)
            cubicTo(11.448, 20, 11, 19.552, 11, 19)
            cubicTo(11, 18.448, 11.448, 18, 12, 18)
            cubicTo(12.552, 18, 13, 18.448, 13, 19)
            close()
        }
    }

    val File: ImageVector by lazy {
        arsipIcon("file") {
            moveTo(6, 22)
            cubicTo(4.895, 22, 4, 21.105, 4, 20)
            lineTo(4, 4)
            cubicTo(4, 2.895, 4.895, 2, 6, 2)
            lineTo(14, 2)
            cubicTo(14.639, 1.999, 15.253, 2.253, 15.704, 2.706)
            lineTo(19.292, 6.294)
            cubicTo(19.746, 6.745, 20.001, 7.36, 20, 8)
            lineTo(20, 20)
            cubicTo(20, 21.105, 19.105, 22, 18, 22)
            close()
            moveTo(14, 2)
            lineTo(14, 7)
            cubicTo(14, 7.552, 14.448, 8, 15, 8)
            lineTo(20, 8)
        }
    }

    val FileText: ImageVector by lazy {
        arsipIcon("file-text") {
            moveTo(6, 22)
            cubicTo(4.895, 22, 4, 21.105, 4, 20)
            lineTo(4, 4)
            cubicTo(4, 2.895, 4.895, 2, 6, 2)
            lineTo(14, 2)
            cubicTo(14.639, 1.999, 15.253, 2.253, 15.704, 2.706)
            lineTo(19.292, 6.294)
            cubicTo(19.746, 6.745, 20.001, 7.36, 20, 8)
            lineTo(20, 20)
            cubicTo(20, 21.105, 19.105, 22, 18, 22)
            close()
            moveTo(14, 2)
            lineTo(14, 7)
            cubicTo(14, 7.552, 14.448, 8, 15, 8)
            lineTo(20, 8)
            moveTo(10, 9)
            lineTo(8, 9)
            moveTo(16, 13)
            lineTo(8, 13)
            moveTo(16, 17)
            lineTo(8, 17)
        }
    }

    val Image: ImageVector by lazy {
        arsipIcon("image") {
            moveTo(5, 3)
            lineTo(19, 3)
            cubicTo(20.105, 3, 21, 3.895, 21, 5)
            lineTo(21, 19)
            cubicTo(21, 20.105, 20.105, 21, 19, 21)
            lineTo(5, 21)
            cubicTo(3.895, 21, 3, 20.105, 3, 19)
            lineTo(3, 5)
            cubicTo(3, 3.895, 3.895, 3, 5, 3)
            close()
            moveTo(11, 9)
            cubicTo(11, 10.105, 10.105, 11, 9, 11)
            cubicTo(7.895, 11, 7, 10.105, 7, 9)
            cubicTo(7, 7.895, 7.895, 7, 9, 7)
            cubicTo(10.105, 7, 11, 7.895, 11, 9)
            close()
            moveTo(21, 15)
            lineTo(17.914, 11.914)
            cubicTo(17.133, 11.133, 15.867, 11.133, 15.086, 11.914)
            lineTo(6, 21)
        }
    }

    val Film: ImageVector by lazy {
        arsipIcon("film") {
            moveTo(5, 3)
            lineTo(19, 3)
            cubicTo(20.105, 3, 21, 3.895, 21, 5)
            lineTo(21, 19)
            cubicTo(21, 20.105, 20.105, 21, 19, 21)
            lineTo(5, 21)
            cubicTo(3.895, 21, 3, 20.105, 3, 19)
            lineTo(3, 5)
            cubicTo(3, 3.895, 3.895, 3, 5, 3)
            close()
            moveTo(7, 3)
            lineTo(7, 21)
            moveTo(3, 7.5)
            lineTo(7, 7.5)
            moveTo(3, 12)
            lineTo(21, 12)
            moveTo(3, 16.5)
            lineTo(7, 16.5)
            moveTo(17, 3)
            lineTo(17, 21)
            moveTo(17, 7.5)
            lineTo(21, 7.5)
            moveTo(17, 16.5)
            lineTo(21, 16.5)
        }
    }

    val Music: ImageVector by lazy {
        arsipIcon("music") {
            moveTo(9, 18)
            lineTo(9, 5)
            lineTo(21, 3)
            lineTo(21, 16)
            moveTo(9, 18)
            cubicTo(9, 19.657, 7.657, 21, 6, 21)
            cubicTo(4.343, 21, 3, 19.657, 3, 18)
            cubicTo(3, 16.343, 4.343, 15, 6, 15)
            cubicTo(7.657, 15, 9, 16.343, 9, 18)
            close()
            moveTo(21, 16)
            cubicTo(21, 17.657, 19.657, 19, 18, 19)
            cubicTo(16.343, 19, 15, 17.657, 15, 16)
            cubicTo(15, 14.343, 16.343, 13, 18, 13)
            cubicTo(19.657, 13, 21, 14.343, 21, 16)
            close()
        }
    }

    val Folder: ImageVector by lazy {
        arsipIcon("folder") {
            moveTo(20, 20)
            cubicTo(21.105, 20, 22, 19.105, 22, 18)
            lineTo(22, 8)
            cubicTo(22, 6.895, 21.105, 6, 20, 6)
            lineTo(12.1, 6)
            cubicTo(11.42, 6.007, 10.784, 5.668, 10.41, 5.1)
            lineTo(9.6, 3.9)
            cubicTo(9.23, 3.338, 8.603, 3, 7.93, 3)
            lineTo(4, 3)
            cubicTo(2.895, 3, 2, 3.895, 2, 5)
            lineTo(2, 18)
            cubicTo(2, 19.105, 2.895, 20, 4, 20)
            close()
        }
    }

    val Archive: ImageVector by lazy {
        arsipIcon("archive") {
            moveTo(3, 3)
            lineTo(21, 3)
            cubicTo(21.552, 3, 22, 3.448, 22, 4)
            lineTo(22, 7)
            cubicTo(22, 7.552, 21.552, 8, 21, 8)
            lineTo(3, 8)
            cubicTo(2.448, 8, 2, 7.552, 2, 7)
            lineTo(2, 4)
            cubicTo(2, 3.448, 2.448, 3, 3, 3)
            close()
            moveTo(4, 8)
            lineTo(4, 19)
            cubicTo(4, 20.105, 4.895, 21, 6, 21)
            lineTo(18, 21)
            cubicTo(19.105, 21, 20, 20.105, 20, 19)
            lineTo(20, 8)
            moveTo(10, 12)
            lineTo(14, 12)
        }
    }

    val Download: ImageVector by lazy {
        arsipIcon("download") {
            moveTo(12, 15)
            lineTo(12, 3)
            moveTo(21, 15)
            lineTo(21, 19)
            cubicTo(21, 20.105, 20.105, 21, 19, 21)
            lineTo(5, 21)
            cubicTo(3.895, 21, 3, 20.105, 3, 19)
            lineTo(3, 15)
            moveTo(7, 10)
            lineTo(12, 15)
            lineTo(17, 10)
        }
    }

    val Upload: ImageVector by lazy {
        arsipIcon("upload") {
            moveTo(12, 3)
            lineTo(12, 15)
            moveTo(17, 8)
            lineTo(12, 3)
            lineTo(7, 8)
            moveTo(21, 15)
            lineTo(21, 19)
            cubicTo(21, 20.105, 20.105, 21, 19, 21)
            lineTo(5, 21)
            cubicTo(3.895, 21, 3, 20.105, 3, 19)
            lineTo(3, 15)
        }
    }

    val HardDrive: ImageVector by lazy {
        arsipIcon("hard-drive") {
            moveTo(10, 16)
            lineTo(10.01, 16)
            moveTo(2.212, 11.577)
            cubicTo(2.073, 11.855, 2, 12.162, 2, 12.473)
            lineTo(2, 18)
            cubicTo(2, 19.105, 2.895, 20, 4, 20)
            lineTo(20, 20)
            cubicTo(21.105, 20, 22, 19.105, 22, 18)
            lineTo(22, 12.473)
            cubicTo(22, 12.162, 21.927, 11.855, 21.788, 11.577)
            lineTo(18.55, 5.11)
            cubicTo(18.212, 4.43, 17.519, 4, 16.76, 4)
            lineTo(7.24, 4)
            cubicTo(6.481, 4, 5.788, 4.43, 5.45, 5.11)
            close()
            moveTo(21.946, 12.013)
            lineTo(2.054, 12.013)
            moveTo(6, 16)
            lineTo(6.01, 16)
        }
    }

    val Database: ImageVector by lazy {
        arsipIcon("database") {
            moveTo(21, 5)
            cubicTo(21, 6.657, 16.971, 8, 12, 8)
            cubicTo(7.029, 8, 3, 6.657, 3, 5)
            cubicTo(3, 3.343, 7.029, 2, 12, 2)
            cubicTo(16.971, 2, 21, 3.343, 21, 5)
            close()
            moveTo(3, 5)
            lineTo(3, 19)
            cubicTo(3, 20.657, 7.029, 22, 12, 22)
            cubicTo(16.971, 22, 21, 20.657, 21, 19)
            lineTo(21, 5)
            moveTo(3, 12)
            cubicTo(3, 13.657, 7.029, 15, 12, 15)
            cubicTo(16.971, 15, 21, 13.657, 21, 12)
        }
    }

    val Inbox: ImageVector by lazy {
        arsipIcon("inbox") {
            moveTo(22, 12)
            lineTo(16, 12)
            lineTo(14, 15)
            lineTo(10, 15)
            lineTo(8, 12)
            lineTo(2, 12)
            moveTo(5.45, 5.11)
            lineTo(2, 12)
            lineTo(2, 18)
            cubicTo(2, 19.105, 2.895, 20, 4, 20)
            lineTo(20, 20)
            cubicTo(21.105, 20, 22, 19.105, 22, 18)
            lineTo(22, 12)
            lineTo(18.55, 5.11)
            cubicTo(18.212, 4.43, 17.519, 4, 16.76, 4)
            lineTo(7.24, 4)
            cubicTo(6.481, 4, 5.788, 4.43, 5.45, 5.11)
            close()
        }
    }

    val Trash2: ImageVector by lazy {
        arsipIcon("trash-2") {
            moveTo(10, 11)
            lineTo(10, 17)
            moveTo(14, 11)
            lineTo(14, 17)
            moveTo(19, 6)
            lineTo(19, 20)
            cubicTo(19, 21.105, 18.105, 22, 17, 22)
            lineTo(7, 22)
            cubicTo(5.895, 22, 5, 21.105, 5, 20)
            lineTo(5, 6)
            moveTo(3, 6)
            lineTo(21, 6)
            moveTo(8, 6)
            lineTo(8, 4)
            cubicTo(8, 2.895, 8.895, 2, 10, 2)
            lineTo(14, 2)
            cubicTo(15.105, 2, 16, 2.895, 16, 4)
            lineTo(16, 6)
        }
    }

    val Pencil: ImageVector by lazy {
        arsipIcon("pencil") {
            moveTo(21.174, 6.812)
            cubicTo(22.275, 5.711, 22.275, 3.926, 21.174, 2.826)
            cubicTo(20.074, 1.725, 18.289, 1.724, 17.188, 2.825)
            lineTo(3.842, 16.174)
            cubicTo(3.61, 16.406, 3.438, 16.691, 3.342, 17.004)
            lineTo(2.021, 21.356)
            cubicTo(1.968, 21.532, 2.017, 21.723, 2.147, 21.853)
            cubicTo(2.277, 21.983, 2.468, 22.031, 2.644, 21.978)
            lineTo(6.997, 20.658)
            cubicTo(7.31, 20.563, 7.595, 20.392, 7.827, 20.161)
            close()
            moveTo(15, 5)
            lineTo(19, 9)
        }
    }

    val Copy: ImageVector by lazy {
        arsipIcon("copy") {
            moveTo(10, 8)
            lineTo(20, 8)
            cubicTo(21.105, 8, 22, 8.895, 22, 10)
            lineTo(22, 20)
            cubicTo(22, 21.105, 21.105, 22, 20, 22)
            lineTo(10, 22)
            cubicTo(8.895, 22, 8, 21.105, 8, 20)
            lineTo(8, 10)
            cubicTo(8, 8.895, 8.895, 8, 10, 8)
            close()
            moveTo(4, 16)
            cubicTo(2.9, 16, 2, 15.1, 2, 14)
            lineTo(2, 4)
            cubicTo(2, 2.9, 2.9, 2, 4, 2)
            lineTo(14, 2)
            cubicTo(15.1, 2, 16, 2.9, 16, 4)
        }
    }

    val Share2: ImageVector by lazy {
        arsipIcon("share-2") {
            moveTo(21, 5)
            cubicTo(21, 6.657, 19.657, 8, 18, 8)
            cubicTo(16.343, 8, 15, 6.657, 15, 5)
            cubicTo(15, 3.343, 16.343, 2, 18, 2)
            cubicTo(19.657, 2, 21, 3.343, 21, 5)
            close()
            moveTo(9, 12)
            cubicTo(9, 13.657, 7.657, 15, 6, 15)
            cubicTo(4.343, 15, 3, 13.657, 3, 12)
            cubicTo(3, 10.343, 4.343, 9, 6, 9)
            cubicTo(7.657, 9, 9, 10.343, 9, 12)
            close()
            moveTo(21, 19)
            cubicTo(21, 20.657, 19.657, 22, 18, 22)
            cubicTo(16.343, 22, 15, 20.657, 15, 19)
            cubicTo(15, 17.343, 16.343, 16, 18, 16)
            cubicTo(19.657, 16, 21, 17.343, 21, 19)
            close()
            moveTo(8.59, 13.51)
            lineTo(15.42, 17.49)
            moveTo(15.41, 6.51)
            lineTo(8.59, 10.49)
        }
    }

    val RefreshCw: ImageVector by lazy {
        arsipIcon("refresh-cw") {
            moveTo(3, 12)
            cubicTo(3, 7.029, 7.029, 3, 12, 3)
            cubicTo(14.516, 3.009, 16.931, 3.991, 18.74, 5.74)
            lineTo(21, 8)
            moveTo(21, 3)
            lineTo(21, 8)
            lineTo(16, 8)
            moveTo(21, 12)
            cubicTo(21, 16.971, 16.971, 21, 12, 21)
            cubicTo(9.484, 20.991, 7.069, 20.009, 5.26, 18.26)
            lineTo(3, 16)
            moveTo(8, 16)
            lineTo(3, 16)
            lineTo(3, 21)
        }
    }

    val Eye: ImageVector by lazy {
        arsipIcon("eye") {
            moveTo(2.062, 12.348)
            cubicTo(1.979, 12.123, 1.979, 11.877, 2.062, 11.652)
            cubicTo(3.722, 7.627, 7.646, 5.001, 12, 5.001)
            cubicTo(16.354, 5.001, 20.278, 7.627, 21.938, 11.652)
            cubicTo(22.021, 11.877, 22.021, 12.123, 21.938, 12.348)
            cubicTo(20.278, 16.373, 16.354, 18.999, 12, 18.999)
            cubicTo(7.646, 18.999, 3.722, 16.373, 2.062, 12.348)
            moveTo(15, 12)
            cubicTo(15, 13.657, 13.657, 15, 12, 15)
            cubicTo(10.343, 15, 9, 13.657, 9, 12)
            cubicTo(9, 10.343, 10.343, 9, 12, 9)
            cubicTo(13.657, 9, 15, 10.343, 15, 12)
            close()
        }
    }

    val Link: ImageVector by lazy {
        arsipIcon("link") {
            moveTo(10, 13)
            cubicTo(10.869, 14.162, 12.2, 14.889, 13.647, 14.992)
            cubicTo(15.094, 15.096, 16.514, 14.566, 17.54, 13.54)
            lineTo(20.54, 10.54)
            cubicTo(22.435, 8.578, 22.408, 5.46, 20.479, 3.531)
            cubicTo(18.55, 1.602, 15.432, 1.575, 13.47, 3.47)
            lineTo(11.75, 5.18)
            moveTo(14, 11)
            cubicTo(13.131, 9.838, 11.8, 9.111, 10.353, 9.008)
            cubicTo(8.906, 8.904, 7.486, 9.434, 6.46, 10.46)
            lineTo(3.46, 13.46)
            cubicTo(1.565, 15.422, 1.592, 18.54, 3.521, 20.469)
            cubicTo(5.45, 22.398, 8.568, 22.425, 10.53, 20.53)
            lineTo(12.24, 18.82)
        }
    }

    val Unlink: ImageVector by lazy {
        arsipIcon("unlink") {
            moveTo(18.84, 12.25)
            lineTo(20.56, 10.54)
            lineTo(20.54, 10.54)
            cubicTo(22.457, 8.554, 22.403, 5.39, 20.42, 3.47)
            cubicTo(18.481, 1.6, 15.409, 1.6, 13.47, 3.47)
            lineTo(11.75, 5.18)
            moveTo(5.17, 11.75)
            lineTo(3.46, 13.46)
            cubicTo(1.543, 15.446, 1.597, 18.61, 3.58, 20.53)
            cubicTo(5.519, 22.4, 8.591, 22.4, 10.53, 20.53)
            lineTo(12.24, 18.82)
            moveTo(8, 2)
            lineTo(8, 5)
            moveTo(2, 8)
            lineTo(5, 8)
            moveTo(16, 19)
            lineTo(16, 22)
            moveTo(19, 16)
            lineTo(22, 16)
        }
    }

    val LogOut: ImageVector by lazy {
        arsipIcon("log-out") {
            moveTo(16, 17)
            lineTo(21, 12)
            lineTo(16, 7)
            moveTo(21, 12)
            lineTo(9, 12)
            moveTo(9, 21)
            lineTo(5, 21)
            cubicTo(3.895, 21, 3, 20.105, 3, 19)
            lineTo(3, 5)
            cubicTo(3, 3.895, 3.895, 3, 5, 3)
            lineTo(9, 3)
        }
    }

    val Filter: ImageVector by lazy {
        arsipIcon("filter") {
            moveTo(10, 20)
            cubicTo(10, 20.379, 10.214, 20.726, 10.553, 20.895)
            lineTo(12.553, 21.895)
            cubicTo(12.863, 22.05, 13.231, 22.033, 13.526, 21.851)
            cubicTo(13.821, 21.669, 14, 21.347, 14, 21)
            lineTo(14, 14)
            cubicTo(14, 13.504, 14.184, 13.027, 14.517, 12.659)
            lineTo(21.74, 4.67)
            cubicTo(22.004, 4.377, 22.072, 3.956, 21.912, 3.595)
            cubicTo(21.752, 3.234, 21.395, 3.001, 21, 3)
            lineTo(3, 3)
            cubicTo(2.605, 3, 2.247, 3.233, 2.087, 3.594)
            cubicTo(1.926, 3.955, 1.993, 4.377, 2.258, 4.67)
            lineTo(9.483, 12.659)
            cubicTo(9.816, 13.027, 10, 13.504, 10, 14)
            close()
        }
    }

    val ArrowUpDown: ImageVector by lazy {
        arsipIcon("arrow-up-down") {
            moveTo(21, 16)
            lineTo(17, 20)
            lineTo(13, 16)
            moveTo(17, 20)
            lineTo(17, 4)
            moveTo(3, 8)
            lineTo(7, 4)
            lineTo(11, 8)
            moveTo(7, 4)
            lineTo(7, 20)
        }
    }

    val Info: ImageVector by lazy {
        arsipIcon("info") {
            moveTo(22, 12)
            cubicTo(22, 17.523, 17.523, 22, 12, 22)
            cubicTo(6.477, 22, 2, 17.523, 2, 12)
            cubicTo(2, 6.477, 6.477, 2, 12, 2)
            cubicTo(17.523, 2, 22, 6.477, 22, 12)
            close()
            moveTo(12, 16)
            lineTo(12, 12)
            moveTo(12, 8)
            lineTo(12.01, 8)
        }
    }

    val CircleAlert: ImageVector by lazy {
        arsipIcon("circle-alert") {
            moveTo(22, 12)
            cubicTo(22, 17.523, 17.523, 22, 12, 22)
            cubicTo(6.477, 22, 2, 17.523, 2, 12)
            cubicTo(2, 6.477, 6.477, 2, 12, 2)
            cubicTo(17.523, 2, 22, 6.477, 22, 12)
            close()
            moveTo(12, 8)
            lineTo(12, 12)
            moveTo(12, 16)
            lineTo(12.01, 16)
        }
    }

    val TriangleAlert: ImageVector by lazy {
        arsipIcon("triangle-alert") {
            moveTo(21.73, 18)
            lineTo(13.73, 4)
            cubicTo(13.375, 3.373, 12.71, 2.986, 11.99, 2.986)
            cubicTo(11.27, 2.986, 10.605, 3.373, 10.25, 4)
            lineTo(2.25, 18)
            cubicTo(1.891, 18.622, 1.893, 19.388, 2.254, 20.008)
            cubicTo(2.616, 20.628, 3.282, 21.006, 4, 21)
            lineTo(20, 21)
            cubicTo(20.714, 20.999, 21.374, 20.618, 21.731, 19.999)
            cubicTo(22.087, 19.38, 22.087, 18.618, 21.73, 18)
            moveTo(12, 9)
            lineTo(12, 13)
            moveTo(12, 17)
            lineTo(12.01, 17)
        }
    }

    val CircleX: ImageVector by lazy {
        arsipIcon("circle-x") {
            moveTo(22, 12)
            cubicTo(22, 17.523, 17.523, 22, 12, 22)
            cubicTo(6.477, 22, 2, 17.523, 2, 12)
            cubicTo(2, 6.477, 6.477, 2, 12, 2)
            cubicTo(17.523, 2, 22, 6.477, 22, 12)
            close()
            moveTo(15, 9)
            lineTo(9, 15)
            moveTo(9, 9)
            lineTo(15, 15)
        }
    }

    val CircleCheck: ImageVector by lazy {
        arsipIcon("circle-check") {
            moveTo(22, 12)
            cubicTo(22, 17.523, 17.523, 22, 12, 22)
            cubicTo(6.477, 22, 2, 17.523, 2, 12)
            cubicTo(2, 6.477, 6.477, 2, 12, 2)
            cubicTo(17.523, 2, 22, 6.477, 22, 12)
            close()
            moveTo(16, 9)
            lineTo(10.5, 14.5)
            lineTo(8, 12)
        }
    }

    val Fingerprint: ImageVector by lazy {
        arsipIcon("fingerprint") {
            moveTo(12, 10)
            cubicTo(10.895, 10, 10, 10.895, 10, 12)
            cubicTo(10, 13.02, 9.9, 14.51, 9.74, 16)
            moveTo(14, 13.12)
            cubicTo(14, 15.5, 14, 19.5, 13, 22)
            moveTo(17.29, 21.02)
            cubicTo(17.41, 20.42, 17.72, 18.72, 17.79, 18)
            moveTo(2, 12)
            cubicTo(2, 7.696, 4.754, 3.874, 8.838, 2.513)
            cubicTo(12.921, 1.152, 17.417, 2.557, 20, 6)
            moveTo(2, 16)
            lineTo(2.01, 16)
            moveTo(21.8, 16)
            cubicTo(22, 14, 21.931, 10.646, 21.8, 10)
            moveTo(5, 19.5)
            cubicTo(5.5, 18, 6, 15, 6, 12)
            cubicTo(5.999, 11.319, 6.114, 10.643, 6.34, 10)
            moveTo(8.65, 22)
            cubicTo(8.86, 21.34, 9.1, 20.68, 9.22, 20)
            moveTo(9, 6.8)
            cubicTo(10.857, 5.728, 13.145, 5.728, 15.002, 6.801)
            cubicTo(16.858, 7.874, 18.001, 9.856, 18, 12)
            lineTo(18, 14)
        }
    }

    val KeyRound: ImageVector by lazy {
        arsipIcon("key-round") {
            moveTo(2.586, 17.414)
            cubicTo(2.211, 17.789, 2, 18.298, 2, 18.828)
            lineTo(2, 21)
            cubicTo(2, 21.552, 2.448, 22, 3, 22)
            lineTo(6, 22)
            cubicTo(6.552, 22, 7, 21.552, 7, 21)
            lineTo(7, 20)
            cubicTo(7, 19.448, 7.448, 19, 8, 19)
            lineTo(9, 19)
            cubicTo(9.552, 19, 10, 18.552, 10, 18)
            lineTo(10, 17)
            cubicTo(10, 16.448, 10.448, 16, 11, 16)
            lineTo(11.172, 16)
            cubicTo(11.702, 16, 12.211, 15.789, 12.586, 15.414)
            lineTo(13.4, 14.6)
            cubicTo(16.262, 15.597, 19.434, 14.484, 21.044, 11.916)
            cubicTo(22.655, 9.349, 22.278, 6.009, 20.134, 3.866)
            cubicTo(17.991, 1.722, 14.651, 1.345, 12.084, 2.956)
            cubicTo(9.516, 4.566, 8.403, 7.738, 9.4, 10.6)
            close()
            moveTo(17, 7.5)
            cubicTo(17, 7.776, 16.776, 8, 16.5, 8)
            cubicTo(16.224, 8, 16, 7.776, 16, 7.5)
            cubicTo(16, 7.224, 16.224, 7, 16.5, 7)
            cubicTo(16.776, 7, 17, 7.224, 17, 7.5)
            close()
        }
    }

    val Smartphone: ImageVector by lazy {
        arsipIcon("smartphone") {
            moveTo(7, 2)
            lineTo(17, 2)
            cubicTo(18.105, 2, 19, 2.895, 19, 4)
            lineTo(19, 20)
            cubicTo(19, 21.105, 18.105, 22, 17, 22)
            lineTo(7, 22)
            cubicTo(5.895, 22, 5, 21.105, 5, 20)
            lineTo(5, 4)
            cubicTo(5, 2.895, 5.895, 2, 7, 2)
            close()
            moveTo(12, 18)
            lineTo(12.01, 18)
        }
    }

    val ShieldCheck: ImageVector by lazy {
        arsipIcon("shield-check") {
            moveTo(20, 13)
            cubicTo(20, 18, 16.5, 20.5, 12.34, 21.95)
            cubicTo(12.122, 22.024, 11.886, 22.02, 11.67, 21.94)
            cubicTo(7.5, 20.5, 4, 18, 4, 13)
            lineTo(4, 6)
            cubicTo(4, 5.448, 4.448, 5, 5, 5)
            cubicTo(7, 5, 9.5, 3.8, 11.24, 2.28)
            cubicTo(11.678, 1.906, 12.322, 1.906, 12.76, 2.28)
            cubicTo(14.51, 3.81, 17, 5, 19, 5)
            cubicTo(19.552, 5, 20, 5.448, 20, 6)
            close()
            moveTo(9, 12)
            lineTo(11, 14)
            lineTo(15, 10)
        }
    }

    val Calendar: ImageVector by lazy {
        arsipIcon("calendar") {
            moveTo(8, 2)
            lineTo(8, 5)
            moveTo(16, 2)
            lineTo(16, 5)
            moveTo(5, 3)
            lineTo(19, 3)
            cubicTo(20.105, 3, 21, 3.895, 21, 5)
            lineTo(21, 19)
            cubicTo(21, 20.105, 20.105, 21, 19, 21)
            lineTo(5, 21)
            cubicTo(3.895, 21, 3, 20.105, 3, 19)
            lineTo(3, 5)
            cubicTo(3, 3.895, 3.895, 3, 5, 3)
            close()
            moveTo(3, 9)
            lineTo(21, 9)
        }
    }

    val Hash: ImageVector by lazy {
        arsipIcon("hash") {
            moveTo(4, 9)
            lineTo(20, 9)
            moveTo(4, 15)
            lineTo(20, 15)
            moveTo(10, 3)
            lineTo(8, 21)
            moveTo(16, 3)
            lineTo(14, 21)
        }
    }

    val Lock: ImageVector by lazy {
        arsipIcon("lock") {
            moveTo(5, 11)
            lineTo(19, 11)
            cubicTo(20.105, 11, 21, 11.895, 21, 13)
            lineTo(21, 20)
            cubicTo(21, 21.105, 20.105, 22, 19, 22)
            lineTo(5, 22)
            cubicTo(3.895, 22, 3, 21.105, 3, 20)
            lineTo(3, 13)
            cubicTo(3, 11.895, 3.895, 11, 5, 11)
            close()
            moveTo(7, 11)
            lineTo(7, 7)
            cubicTo(7, 4.239, 9.239, 2, 12, 2)
            cubicTo(14.761, 2, 17, 4.239, 17, 7)
            lineTo(17, 11)
        }
    }

    val Unlock: ImageVector by lazy {
        arsipIcon("unlock") {
            moveTo(5, 11)
            lineTo(19, 11)
            cubicTo(20.105, 11, 21, 11.895, 21, 13)
            lineTo(21, 20)
            cubicTo(21, 21.105, 20.105, 22, 19, 22)
            lineTo(5, 22)
            cubicTo(3.895, 22, 3, 21.105, 3, 20)
            lineTo(3, 13)
            cubicTo(3, 11.895, 3.895, 11, 5, 11)
            close()
            moveTo(7, 11)
            lineTo(7, 7)
            cubicTo(6.997, 4.431, 8.942, 2.278, 11.498, 2.02)
            cubicTo(14.053, 1.762, 16.389, 3.483, 16.9, 6)
        }
    }

    val LayoutGrid: ImageVector by lazy {
        arsipIcon("layout-grid") {
            moveTo(4, 3)
            lineTo(9, 3)
            cubicTo(9.552, 3, 10, 3.448, 10, 4)
            lineTo(10, 9)
            cubicTo(10, 9.552, 9.552, 10, 9, 10)
            lineTo(4, 10)
            cubicTo(3.448, 10, 3, 9.552, 3, 9)
            lineTo(3, 4)
            cubicTo(3, 3.448, 3.448, 3, 4, 3)
            close()
            moveTo(15, 3)
            lineTo(20, 3)
            cubicTo(20.552, 3, 21, 3.448, 21, 4)
            lineTo(21, 9)
            cubicTo(21, 9.552, 20.552, 10, 20, 10)
            lineTo(15, 10)
            cubicTo(14.448, 10, 14, 9.552, 14, 9)
            lineTo(14, 4)
            cubicTo(14, 3.448, 14.448, 3, 15, 3)
            close()
            moveTo(15, 14)
            lineTo(20, 14)
            cubicTo(20.552, 14, 21, 14.448, 21, 15)
            lineTo(21, 20)
            cubicTo(21, 20.552, 20.552, 21, 20, 21)
            lineTo(15, 21)
            cubicTo(14.448, 21, 14, 20.552, 14, 20)
            lineTo(14, 15)
            cubicTo(14, 14.448, 14.448, 14, 15, 14)
            close()
            moveTo(4, 14)
            lineTo(9, 14)
            cubicTo(9.552, 14, 10, 14.448, 10, 15)
            lineTo(10, 20)
            cubicTo(10, 20.552, 9.552, 21, 9, 21)
            lineTo(4, 21)
            cubicTo(3.448, 21, 3, 20.552, 3, 20)
            lineTo(3, 15)
            cubicTo(3, 14.448, 3.448, 14, 4, 14)
            close()
        }
    }

    val List: ImageVector by lazy {
        arsipIcon("list") {
            moveTo(3, 5)
            lineTo(3.01, 5)
            moveTo(3, 12)
            lineTo(3.01, 12)
            moveTo(3, 19)
            lineTo(3.01, 19)
            moveTo(8, 5)
            lineTo(21, 5)
            moveTo(8, 12)
            lineTo(21, 12)
            moveTo(8, 19)
            lineTo(21, 19)
        }
    }

    val ListOrdered: ImageVector by lazy {
        arsipIcon("list-ordered") {
            moveTo(11, 5)
            lineTo(21, 5)
            moveTo(11, 12)
            lineTo(21, 12)
            moveTo(11, 19)
            lineTo(21, 19)
            moveTo(4, 4)
            lineTo(5, 4)
            lineTo(5, 9)
            moveTo(4, 9)
            lineTo(6, 9)
            moveTo(6.5, 20)
            lineTo(3.4, 20)
            cubicTo(3.4, 19, 6, 18.075, 6, 16.5)
            cubicTo(6, 15.883, 5.622, 15.329, 5.048, 15.103)
            cubicTo(4.473, 14.878, 3.82, 15.027, 3.4, 15.48)
        }
    }

    val LoaderCircle: ImageVector by lazy {
        arsipIcon("loader-circle") {
            moveTo(21, 12)
            cubicTo(21, 15.899, 18.489, 19.354, 14.781, 20.559)
            cubicTo(11.073, 21.764, 7.01, 20.444, 4.719, 17.29)
            cubicTo(2.427, 14.135, 2.427, 9.864, 4.719, 6.709)
            cubicTo(7.011, 3.555, 11.073, 2.235, 14.781, 3.44)
        }
    }

    val WifiOff: ImageVector by lazy {
        arsipIcon("wifi-off") {
            moveTo(12, 20)
            lineTo(12.01, 20)
            moveTo(8.5, 16.429)
            cubicTo(10.444, 14.523, 13.556, 14.523, 15.5, 16.429)
            moveTo(5, 12.859)
            cubicTo(6.418, 11.469, 8.218, 10.532, 10.17, 10.169)
            moveTo(19, 12.859)
            cubicTo(18.398, 12.269, 17.724, 11.757, 16.993, 11.336)
            moveTo(2, 8.82)
            cubicTo(3.237, 7.714, 4.648, 6.821, 6.177, 6.177)
            moveTo(22, 8.82)
            cubicTo(18.917, 6.063, 14.833, 4.701, 10.712, 5.056)
            moveTo(2, 2)
            lineTo(22, 22)
        }
    }

    val ExternalLink: ImageVector by lazy {
        arsipIcon("external-link") {
            moveTo(15, 3)
            lineTo(21, 3)
            lineTo(21, 9)
            moveTo(10, 14)
            lineTo(21, 3)
            moveTo(18, 13)
            lineTo(18, 19)
            cubicTo(18, 20.105, 17.105, 21, 16, 21)
            lineTo(5, 21)
            cubicTo(3.895, 21, 3, 20.105, 3, 19)
            lineTo(3, 8)
            cubicTo(3, 6.895, 3.895, 6, 5, 6)
            lineTo(11, 6)
        }
    }

    val Clock: ImageVector by lazy {
        arsipIcon("clock") {
            moveTo(22, 12)
            cubicTo(22, 17.523, 17.523, 22, 12, 22)
            cubicTo(6.477, 22, 2, 17.523, 2, 12)
            cubicTo(2, 6.477, 6.477, 2, 12, 2)
            cubicTo(17.523, 2, 22, 6.477, 22, 12)
            close()
            moveTo(12, 6)
            lineTo(12, 12)
            lineTo(16, 14)
        }
    }

    val BookOpen: ImageVector by lazy {
        arsipIcon("book-open") {
            moveTo(12, 5)
            lineTo(12, 21)
            moveTo(20.001, 19)
            cubicTo(21.105, 18.999, 22, 18.104, 22, 17)
            lineTo(22, 5)
            cubicTo(22, 3.896, 21.105, 3.001, 20.001, 3)
            lineTo(16, 3.002)
            cubicTo(14.427, 3.002, 12.945, 3.742, 12, 5)
            cubicTo(11.056, 3.741, 9.574, 3, 8, 3)
            lineTo(4, 3)
            cubicTo(2.895, 3, 2, 3.895, 2, 5)
            lineTo(2, 17)
            cubicTo(2, 18.104, 2.895, 18.999, 3.999, 19)
            lineTo(8, 19)
            cubicTo(9.574, 19, 11.056, 19.741, 12, 21)
            cubicTo(12.944, 19.741, 14.426, 19, 16, 19)
            close()
        }
    }

    val Bookmark: ImageVector by lazy {
        arsipIcon("bookmark") {
            moveTo(17, 3)
            cubicTo(18.105, 3, 19, 3.895, 19, 5)
            lineTo(19, 20)
            cubicTo(19, 20.356, 18.81, 20.686, 18.502, 20.865)
            cubicTo(18.194, 21.043, 17.813, 21.045, 17.504, 20.868)
            lineTo(12.992, 18.29)
            cubicTo(12.377, 17.939, 11.623, 17.939, 11.008, 18.29)
            lineTo(6.496, 20.868)
            cubicTo(6.187, 21.045, 5.806, 21.043, 5.498, 20.865)
            cubicTo(5.19, 20.686, 5, 20.356, 5, 20)
            lineTo(5, 5)
            cubicTo(5, 3.895, 5.895, 3, 7, 3)
            close()
        }
    }

    val Infinity: ImageVector by lazy {
        arsipIcon("infinity") {
            moveTo(6, 16)
            cubicTo(11, 16, 13, 8, 18, 8)
            cubicTo(20.209, 8, 22, 9.791, 22, 12)
            cubicTo(22, 14.209, 20.209, 16, 18, 16)
            cubicTo(13, 16, 11, 8, 6, 8)
            cubicTo(3.791, 8, 2, 9.791, 2, 12)
            cubicTo(2, 14.209, 3.791, 16, 6, 16)
        }
    }

    val Scissors: ImageVector by lazy {
        arsipIcon("scissors") {
            moveTo(9, 6)
            cubicTo(9, 7.657, 7.657, 9, 6, 9)
            cubicTo(4.343, 9, 3, 7.657, 3, 6)
            cubicTo(3, 4.343, 4.343, 3, 6, 3)
            cubicTo(7.657, 3, 9, 4.343, 9, 6)
            close()
            moveTo(8.12, 8.12)
            lineTo(12, 12)
            moveTo(20, 4)
            lineTo(8.12, 15.88)
            moveTo(9, 18)
            cubicTo(9, 19.657, 7.657, 21, 6, 21)
            cubicTo(4.343, 21, 3, 19.657, 3, 18)
            cubicTo(3, 16.343, 4.343, 15, 6, 15)
            cubicTo(7.657, 15, 9, 16.343, 9, 18)
            close()
            moveTo(14.8, 14.8)
            lineTo(20, 20)
        }
    }

    val Stamp: ImageVector by lazy {
        arsipIcon("stamp") {
            moveTo(14, 13)
            lineTo(14, 8.5)
            cubicTo(14, 7, 15, 7, 15, 5)
            cubicTo(15, 3.343, 13.657, 2, 12, 2)
            cubicTo(10.343, 2, 9, 3.343, 9, 5)
            cubicTo(9, 7, 10, 7, 10, 8.5)
            lineTo(10, 13)
            moveTo(20, 15.5)
            cubicTo(20, 14.119, 18.881, 13, 17.5, 13)
            lineTo(6.5, 13)
            cubicTo(5.119, 13, 4, 14.119, 4, 15.5)
            lineTo(4, 17)
            cubicTo(4, 17.552, 4.448, 18, 5, 18)
            lineTo(19, 18)
            cubicTo(19.552, 18, 20, 17.552, 20, 17)
            close()
            moveTo(5, 22)
            lineTo(19, 22)
        }
    }

    private fun arsipIcon(name: String, block: JalurAngka.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(
            pathData = JalurAngka().apply(block).nodes,
            fill = null,
            stroke = SolidColor(Color(0xFFFFFFFF)),
            strokeLineWidth = 2f,
            pathFillType = PathFillType.NonZero,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ).build()
}
