package androidx.recyclerview.widget

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup

/**
 * Created on 2022/5/17
 * Author: wangxiaojie
 * Description:
 */
class StickyParentLayout : ViewGroup {

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
        setMeasuredDimension(child.measuredWidth, child.measuredHeight)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val count = childCount
        if (count == 0) {
            return
        }
        val child = getChildAt(0)
        child.layout(l, t, r, b)
    }

    internal fun addStickyHeader(view: View) {
        addViewInLayout(view, -1, view.layoutParams)
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