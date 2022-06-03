package io.brainergy.prescreen.demo

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat

class BoundingBoxOverlay constructor(context: Context?, attributeSet: AttributeSet?) :
    View(context, attributeSet) {

    private val bboxes: MutableList<Rect> = mutableListOf()
    private val faceBBoxes : MutableList<Rect> = mutableListOf()
    private var hiBox: Rect? = null
    private var rotationDegree: Int = 0
    private val paint1 = Paint().apply {
        style = Paint.Style.STROKE
        color = ContextCompat.getColor(context!!, android.R.color.holo_green_light)
        strokeWidth = 7f
    }
    private val paint2 = Paint().apply {
        style = Paint.Style.STROKE
        color = ContextCompat.getColor(context!!, android.R.color.holo_red_light)
        strokeWidth = 7f
    }
    private val paint3 = Paint().apply {
        style = Paint.Style.STROKE
        color = ContextCompat.getColor(context!!, android.R.color.holo_blue_bright)
        strokeWidth = 7f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
//        canvas.save()
//        canvas.rotate(this.rotationDegree.toFloat() - 90, canvas.width.toFloat()/2, canvas.height.toFloat()/2)
        bboxes.forEach {
            if (it.left < 0 || it.right > width || it.top < 0 || it.bottom > height) {
                canvas.drawRect(it, paint2)
            }  else {
                canvas.drawRect(it, paint1)
            }

        }
        if (this.hiBox != null) {
            canvas.drawRect(this.hiBox!!, paint3)
        }
        faceBBoxes.forEach {
            if (it.left < width * .025 || it.right > width *.975 || it.top < height * .075 || it.bottom > height*.925) {
                canvas.drawRect(it, paint2)
            }  else {
                canvas.drawRect(it, paint1)
            }
//            canvas.drawRect(it, paint3)
        }
//        canvas.restore()

    }

    fun drawBounds(
            bboxes: List<Rect>,
            faceBBoxes: List<Rect>?,
            highlightedBox: Rect?,
            rotationDegree: Int) {
        this.rotationDegree = rotationDegree
        this.bboxes.clear()
        this.bboxes.addAll(bboxes)
        this.faceBBoxes.clear()
        if (faceBBoxes != null){
            this.faceBBoxes.addAll(faceBBoxes)
        }
        this.hiBox = highlightedBox
        invalidate()
    }

    fun clearBounds() {
        this.bboxes.clear()
        this.hiBox = null
        invalidate()
    }
}