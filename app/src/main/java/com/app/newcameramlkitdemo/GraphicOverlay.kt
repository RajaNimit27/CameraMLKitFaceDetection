package com.app.newcameramlkitdemo

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.app.newcameramlkitdemo.GraphicOverlay.Graphic

/*
 * Copyright 2020 Google LLC. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */ /**
 * A view which renders a series of custom graphics to be overlayed on top of an associated preview
 * (i.e., the camera preview). The creator can add graphics objects, update the objects, and remove
 * them, triggering the appropriate drawing and invalidation within the view.
 *
 *
 * Supports scaling and mirroring of the graphics relative the camera's preview properties. The
 * idea is that detection items are expressed in terms of an image size, but need to be scaled up
 * to the full view size, and also mirrored in the case of the front-facing camera.
 *
 *
 * Associated [Graphic] items should use the following methods to convert to view
 * coordinates for the graphics that are drawn:
 *
 *
 *  1. [Graphic.scale] adjusts the size of the supplied value from the image scale
 * to the view scale.
 *  1. [Graphic.translateX] and [Graphic.translateY] adjust the
 * coordinate from the image's coordinate system to the view coordinate system.
 *
 */
class GraphicOverlay(context: Context?, attrs: AttributeSet?) : View(context, attrs) {
    private val lock = Any()
    private val graphics: MutableList<Graphic> = ArrayList()

    // Matrix for transforming from image coordinates to overlay view coordinates.
    private val transformationMatrix = Matrix()
    var imageWidth = 0
        private set
    var imageHeight = 0
        private set

    // The factor of overlay View size to image size. Anything in the image coordinates need to be
    // scaled by this amount to fit with the area of overlay View.
    private var scaleFactor = 1.0f

    // The number of horizontal pixels needed to be cropped on each side to fit the image with the
    // area of overlay View after scaling.
    private var postScaleWidthOffset = 0f

    // The number of vertical pixels needed to be cropped on each side to fit the image with the
    // area of overlay View after scaling.
    private var postScaleHeightOffset = 0f
    private var isImageFlipped = false
    private var needUpdateTransformation = true
    private var transparentPaint: Paint? = null
    private var ovalPaint: Paint? = null
    private var centerX = 0
    private  var centerY = 0
    private var ovalWidth = 0
    private  var ovalHeight = 0

    init {
        addOnLayoutChangeListener { view: View?, left: Int, top: Int, right: Int, bottom: Int, oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int -> needUpdateTransformation = true }
        init()
    }

    /**
     * Removes all graphics from the overlay.
     */
    fun clear() {
        synchronized(lock) { graphics.clear() }
        postInvalidate()
    }
    private fun init() {
        transparentPaint = Paint().apply {
            color = Color.TRANSPARENT
            style = Paint.Style.FILL
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }

        ovalPaint = Paint().apply {
            color = Color.RED // Set the color of the oval
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }

    }

    /**
     * Adds a graphic to the overlay.
     */
    fun add(graphic: Graphic) {
        synchronized(lock) { graphics.add(graphic) }
    }

    /**
     * Removes a graphic from the overlay.
     */
    fun remove(graphic: Graphic) {
        synchronized(lock) { graphics.remove(graphic) }
        postInvalidate()
    }

    /**
     * Sets the source information of the image being processed by detectors, including size and
     * whether it is flipped, which informs how to transform image coordinates later.
     *
     * @param imageWidth  the width of the image sent to ML Kit detectors
     * @param imageHeight the height of the image sent to ML Kit detectors
     * @param isFlipped   whether the image is flipped. Should set it to true when the image is from the
     * front camera.
     */
    fun setImageSourceInfo(imageWidth: Int, imageHeight: Int, isFlipped: Boolean) {
        // Preconditions.checkState(imageWidth > 0, "image width must be positive");
        // Preconditions.checkState(imageHeight > 0, "image height must be positive");
        synchronized(lock) {
            this.imageWidth = imageWidth
            this.imageHeight = imageHeight
            isImageFlipped = isFlipped
            needUpdateTransformation = true
        }
        postInvalidate()
    }

    private fun updateTransformationIfNeeded() {
        try {
            if (!needUpdateTransformation || imageWidth <= 0 || imageHeight <= 0) {
                return
            }
            val viewAspectRatio = width.toFloat() / height
            val imageAspectRatio = imageWidth.toFloat() / imageHeight
            postScaleWidthOffset = 0f
            postScaleHeightOffset = 0f
            if (viewAspectRatio > imageAspectRatio) {
                // The image needs to be vertically cropped to be displayed in this view.
                scaleFactor = width.toFloat() / imageWidth
                postScaleHeightOffset = (width.toFloat() / imageAspectRatio - height) / 2
            } else {
                // The image needs to be horizontally cropped to be displayed in this view.
                scaleFactor = height.toFloat() / imageHeight
                postScaleWidthOffset = (height.toFloat() * imageAspectRatio - width) / 2
            }
            transformationMatrix.reset()
            transformationMatrix.setScale(scaleFactor, scaleFactor)
            transformationMatrix.postTranslate(-postScaleWidthOffset, -postScaleHeightOffset)
            if (isImageFlipped) {
                transformationMatrix.postScale(-1f, 1f, width / 2f, height / 2f)
            }
            needUpdateTransformation = false
        } catch (e: Exception) {
        }
    }

    /**
     * Draws the overlay with its associated graphic objects.
     */
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        synchronized(lock) {
            try {
                updateTransformationIfNeeded()
                for (graphic in graphics) {
                    graphic.draw(canvas)
                }
                val width = width
                val height = height
                centerX = width / 2
                centerY = height / 2
                ovalWidth = width / 2 // Set the width of the oval

                ovalHeight = height / 4 // Set the height of the oval


                // Draw a transparent overlay with a centered oval

                // Draw a transparent overlay with a centered oval
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), transparentPaint!!)
                val ovalRect = RectF(
                    (centerX - ovalWidth).toFloat(),
                    (centerY - ovalHeight).toFloat(),
                    (centerX + ovalWidth).toFloat(),
                    (centerY + ovalHeight).toFloat()
                )
                canvas.drawOval(ovalRect, ovalPaint!!)
            } catch (e: Exception) {
            }
        }
    }

    /**
     * Base class for a custom graphics object to be rendered within the graphic overlay. Subclass
     * this and implement the [Graphic.draw] method to define the graphics element. Add
     * instances to the overlay using [GraphicOverlay.add].
     */
    abstract class Graphic(private val overlay: GraphicOverlay?) {
        /**
         * Draw the graphic on the supplied canvas. Drawing should use the following methods to convert
         * to view coordinates for the graphics that are drawn:
         *
         *
         *  1. [Graphic.scale] adjusts the size of the supplied value from the image
         * scale to the view scale.
         *  1. [Graphic.translateX] and [Graphic.translateY] adjust the
         * coordinate from the image's coordinate system to the view coordinate system.
         *
         *
         * @param canvas drawing canvas
         */
        abstract fun draw(canvas: Canvas?)

        /**
         * Adjusts the supplied value from the image scale to the view scale.
         */
        fun scale(imagePixel: Float): Float {
            return imagePixel * overlay?.scaleFactor!!
        }

        /**
         * Returns the application context of the app.
         */
        val applicationContext: Context?
            get() = overlay?.context?.applicationContext

        fun isImageFlipped(): Boolean? {
            return overlay?.isImageFlipped
        }

        /**
         * Adjusts the x coordinate from the image's coordinate system to the view coordinate system.
         */
        fun translateX(x: Float): Float {
            return if (overlay?.isImageFlipped == true) {
                overlay.width - (scale(x) - overlay.postScaleWidthOffset)
            } else {
                scale(x) - overlay?.postScaleWidthOffset!!
            }
        }

        /**
         * Adjusts the y coordinate from the image's coordinate system to the view coordinate system.
         */
        fun translateY(y: Float): Float {
            return scale(y) - overlay?.postScaleHeightOffset!!
        }

        /**
         * Returns a [Matrix] for transforming from image coordinates to overlay view coordinates.
         */
        fun getTransformationMatrix(): Matrix? {
            return overlay?.transformationMatrix
        }

        fun postInvalidate() {
            overlay?.postInvalidate()
        }
    }
}