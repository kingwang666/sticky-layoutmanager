package androidx.recyclerview.widget

import android.content.Context
import com.jay.widget.StickyHeaders
import androidx.recyclerview.widget.RecyclerView.AdapterDataObserver
import android.os.Parcelable
import androidx.recyclerview.widget.RecyclerView.Recycler
import android.graphics.PointF
import com.jay.widget.StickyHeaders.OnViewAttachListener
import android.view.ViewGroup
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.os.Parcel
import android.util.Log
import android.view.View
import androidx.core.view.marginBottom
import androidx.core.view.marginEnd
import androidx.core.view.marginStart
import androidx.core.view.marginTop
import java.lang.Exception
import java.util.ArrayList

/**
 *
 * Adds sticky headers capabilities to your [RecyclerView.Adapter]. It must implement [StickyHeaders] to
 * indicate which items are headers.
 *
 * @link https://github.com/Doist/RecyclerViewExtensions/blob/master/StickyHeaders
 */
class StickyHeadersLinearLayoutManager : LinearLayoutManager {

    private var mAdapter: RecyclerView.Adapter<*>? = null

    private var mTranslationX = 0f
    private var mTranslationY = 0f

    // Header positions for the currently displayed list and their observer.
    private val mHeaderPositions: MutableList<Int> = ArrayList(0)
    private val mHeaderPositionsObserver: AdapterDataObserver = HeaderPositionsAdapterDataObserver()

    // ViewHolder and dirty state.
    private var mStickyHeader: View? = null
    private var mAddToParent = false
    private var mStickyHeaderPosition = RecyclerView.NO_POSITION
    private var mSkipStickyHeader = false
    private val mPendingScrollPosition = RecyclerView.NO_POSITION
    private var mPendingScrollOffset = 0

    // Attach count, to ensure the sticky header is only attached and detached when expected.
    private var mStickyHeaderAttachCount = 0

    constructor(context: Context) : super(context) {}
    constructor(context: Context, orientation: Int, reverseLayout: Boolean) : super(
        context,
        orientation,
        reverseLayout
    ) {
    }

    /**
     * Offsets the vertical location of the sticky header relative to the its default position.
     */
    fun setStickyHeaderTranslationY(translationY: Float) {
        mTranslationY = translationY
        requestLayout()
    }

    /**
     * Offsets the horizontal location of the sticky header relative to the its default position.
     */
    fun setStickyHeaderTranslationX(translationX: Float) {
        mTranslationX = translationX
        requestLayout()
    }

    /**
     * Returns true if `view` is the current sticky header.
     */
    fun isStickyHeader(view: View): Boolean {
        return view === mStickyHeader
    }

    fun hasStickyHeader(): Boolean {
        return mStickyHeader != null
    }

    override fun onAttachedToWindow(view: RecyclerView) {
        super.onAttachedToWindow(view)
        setAdapter(view.adapter)
    }

    override fun onAdapterChanged(
        oldAdapter: RecyclerView.Adapter<*>?,
        newAdapter: RecyclerView.Adapter<*>?
    ) {
        super.onAdapterChanged(oldAdapter, newAdapter)
        setAdapter(newAdapter)
    }

    @Suppress("UNCHECKED_CAST")
    private fun setAdapter(adapter: RecyclerView.Adapter<*>?) {

        mAdapter?.unregisterAdapterDataObserver(mHeaderPositionsObserver)
        (mRecyclerView?.parent as? StickyParentLayout)?.removeStickyHeaders()
        removeAllViews()
        if (adapter is StickyHeaders) {
            mAdapter = adapter
            adapter.registerAdapterDataObserver(mHeaderPositionsObserver)
            mHeaderPositionsObserver.onChanged()
        } else {
            mAdapter = null
            mHeaderPositions.clear()
        }
    }

    override fun onSaveInstanceState(): Parcelable {
        val ss = SavedState()
        ss.superState = super.onSaveInstanceState()
        ss.pendingScrollPosition = mPendingScrollPosition
        ss.pendingScrollOffset = mPendingScrollOffset
        return ss
    }

    override fun onRestoreInstanceState(state: Parcelable) {
        var state: Parcelable? = state
        if (state is SavedState) {
            val ss = state
            mPendingScrollPosition = ss.pendingScrollPosition
            mPendingScrollOffset = ss.pendingScrollOffset
            state = ss.superState
        }
        super.onRestoreInstanceState(state)
    }

    override fun scrollVerticallyBy(dy: Int, recycler: Recycler, state: RecyclerView.State): Int {
        detachStickyHeader()
        val scrolled = super.scrollVerticallyBy(dy, recycler, state)
        attachStickyHeader()
        if (scrolled != 0) {
            updateStickyHeader(recycler, false)
        }
        return scrolled
    }

    override fun scrollHorizontallyBy(dx: Int, recycler: Recycler, state: RecyclerView.State): Int {
        detachStickyHeader()
        val scrolled = super.scrollHorizontallyBy(dx, recycler, state)
        attachStickyHeader()
        if (scrolled != 0) {
            updateStickyHeader(recycler, false)
        }
        return scrolled
    }

    override fun onLayoutChildren(recycler: Recycler, state: RecyclerView.State) {
        detachStickyHeader()
        super.onLayoutChildren(recycler, state)
        attachStickyHeader()
        if (!state.isPreLayout) {
            updateStickyHeader(recycler, true)
        }
    }

    override fun scrollToPosition(position: Int) {
        scrollToPositionWithOffset(position, INVALID_OFFSET)
    }

    override fun scrollToPositionWithOffset(position: Int, offset: Int) {
        scrollToPositionWithOffset(position, offset, true)
    }

    private fun scrollToPositionWithOffset(
        position: Int,
        offset: Int,
        adjustForStickyHeader: Boolean
    ) {
        // Reset pending scroll.
        setPendingScroll(RecyclerView.NO_POSITION, INVALID_OFFSET)

        // Adjusting is disabled.
        if (!adjustForStickyHeader) {
            super.scrollToPositionWithOffset(position, offset)
            return
        }

        // There is no header above or the position is a header.
        val headerIndex = findHeaderIndexOrBefore(position)
        if (headerIndex == -1 || findHeaderIndex(position) != -1) {
            super.scrollToPositionWithOffset(position, offset)
            return
        }

        // The position is right below a header, scroll to the header.
        if (findHeaderIndex(position - 1) != -1) {
            super.scrollToPositionWithOffset(position - 1, offset)
            return
        }

        // Current sticky header is the same as at the position. Adjust the scroll offset and reset pending scroll.
        if (mStickyHeader != null && headerIndex == findHeaderIndex(mStickyHeaderPosition)) {
            val adjustedOffset =
                (if (offset != INVALID_OFFSET) offset else 0) + mStickyHeader!!.height
            super.scrollToPositionWithOffset(position, adjustedOffset)
            return
        }

        // Remember this position and offset and scroll to it to trigger creating the sticky header.
        setPendingScroll(position, offset)
        super.scrollToPositionWithOffset(position, offset)
    }

    override fun computeVerticalScrollExtent(state: RecyclerView.State): Int {
        skipStickyHeader()
        val extent = super.computeVerticalScrollExtent(state)
        unskipStickyHeader()
        return extent
    }

    override fun computeVerticalScrollOffset(state: RecyclerView.State): Int {
        skipStickyHeader()
        val offset = super.computeVerticalScrollOffset(state)
        unskipStickyHeader()
        return offset
    }

    override fun computeVerticalScrollRange(state: RecyclerView.State): Int {
        skipStickyHeader()
        val range = super.computeVerticalScrollRange(state)
        unskipStickyHeader()
        return range
    }

    override fun computeHorizontalScrollExtent(state: RecyclerView.State): Int {
        skipStickyHeader()
        val extent = super.computeHorizontalScrollExtent(state)
        unskipStickyHeader()
        return extent
    }

    override fun computeHorizontalScrollOffset(state: RecyclerView.State): Int {
        skipStickyHeader()
        val offset = super.computeHorizontalScrollOffset(state)
        unskipStickyHeader()
        return offset
    }

    override fun computeHorizontalScrollRange(state: RecyclerView.State): Int {
        skipStickyHeader()
        val range = super.computeHorizontalScrollRange(state)
        unskipStickyHeader()
        return range
    }

    override fun computeScrollVectorForPosition(targetPosition: Int): PointF? {
        skipStickyHeader()
        val vector = super.computeScrollVectorForPosition(targetPosition)
        unskipStickyHeader()
        return vector
    }

    override fun findFirstVisibleItemPosition(): Int {
        detachStickyHeader()
        val position = super.findFirstVisibleItemPosition()
        attachStickyHeader()
        return position
    }

    override fun findFirstCompletelyVisibleItemPosition(): Int {
        detachStickyHeader()
        val position = super.findFirstCompletelyVisibleItemPosition()
        attachStickyHeader()
        return position
    }

    override fun findLastVisibleItemPosition(): Int {
        detachStickyHeader()
        val position = super.findLastVisibleItemPosition()
        attachStickyHeader()
        return position
    }

    override fun findLastCompletelyVisibleItemPosition(): Int {
        detachStickyHeader()
        val position = super.findLastCompletelyVisibleItemPosition()
        attachStickyHeader()
        return position
    }

    override fun onFocusSearchFailed(
        focused: View,
        focusDirection: Int,
        recycler: Recycler,
        state: RecyclerView.State
    ): View? {
        val view = super.onFocusSearchFailed(focused, focusDirection, recycler, state)
        return if (view === mStickyHeader) {
            null
        } else view
    }

    private fun skipStickyHeader() {
        mSkipStickyHeader = true
    }

    private fun unskipStickyHeader() {
        mSkipStickyHeader = false
    }

    override fun getChildCount(): Int {
        return if (!mAddToParent && mSkipStickyHeader && mStickyHeader != null) {
            super.getChildCount() - 1
        } else super.getChildCount()
    }

    override fun getChildAt(index: Int): View? {
        return if (!mAddToParent && mSkipStickyHeader && mStickyHeader != null && index >= mChildHelper.indexOfChild(
                mStickyHeader
            )
        ) {
            super.getChildAt(index + 1)
        } else super.getChildAt(index)
    }

    private fun detachStickyHeader() {
        val stickyHeader = mStickyHeader
        if (--mStickyHeaderAttachCount == 0 && stickyHeader != null && !mAddToParent) {
            detachView(stickyHeader)
        }
    }

    private fun attachStickyHeader() {
        val stickyHeader = mStickyHeader
        if (++mStickyHeaderAttachCount == 1 && stickyHeader != null && !mAddToParent) {
            attachView(stickyHeader)
        }
    }

    /**
     * Updates the sticky header state (creation, binding, display), to be called whenever there's a layout or scroll
     */
    private fun updateStickyHeader(recycler: Recycler, layout: Boolean) {
        val headerCount = mHeaderPositions.size
        val childCount = childCount
        if (headerCount > 0 && childCount > 0) {
            // Find first valid child.
            var anchorView: View? = null
            var anchorIndex = -1
            var anchorPos = -1
            for (i in 0 until childCount) {
                val child = getChildAt(i) ?: continue
                val params = child.layoutParams as RecyclerView.LayoutParams
                if (isViewValidAnchor(child, params)) {
                    anchorView = child
                    anchorIndex = i
                    anchorPos = params.absoluteAdapterPosition
                    break
                }
            }
            if (anchorView != null && anchorPos != -1) {
                var headerIndex = findHeaderIndexOrBefore(anchorPos)

                val isViewOnBoundary = isViewOnBoundary(anchorView)

                if (headerIndex > 0 && !isViewOnBoundary && isViewMarginOnBoundary(anchorView)) {
                    headerIndex--
                }

                val headerPos = if (headerIndex != -1) mHeaderPositions[headerIndex] else -1
                val nextHeaderPos =
                    if (headerCount > headerIndex + 1) mHeaderPositions[headerIndex + 1] else -1


                // Show sticky header if:
                // - There's one to show;
                // - It's on the edge or it's not the anchor view;
                // - Isn't followed by another sticky header;
                if (headerPos != -1 && (headerPos != anchorPos || isViewOnBoundary) && nextHeaderPos != headerPos + 1) {
                    var stickyHeader = mStickyHeader
                    // Ensure existing sticky header, if any, is of correct type.
                    if (stickyHeader != null
                        && getItemViewType(stickyHeader) != mAdapter?.getItemViewType(headerPos)
                    ) {
                        // A sticky header was shown before but is not of the correct type. Scrap it.
                        scrapStickyHeader(recycler)
                    }

                    // Ensure sticky header is created, if absent, or bound, if being laid out or the position changed.
                    if (stickyHeader == null) {
                        stickyHeader = createStickyHeader(recycler, headerPos)
                    }
                    if (layout || getPosition(stickyHeader) != headerPos) {
                        bindStickyHeader(recycler, headerPos)
                    }

                    // Draw the sticky header using translation values which depend on orientation, direction and
                    // position of the next header view.
                    var nextHeaderView: View? = null
                    if (nextHeaderPos != -1) {
                        nextHeaderView = getChildAt(anchorIndex + (nextHeaderPos - anchorPos))
                        // The header view itself is added to the RecyclerView. Discard it if it comes up.
                        if (nextHeaderView === mStickyHeader) {
                            nextHeaderView = null
                        }
                    }
                    stickyHeader.translationX = getX(stickyHeader, nextHeaderView)
                    stickyHeader.translationY = getY(stickyHeader, nextHeaderView)
                    return
                }
            }
        }
        if (mStickyHeader != null) {
            scrapStickyHeader(recycler)
        }
    }

    /**
     * Creates [RecyclerView.ViewHolder] for `position`, including measure / layout, and assigns it to
     * [.mStickyHeader].
     */
    @Suppress("UNCHECKED_CAST")
    private fun createStickyHeader(recycler: Recycler, position: Int): View {
        val stickyHeader = recycler.getViewForPosition(position)
        val viewHolder = RecyclerView.getChildViewHolderInt(stickyHeader)
        // Setup sticky header if the adapter requires it.
        if (viewHolder != null && mAdapter is OnViewAttachListener) {
            (mAdapter as OnViewAttachListener).onStickyHeaderViewAttachedToWindow(viewHolder)
        }

        // Add sticky header as a child view, to be detached / reattached whenever LinearLayoutManager#fill() is called,
        // which happens on layout and scroll (see overrides).
        val parent = mRecyclerView.parent as ViewGroup
        if (parent is StickyParentLayout) {
            mAddToParent = true
            parent.addStickyHeader(stickyHeader)
            if (viewHolder != null) {
                (mAdapter as? RecyclerView.Adapter<RecyclerView.ViewHolder>)?.onViewAttachedToWindow(
                    viewHolder
                )
            }
            measureAndLayout(stickyHeader)
            // Ignore sticky header, as it's fully managed by this LayoutManager.
            viewHolder.addFlags(RecyclerView.ViewHolder.FLAG_IGNORE)
            mRecyclerView.mViewInfoStore.removeViewHolder(viewHolder)
        } else {
            mAddToParent = false
            addView(stickyHeader)
            measureAndLayout(stickyHeader)
            // Ignore sticky header, as it's fully managed by this LayoutManager.
            ignoreView(stickyHeader)
        }
        mStickyHeader = stickyHeader
        mStickyHeaderPosition = position
        mStickyHeaderAttachCount = 1
        return stickyHeader
    }

    override fun addView(child: View, index: Int) {
        super.addView(child, index)
    }

    override fun removeViewAt(index: Int) {
        super.removeViewAt(index)
    }

    /**
     * Binds the [.mStickyHeader] for the given `position`.
     */
    private fun bindStickyHeader(recycler: Recycler, position: Int) {
        // Bind the sticky header.
        val stickyHeader = mStickyHeader ?: return
        recycler.bindViewToPosition(stickyHeader, position)
        mStickyHeaderPosition = position
        measureAndLayout(stickyHeader)

        // If we have a pending scroll wait until the end of layout and scroll again.
        if (mPendingScrollPosition != RecyclerView.NO_POSITION) {
            val vto = stickyHeader.viewTreeObserver

            vto?.addOnGlobalLayoutListener(object : OnGlobalLayoutListener {

                override fun onGlobalLayout() {
                    vto.removeOnGlobalLayoutListener(this)
                    if (getPendingScrollPosition() != RecyclerView.NO_POSITION) {
                        scrollToPositionWithOffset(getPendingScrollPosition(), mPendingScrollOffset)
                        setPendingScroll(RecyclerView.NO_POSITION, INVALID_OFFSET)
                    }
                }
            })
        }
    }

    private fun getPendingScrollPosition() = mPendingScrollPosition

    /**
     * Measures and lays out `stickyHeader`.
     */
    private fun measureAndLayout(stickyHeader: View) {
        measureChildWithMargins(stickyHeader, 0, 0)
        if (orientation == VERTICAL) {
            val shouldPadding = getShouldPaddingTop()
            stickyHeader.layout(
                paddingStart,
                shouldPadding,
                width - paddingEnd,
                stickyHeader.measuredHeight + shouldPadding
            )
        } else {
            val shouldPadding = getShouldPaddingStart()
            stickyHeader.layout(
                shouldPadding,
                paddingTop,
                stickyHeader.measuredWidth + shouldPadding,
                height - paddingBottom
            )
        }
    }

    /**
     * Returns [.mStickyHeader] to the [RecyclerView]'s [RecyclerView.RecycledViewPool], assigning it
     * to `null`.
     *
     * @param recycler If passed, the sticky header will be returned to the recycled view pool.
     */
    @Suppress("UNCHECKED_CAST")
    private fun scrapStickyHeader(recycler: Recycler?) {
        Log.d("fuck", "", Exception("12"))
        val stickyHeader = mStickyHeader ?: return
        mStickyHeader = null
        mStickyHeaderPosition = RecyclerView.NO_POSITION

        // Revert translation values.
        stickyHeader.translationX = 0f
        stickyHeader.translationY = 0f
        val viewHolder = RecyclerView.getChildViewHolderInt(stickyHeader)

        // Teardown holder if the adapter requires it.
        if (viewHolder != null && mAdapter is OnViewAttachListener) {
            (mAdapter as OnViewAttachListener).onStickyHeaderViewDetachedFromWindow(viewHolder)
        }
        // Stop ignoring sticky header so that it can be recycled.
        stopIgnoringView(stickyHeader)
        if (mAddToParent) {
            val parent = mRecyclerView.parent as ViewGroup
            if (parent is StickyParentLayout) {
                parent.removeStickyHeader(stickyHeader)
            }
            if (viewHolder != null) {
                (mAdapter as? RecyclerView.Adapter<RecyclerView.ViewHolder>)?.onViewAttachedToWindow(
                    viewHolder
                )
            }
        } else {
            // Remove and recycle sticky header.
            removeView(stickyHeader)
        }
        mAddToParent = false
        recycler?.recycleView(stickyHeader)
    }

    /**
     * Returns true when `view` is a valid anchor, ie. the first view to be valid and visible.
     */
    private fun isViewValidAnchor(view: View, params: RecyclerView.LayoutParams): Boolean {
        return if (!params.isItemRemoved && !params.isViewInvalid) {
            if (orientation == VERTICAL) {
                if (reverseLayout) {
                    view.top + view.translationY <= height + mTranslationY + getShouldPaddingBottom()
                } else {
                    view.bottom - view.translationY >= mTranslationY + getShouldPaddingTop()
                }
            } else {
                if (reverseLayout) {
                    view.left + view.translationX <= width + mTranslationX + getShouldPaddingEnd()
                } else {
                    view.right - view.translationX >= mTranslationX + getShouldPaddingStart()
                }
            }
        } else {
            false
        }
    }

    private fun isClipPadding(): Boolean {
        return mRecyclerView?.clipToPadding ?: false
    }

    private fun getShouldPaddingStart(): Int {
        return if (isClipPadding()) {
            paddingStart
        } else {
            0
        }
    }

    private fun getShouldPaddingTop(): Int {
        return if (isClipPadding()) {
            paddingTop
        } else {
            0
        }
    }


    private fun getShouldPaddingEnd(): Int {
        return if (isClipPadding()) {
            paddingEnd
        } else {
            0
        }
    }

    private fun getShouldPaddingBottom(): Int {
        return if (isClipPadding()) {
            paddingBottom
        } else {
            0
        }
    }

    /**
     * Returns true when the `view` is at the edge of the parent [RecyclerView].
     */
    private fun isViewOnBoundary(view: View): Boolean {
        return if (orientation == VERTICAL) {
            if (reverseLayout) {
                view.bottom - view.translationY > height + mTranslationY + getShouldPaddingBottom()
            } else {
                view.top + view.translationY < mTranslationY + getShouldPaddingTop()
            }
        } else {
            if (reverseLayout) {
                view.right - view.translationX > width + mTranslationX + getShouldPaddingEnd()
            } else {
                view.left + view.translationX < mTranslationX + getShouldPaddingStart()
            }
        }
    }

    private fun isViewMarginOnBoundary(view: View): Boolean {
        return if (orientation == VERTICAL) {
            if (reverseLayout) {
                val margin = view.marginBottom
                if (margin == 0){
                    return false
                }
                view.bottom - view.translationY >= height + mTranslationY + getShouldPaddingBottom() + margin
            } else {
                val margin = view.marginTop
                if (margin == 0){
                    return false
                }
                view.top + view.translationY <= mTranslationY + getShouldPaddingTop() + margin
            }
        } else {
            if (reverseLayout) {
                val margin = view.marginEnd
                if (margin == 0){
                    return false
                }
                view.right - view.translationX >= width + mTranslationX + getShouldPaddingEnd() + margin
            } else {
                val margin = view.marginStart
                if (margin == 0){
                    return false
                }
                view.left + view.translationX <= mTranslationX + getShouldPaddingStart() + margin
            }
        }
    }

    /**
     * Returns the position in the Y axis to position the header appropriately, depending on orientation, direction and
     * [android.R.attr.clipToPadding].
     */
    private fun getY(headerView: View, nextHeaderView: View?): Float {
        var y = mTranslationY
        if (orientation == VERTICAL) {
            if (reverseLayout) {
                y += (height - headerView.height).toFloat()
            }
            if (nextHeaderView != null) {
                if (reverseLayout) {
                    y = Math.max(nextHeaderView.bottom.toFloat(), y)
                } else {
                    y = Math.min(
                        (nextHeaderView.top - headerView.bottom).toFloat(),
                        y
                    )
                }
            }
        }
        return y
    }

    /**
     * Returns the position in the X axis to position the header appropriately, depending on orientation, direction and
     * [android.R.attr.clipToPadding].
     */
    private fun getX(headerView: View, nextHeaderView: View?): Float {
        return if (orientation != VERTICAL) {
            var x = mTranslationX
            if (reverseLayout) {
                x += (width - headerView.width).toFloat()
            }
            if (nextHeaderView != null) {
                if (reverseLayout) {

                    x = Math.max(nextHeaderView.right.toFloat(), x)
                } else {
                    x = Math.min(
                        (nextHeaderView.left - headerView.width).toFloat(),
                        x
                    )
                }
            }
            x
        } else {
            mTranslationX
        }
    }

    /**
     * Finds the header index of `position` in `mHeaderPositions`.
     */
    private fun findHeaderIndex(position: Int): Int {
        var low = 0
        var high = mHeaderPositions.size - 1
        while (low <= high) {
            val middle = (low + high) / 2
            if (mHeaderPositions[middle] > position) {
                high = middle - 1
            } else if (mHeaderPositions[middle] < position) {
                low = middle + 1
            } else {
                return middle
            }
        }
        return -1
    }

    /**
     * Finds the header index of `position` or the one before it in `mHeaderPositions`.
     */
    private fun findHeaderIndexOrBefore(position: Int): Int {
        var low = 0
        var high = mHeaderPositions.size - 1
        while (low <= high) {
            val middle = (low + high) / 2
            if (mHeaderPositions[middle] > position) {
                high = middle - 1
            } else if (middle < mHeaderPositions.size - 1 && mHeaderPositions[middle + 1] <= position) {
                low = middle + 1
            } else {
                return middle
            }
        }
        return -1
    }

    /**
     * Finds the header index of `position` or the one next to it in `mHeaderPositions`.
     */
    private fun findHeaderIndexOrNext(position: Int): Int {
        var low = 0
        var high = mHeaderPositions.size - 1
        while (low <= high) {
            val middle = (low + high) / 2
            if (middle > 0 && mHeaderPositions[middle - 1] >= position) {
                high = middle - 1
            } else if (mHeaderPositions[middle] < position) {
                low = middle + 1
            } else {
                return middle
            }
        }
        return -1
    }

    private fun setPendingScroll(position: Int, offset: Int) {
        mPendingScrollPosition = position
        mPendingScrollOffset = offset
    }

    /**
     * Handles header positions while adapter changes occur.
     *
     * This is used in detriment of [RecyclerView.LayoutManager]'s callbacks to control when they're received.
     */
    private inner class HeaderPositionsAdapterDataObserver : AdapterDataObserver() {
        override fun onChanged() {
            // There's no hint at what changed, so go through the adapter.
            mHeaderPositions.clear()
            val itemCount = mAdapter?.itemCount ?: 0
            for (i in 0 until itemCount) {
                if ((mAdapter as? StickyHeaders)?.isStickyHeader(i) == true) {
                    mHeaderPositions.add(i)
                }
            }

            // Remove sticky header immediately if the entry it represents has been removed. A layout will follow.
            if (mStickyHeader != null && !mHeaderPositions.contains(mStickyHeaderPosition)) {
                scrapStickyHeader(null)
            }
        }

        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
            // Shift headers below down.
            val headerCount = mHeaderPositions.size
            if (headerCount > 0) {
                var i = findHeaderIndexOrNext(positionStart)
                while (i != -1 && i < headerCount) {
                    mHeaderPositions[i] = mHeaderPositions[i] + itemCount
                    i++
                }
            }

            // Add new headers.
            for (i in positionStart until positionStart + itemCount) {
                if ((mAdapter as? StickyHeaders)?.isStickyHeader(i) == true) {
                    val headerIndex = findHeaderIndexOrNext(i)
                    if (headerIndex != -1) {
                        mHeaderPositions.add(headerIndex, i)
                    } else {
                        mHeaderPositions.add(i)
                    }
                }
            }
        }

        override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
            var headerCount = mHeaderPositions.size
            if (headerCount > 0) {
                // Remove headers.
                for (i in positionStart + itemCount - 1 downTo positionStart) {
                    val index = findHeaderIndex(i)
                    if (index != -1) {
                        mHeaderPositions.removeAt(index)
                        headerCount--
                    }
                }

                // Remove sticky header immediately if the entry it represents has been removed. A layout will follow.
                if (mStickyHeader != null && !mHeaderPositions.contains(mStickyHeaderPosition)) {
                    scrapStickyHeader(null)
                }

                // Shift headers below up.
                var i = findHeaderIndexOrNext(positionStart + itemCount)
                while (i != -1 && i < headerCount) {
                    mHeaderPositions[i] = mHeaderPositions[i] - itemCount
                    i++
                }
            }
        }

        override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) {
            // Shift moved headers by toPosition - fromPosition.
            // Shift headers in-between by itemCount (reverse if downwards).
            val headerCount = mHeaderPositions.size
            if (headerCount > 0) {
                val topPosition = Math.min(fromPosition, toPosition)
                var i = findHeaderIndexOrNext(topPosition)
                while (i != -1 && i < headerCount) {
                    val headerPos = mHeaderPositions[i]
                    var newHeaderPos = headerPos
                    if (headerPos >= fromPosition && headerPos < fromPosition + itemCount) {
                        newHeaderPos += toPosition - fromPosition
                    } else if (fromPosition < toPosition && headerPos >= fromPosition + itemCount && headerPos <= toPosition) {
                        newHeaderPos -= itemCount
                    } else if (fromPosition > toPosition && headerPos >= toPosition && headerPos <= fromPosition) {
                        newHeaderPos += itemCount
                    } else {
                        break
                    }
                    if (newHeaderPos != headerPos) {
                        mHeaderPositions[i] = newHeaderPos
                        sortHeaderAtIndex(i)
                    } else {
                        break
                    }
                    i++
                }
            }
        }

        private fun sortHeaderAtIndex(index: Int) {
            val headerPos = mHeaderPositions.removeAt(index)
            val headerIndex = findHeaderIndexOrNext(headerPos)
            if (headerIndex != -1) {
                mHeaderPositions.add(headerIndex, headerPos)
            } else {
                mHeaderPositions.add(headerPos)
            }
        }
    }

    class SavedState : Parcelable {
        var superState: Parcelable? = null
        var pendingScrollPosition = 0
        var pendingScrollOffset = 0

        constructor() {}
        constructor(parcel: Parcel) {
            superState = parcel.readParcelable(SavedState::class.java.classLoader)
            pendingScrollPosition = parcel.readInt()
            pendingScrollOffset = parcel.readInt()
        }

        override fun describeContents(): Int {
            return 0
        }

        override fun writeToParcel(dest: Parcel, flags: Int) {
            dest.writeParcelable(superState, flags)
            dest.writeInt(pendingScrollPosition)
            dest.writeInt(pendingScrollOffset)
        }

        companion object {
            @JvmField
            val CREATOR: Parcelable.Creator<SavedState> = object : Parcelable.Creator<SavedState> {
                override fun createFromParcel(parcel: Parcel): SavedState {
                    return SavedState(parcel)
                }

                override fun newArray(size: Int): Array<SavedState?> {
                    return arrayOfNulls(size)
                }
            }
        }
    }
}