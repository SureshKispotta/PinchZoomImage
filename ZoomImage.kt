package com.sk.zoomimage

import android.content.Context
import android.graphics.Matrix
import android.graphics.PointF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.annotation.Nullable
import androidx.appcompat.widget.AppCompatImageView

/**
 * Class to provide the Place holder for the image view with pinch to zoom, double tap to reset and
 * zoom feature.
 * This feature work with scrollable container also.
 * Image content will be placed to the center of the container and on double tap image will get zoom
 * from teh center position of the content image with the help on the image matrix.
 *
 * @author Suresh Kispotta
 * @since 10/01/2022
 */
class ZoomImage : AppCompatImageView, View.OnTouchListener,
    GestureDetector.OnGestureListener, GestureDetector.OnDoubleTapListener {

    private var mContext: Context? = null
    private var mScaleDetector: ScaleGestureDetector? = null
    private var mGestureDetector: GestureDetector? = null
    var mMatrix: Matrix? = null
    private var mMatrixValues: FloatArray? = null
    var mode = NONE
    var mSaveScale = 1f
    var mMinScale = 1f
    var mMaxScale = 4f
    var origWidth = 0f
    var origHeight = 0f
    var viewWidth = 0
    var viewHeight = 0
    private var mLast = PointF()
    private var mStart = PointF()
    private var isError = false

    constructor(context: Context) : super(context) {
        init(context)
    }

    constructor(context: Context, @Nullable attrs: AttributeSet?) : super(context, attrs) {
        init(context)
    }

    constructor(
        context: Context?, attrs: AttributeSet?,
        defStyleAttr: Int
    ) : super(context!!, attrs, defStyleAttr) {
        init(context)
    }

    /**
     * Init method to initialization of the required field.*/
    private fun init(context: Context) {
        super.setClickable(true)
        mContext = context
        mScaleDetector = ScaleGestureDetector(context, ScaleListener())
        mMatrix = Matrix()
        mMatrixValues = FloatArray(9)
        imageMatrix = mMatrix
        scaleType = ScaleType.MATRIX
        mGestureDetector = GestureDetector(context, this)
        setOnTouchListener(this)
    }

    /**
     * Taking error status to block zooming feature is error
     * while loading the image.*/
    fun setError(error : Boolean) {
        isError = error
    }

    /**
     * Method to reset the preview image to its original state.*/
    fun resetPreview() {
        if(mSaveScale > mMinScale) {
            fitToScreen()
        }
    }

    /**
     * Listener class to listen the user Pinch action to the container view.
     * and with move action, Scaling the image with respect to to the scale factor distance.
     */
    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            parent.requestDisallowInterceptTouchEvent(true)
            mode = ZOOM
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            parent.requestDisallowInterceptTouchEvent(true)
            var mScaleFactor = detector.scaleFactor
            val prevScale = mSaveScale
            mSaveScale *= mScaleFactor
            if (mSaveScale > mMaxScale) {
                mSaveScale = mMaxScale
                mScaleFactor = mMaxScale / prevScale
            } else if (mSaveScale < mMinScale) {
                mSaveScale = mMinScale
                mScaleFactor = mMinScale / prevScale
            }
            if (origWidth * mSaveScale <= viewWidth
                || origHeight * mSaveScale <= viewHeight
            ) {
                mMatrix!!.postScale(
                    mScaleFactor, mScaleFactor, viewWidth / 2.toFloat(),
                    viewHeight / 2.toFloat()
                )
            } else {
                mMatrix!!.postScale(
                    mScaleFactor, mScaleFactor,
                    detector.focusX, detector.focusY
                )
            }
            fixTranslation()
            return true
        }
    }

    fun fixTranslation() {
        mMatrix!!.getValues(mMatrixValues)
        val transX =
            mMatrixValues!![Matrix.MTRANS_X]
        val transY =
            mMatrixValues!![Matrix.MTRANS_Y]
        val fixTransX = getFixTranslation(transX, viewWidth.toFloat(), origWidth * mSaveScale)
        val fixTransY = getFixTranslation(transY, viewHeight.toFloat(), origHeight * mSaveScale)
        if (fixTransX != 0f || fixTransY != 0f) mMatrix!!.postTranslate(fixTransX, fixTransY)
    }

    private fun getFixTranslation(trans: Float, viewSize: Float, contentSize: Float): Float {
        val minTrans: Float
        val maxTrans: Float
        if (contentSize <= viewSize) {
            minTrans = 0f
            maxTrans = viewSize - contentSize
        } else {
            minTrans = viewSize - contentSize
            maxTrans = 0f
        }
        if (trans < minTrans) {
            return -trans + minTrans
        }
        if (trans > maxTrans) {
            return -trans + maxTrans
        }
        return 0F
    }

    private fun getFixDragTrans(delta: Float, viewSize: Float, contentSize: Float): Float {
        return if (contentSize <= viewSize) {
            0F
        } else delta
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        viewWidth = MeasureSpec.getSize(widthMeasureSpec)
        viewHeight = MeasureSpec.getSize(heightMeasureSpec)
        if (mSaveScale == 1f) {
            //Reset to initial position.
            fitToScreen()
        }
    }

    /**
     * OnTouch method..*/
    override fun onTouch(view: View?, event: MotionEvent): Boolean {
        mScaleDetector!!.onTouchEvent(event)
        mGestureDetector!!.onTouchEvent(event)
        var consume = false
        parent.requestDisallowInterceptTouchEvent(false)
        val currentPoint = PointF(event.x, event.y)
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                mLast.set(currentPoint)
                mStart.set(mLast)
                mode = DRAG
                consume = mSaveScale > mMinScale
            }

            MotionEvent.ACTION_MOVE -> {
                if (mode == ZOOM || mode == DRAG) {
                    val dx = currentPoint.x - mLast.x
                    val dy = currentPoint.y - mLast.y
                    val fixTransX =
                        getFixDragTrans(dx, viewWidth.toFloat(), origWidth * mSaveScale)
                    val fixTransY =
                        getFixDragTrans(dy, viewHeight.toFloat(), origHeight * mSaveScale)
                    mMatrix!!.postTranslate(fixTransX, fixTransY)
                    fixTranslation()
                    mLast[currentPoint.x] = currentPoint.y
                }
                consume = mSaveScale > mMinScale
            }

            MotionEvent.ACTION_POINTER_UP,
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                mode = NONE
                consume = false
            }
        }
        parent.requestDisallowInterceptTouchEvent(consume)
        imageMatrix = mMatrix
        return consume
    }

    override fun onDown(motionEvent: MotionEvent): Boolean {
        return false
    }

    override fun onShowPress(motionEvent: MotionEvent) {
        //Method will get called on motion progress
    }

    override fun onSingleTapUp(motionEvent: MotionEvent): Boolean {
        return false
    }

    override fun onScroll(
        motionEvent: MotionEvent,
        motionEvent1: MotionEvent,
        v: Float,
        v1: Float
    ): Boolean {
        return false
    }

    override fun onLongPress(motionEvent: MotionEvent) {
        //Method will get called on long press event.
    }

    override fun onFling(
        motionEvent: MotionEvent,
        motionEvent1: MotionEvent,
        v: Float,
        v1: Float
    ): Boolean {
        return false
    }

    override fun onSingleTapConfirmed(motionEvent: MotionEvent): Boolean {
        return false
    }

    /**
     * Handling double tap action.*/
    override fun onDoubleTap(motionEvent: MotionEvent): Boolean {
        if (mSaveScale > mMinScale) {
            fitToScreen()
        } else {
            scaleImage(mMaxScale / 2.toFloat())
        }
        return false
    }

    override fun onDoubleTapEvent(motionEvent: MotionEvent): Boolean {
        return false
    }

    /**
     * Reset the image to the initial position in the container.*/
    private fun fitToScreen() {
        mSaveScale = 1f
        val scale: Float
        val drawable = drawable
        if (drawable == null || drawable.intrinsicWidth == 0 || drawable.intrinsicHeight == 0) return
        val imageWidth = drawable.intrinsicWidth
        val imageHeight = drawable.intrinsicHeight
        val scaleX = viewWidth.toFloat() / imageWidth.toFloat()
        val scaleY = viewHeight.toFloat() / imageHeight.toFloat()
        scale = scaleX.coerceAtMost(scaleY)
        mMatrix!!.setScale(scale, scale)

        // Center the image
        var redundantYSpace = (viewHeight.toFloat()
                - scale * imageHeight.toFloat())
        var redundantXSpace = (viewWidth.toFloat()
                - scale * imageWidth.toFloat())
        redundantYSpace /= 2.toFloat()
        redundantXSpace /= 2.toFloat()
        mMatrix!!.postTranslate(redundantXSpace, redundantYSpace)
        origWidth = viewWidth - 2 * redundantXSpace
        origHeight = viewHeight - 2 * redundantYSpace
        imageMatrix = mMatrix
    }

    /**
     * Scaling the image on double tap of the container image view.*/
    private fun scaleImage(factor: Float) {
        var mScaleFactor = factor
        val prevScale = mSaveScale
        mSaveScale *= mScaleFactor
        if (mSaveScale > mMaxScale) {
            mSaveScale = mMaxScale
            mScaleFactor = mMaxScale / prevScale
        } else if (mSaveScale < mMinScale) {
            mSaveScale = mMinScale
            mScaleFactor = mMinScale / prevScale
        }
        mMatrix!!.postScale(
            mScaleFactor, mScaleFactor,
            viewWidth / 2.toFloat(), viewHeight / 2.toFloat()
        )
        fixTranslation()
    }

    /**
     * Action status constant.*/
    companion object {
        const val NONE = 0
        const val DRAG = 1
        const val ZOOM = 2
    }
}