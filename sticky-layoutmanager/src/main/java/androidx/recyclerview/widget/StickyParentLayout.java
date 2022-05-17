package androidx.recyclerview.widget;

import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.RequiresApi;

/**
 * Created on 2022/5/17
 * Author: wangxiaojie
 * Description:
 */
public class StickyParentLayout extends ViewGroup {

    public StickyParentLayout(Context context) {
        super(context);
    }

    public StickyParentLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public StickyParentLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public StickyParentLayout(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int count = getChildCount();
        if (count == 0) {
            setMeasuredDimension(0, 0);
            return;
        }
        View child = getChildAt(0);
        measureChild(child, widthMeasureSpec, heightMeasureSpec);
        setMeasuredDimension(child.getMeasuredWidth(), child.getMeasuredHeight());
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int count = getChildCount();
        if (count == 0) {
            return;
        }
        View child = getChildAt(0);
        child.layout(l, t, r, b);
    }

    void addStickyHeader(View view) {
        addView(view);
    }

    void removeStickyHeader(View view) {
        removeView(view);
    }
}
