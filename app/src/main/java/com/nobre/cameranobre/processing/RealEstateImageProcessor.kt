package com.nobre.cameranobre.processing

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream

object RealEstateImageProcessor {

    /**
     * Processa a imagem garantindo orientação 100% vertical correta,
     * ancoragem de ponto preto (sem névoa) e contraste imobiliário.
     */
    fun processImage(inputFile: File, outputFile: File, quality: Int = 95) {
        try {
            // 1. Ler a rotação física registrada pelo sensor no EXIF original
            val exif = ExifInterface(inputFile.absolutePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            val rotationDegrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }

            // 2. Decodificar o bitmap
            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val rawBitmap = BitmapFactory.decodeFile(inputFile.absolutePath, options) ?: return

            // 3. Rotacionar fisicamente o bitmap para que nunca fique de lado na galeria
            val uprightBitmap = if (rotationDegrees != 0f) {
                val matrix = Matrix().apply { postRotate(rotationDegrees) }
                val rotated = Bitmap.createBitmap(
                    rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true
                )
                if (rotated != rawBitmap) {
                    rawBitmap.recycle()
                }
                rotated
            } else {
                rawBitmap
            }

            // 4. Aplicar o color grading imobiliário (ponto de preto + saturação seletiva)
            val processed = applyRealEstateGrading(uprightBitmap)

            // 5. Salvar o JPEG final upright
            FileOutputStream(outputFile).use { out ->
                processed.compress(Bitmap.CompressFormat.JPEG, quality, out)
                out.flush()
            }

            if (processed != uprightBitmap) {
                processed.recycle()
            }
            uprightBitmap.recycle()

            // 6. Garantir que o novo arquivo tenha orientação NORMAL no EXIF
            val outExif = ExifInterface(outputFile.absolutePath)
            outExif.setAttribute(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL.toString()
            )
            outExif.saveAttributes()

        } catch (e: Exception) {
            e.printStackTrace()
            inputFile.copyTo(outputFile, overwrite = true)
        }
    }

    private fun applyRealEstateGrading(src: Bitmap): Bitmap {
        val width = src.width
        val height = src.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Ancoragem de pretos e contraste inteligente para interiores
        val contrast = 1.14f
        val blackPointOffset = -6f

        val cm = ColorMatrix()
        cm.setSaturation(1.18f)

        val t = (1.0f - contrast) / 2.0f * 255.0f + blackPointOffset
        val contrastMatrix = ColorMatrix(
            floatArrayOf(
                contrast * 1.02f, 0f, 0f, 0f, t + 2f,
                0f, contrast * 1.03f, 0f, 0f, t + 1f,
                0f, 0f, contrast * 0.98f, 0f, t,
                0f, 0f, 0f, 1f, 0f
            )
        )

        contrastMatrix.preConcat(cm)
        paint.colorFilter = ColorMatrixColorFilter(contrastMatrix)
        canvas.drawBitmap(src, 0f, 0f, paint)

        return output
    }
}
