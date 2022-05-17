package com.jay.widget

import androidx.recyclerview.widget.RecyclerView.ViewHolder

/**
 *
 * Adds sticky headers capabilities to the [RecyclerView.Adapter]. Should return `true` for all
 * positions that represent sticky headers.
 *
 * @link https://github.com/Doist/RecyclerViewExtensions/blob/master/StickyHeaders
 */
interface StickyHeaders {

    fun isStickyHeader(position: Int): Boolean

    interface OnViewAttachListener {
        /**
         * Adjusts any necessary properties of the `holder` that is being used as a sticky header.
         *
         * [.onStickyHeaderViewDetachedFromWindow] will be called sometime after this method
         * and before any other calls to this method go through.
         * @param holder
         */
        fun onStickyHeaderViewAttachedToWindow(holder: ViewHolder)

        /**
         * Reverts any properties changed in [.onStickyHeaderViewAttachedToWindow].
         *
         * Called after [.onStickyHeaderViewAttachedToWindow].
         * @param holder
         */
        fun onStickyHeaderViewDetachedFromWindow(holder: ViewHolder)
    }
}