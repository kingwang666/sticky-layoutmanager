package androidx.recyclerview.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.view.ViewGroup

/**
 * Created on 2022/5/17
 * Author: wangxiaojie
 * Description:
 */
class StickyParentLayout : ViewGroup {

    private val mRect = Rect()

    constructor(context: Context) : super(context) {}
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {}
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
    }

    constructor(
        context: Context?,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes) {
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val count = childCount
        if (count == 0) {
            setMeasuredDimension(0, 0)
            return
        }
        val child = getChildAt(0)
        measureChild(child, widthMeasureSpec, heightMeasureSpec)
        setMeasuredDimension(child.measuredWidth + paddingStart + paddingEnd, child.measuredHeight + paddingTop + paddingBottom)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val count = childCount
        if (count == 0) {
            return
        }
        val child = getChildAt(0)
        child.layout(l + paddingStart, t + paddingTop, r, b)
    }

    internal fun addStickyHeader(view: View) {
        addViewInLayout(view, -1, view.layoutParams)
    }

    override fun dispatchDraw(canvas: Canvas) {
        val view = if (childCount == 0) null else getChildAt(0)
        if (view is ViewGroup && view.clipToPadding) {
            canvas.save()
            mRect.set(
                view.paddingStart,
                view.paddingTop,
                view.width - view.paddingEnd,
                view.height - view.paddingBottom
            )
            canvas.clipRect(mRect)
            super.dispatchDraw(canvas)
            canvas.restore()
        } else {
            super.dispatchDraw(canvas)
        }
    }

    internal fun removeStickyHeader(view: View) {
        removeViewInLayout(view)
    }

    internal fun removeStickyHeaders() {
        val count = childCount - 1
        if (count > 0) {
            removeViewsInLayout(1, count)
        }
    }
}