package androidx.recyclerview.widget;

import androidx.recyclerview.widget.RecyclerView;

/**
 * Created by jay on 2017/12/4 上午10:52
 *
 * Adds sticky headers capabilities to the {@link RecyclerView.Adapter}. Should return {@code true} for all
 * positions that represent sticky headers.
 *
 * @link https://github.com/Doist/RecyclerViewExtensions/blob/master/StickyHeaders
 */
public interface StickyHeaders {

    boolean isStickyHeader(int position);

    interface OnViewAttachListener {
        /**
         * Adjusts any necessary properties of the {@code holder} that is being used as a sticky header.
         *
         * {@link #onStickyHeaderViewDetachedFromWindow(RecyclerView.ViewHolder)} will be called sometime after this method
         * and before any other calls to this method go through.
         * @param holder
         */
        void onStickyHeaderViewAttachedToWindow(RecyclerView.ViewHolder holder);

        /**
         * Reverts any properties changed in {@link #onStickyHeaderViewAttachedToWindow(RecyclerView.ViewHolder)}.
         *
         * Called after {@link #onStickyHeaderViewAttachedToWindow(RecyclerView.ViewHolder)}.
         * @param holder
         */
        void onStickyHeaderViewDetachedFromWindow(RecyclerView.ViewHolder holder);
    }
}
