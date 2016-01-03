package pl.polak.oversnackbar;

/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.ColorInt;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.SwipeDismissBehavior;
import android.support.v4.view.ViewCompat;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * OverSnackBar provides lightweight feedback about an operation. They show a brief message at the
 * top of the screen on mobile. OverSnackBars appear above all other
 * elements on screen and only one can be displayed at a time.
 * <p>
 * They automatically disappear after a timeout or after user interaction elsewhere on the screen,
 * particularly after interactions that summon a new surface or activity. Snackbars can be swiped
 * off screen.
 * <p>
 * Snackbars can contain an action which is set via
 * {@link #setAction(CharSequence, android.view.View.OnClickListener)}.
 * <p>
 * To be notified when a snackbar has been shown or dismissed, you can provide a {@link Callback}
 * via {@link #setCallback(Callback)}.</p>
 */
public final class OverSnackBar {

    /**
     * Callback class for {@link OverSnackBar} instances.
     *
     * @see OverSnackBar#setCallback(Callback)
     */
    public static abstract class Callback {
        /**
         * Indicates that the OverSnackBar was dismissed via a swipe.
         */
        public static final int DISMISS_EVENT_SWIPE = 0;
        /**
         * Indicates that the OverSnackBar was dismissed via an action click.
         */
        public static final int DISMISS_EVENT_ACTION = 1;
        /**
         * Indicates that the OverSnackBar was dismissed via a timeout.
         */
        public static final int DISMISS_EVENT_TIMEOUT = 2;
        /**
         * Indicates that the OverSnackBar was dismissed via a call to {@link #dismiss()}.
         */
        public static final int DISMISS_EVENT_MANUAL = 3;
        /**
         * Indicates that the OverSnackBar was dismissed from a new Snackbar being shown.
         */
        public static final int DISMISS_EVENT_CONSECUTIVE = 4;


        @IntDef({DISMISS_EVENT_SWIPE, DISMISS_EVENT_ACTION, DISMISS_EVENT_TIMEOUT,
                DISMISS_EVENT_MANUAL, DISMISS_EVENT_CONSECUTIVE})
        @Retention(RetentionPolicy.SOURCE)
        public @interface DismissEvent {
        }

        /**
         * Called when the given {@link OverSnackBar} has been dismissed, either through a time-out,
         * having been manually dismissed, or an action being clicked.
         *
         * @param OverSnackBar The snackbar which has been dismissed.
         * @param event        The event which caused the dismissal. One of either:
         *                     {@link #DISMISS_EVENT_SWIPE}, {@link #DISMISS_EVENT_ACTION},
         *                     {@link #DISMISS_EVENT_TIMEOUT}, {@link #DISMISS_EVENT_MANUAL} or
         *                     {@link #DISMISS_EVENT_CONSECUTIVE}.
         * @see OverSnackBar#dismiss()
         */
        public void onDismissed(OverSnackBar OverSnackBar, @DismissEvent int event) {
        }

        /**
         * Called when the given {@link OverSnackBar} is visible.
         *
         * @param OverSnackBar The snackbar which is now visible.
         * @see OverSnackBar#show()
         */
        public void onShown(OverSnackBar OverSnackBar) {

        }
    }

    @IntDef({APPEAR_FROM_TOP_TO_DOWN, APPEAR_FROM_BOTTOM_TO_TOP})
    @Retention(RetentionPolicy.SOURCE)
    public @interface OverSnackAppearDirection {
    }

    /**
     * Show the OverSnackBar from top to down.
     */
    public static final int APPEAR_FROM_TOP_TO_DOWN = 0;

    /**
     * Show the OverSnackBar from top to down.
     */
    public static final int APPEAR_FROM_BOTTOM_TO_TOP = 1;

    @IntDef({LENGTH_INDEFINITE, LENGTH_SHORT, LENGTH_LONG})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Duration {
    }

    /**
     * Show the OverSnackBar indefinitely. This means that the OverSnackBar will be displayed from the time
     * that is {@link #show() shown} until either it is dismissed, or another OverSnackBar is shown.
     *
     * @see #setDuration
     */
    public static final int LENGTH_INDEFINITE = -2;

    /**
     * Show the OverSnackBar for a short period of time.
     *
     * @see #setDuration
     */
    public static final int LENGTH_SHORT = -1;

    /**
     * Show the OverSnackBar for a long period of time.
     *
     * @see #setDuration
     */
    public static final int LENGTH_LONG = 0;

    private static final int ANIMATION_DURATION = 250;
    private static final int ANIMATION_FADE_DURATION = 180;

    private static final Handler sHandler;
    private static final int MSG_SHOW = 0;
    private static final int MSG_DISMISS = 1;

    private
    @OverSnackAppearDirection
    int appearDirection;

    static {
        sHandler = new Handler(Looper.getMainLooper(), new Handler.Callback() {
            @Override
            public boolean handleMessage(Message message) {
                switch (message.what) {
                    case MSG_SHOW:
                        ((OverSnackBar) message.obj).showView();
                        return true;
                    case MSG_DISMISS:
                        ((OverSnackBar) message.obj).hideView(message.arg1);
                        return true;
                }
                return false;
            }
        });
    }

    private final ViewGroup mParent;
    private final Context mContext;
    private final SnackbarLayout mView;
    private int mDuration;
    private Callback mCallback;

    private OverSnackBar(ViewGroup parent) {
        appearDirection = APPEAR_FROM_TOP_TO_DOWN;

        mParent = parent;
        mContext = parent.getContext();
        LayoutInflater inflater = LayoutInflater.from(mContext);

        mView = (SnackbarLayout) inflater.inflate(R.layout.view_oversnackbar_layout, mParent, false);
    }

    private OverSnackBar(ViewGroup parent, @OverSnackAppearDirection int appearDirection) {
        this(parent);
        this.appearDirection = appearDirection;

        if(appearDirection == APPEAR_FROM_TOP_TO_DOWN) {
            mView.setPadding(0, DisplayUtils.getStatusBarHeight(mParent.getResources()), 0, 0);
        }
    }

    /**
     * Make a OverSnackBar to display a message
     * <p/>
     * <p>OverSnackBar will try and find a parent view to hold OverSnackBar's view from the value given
     * to {@code view}. Snackbar will walk up the view tree trying to find a suitable parent,
     * which is defined as a {@link CoordinatorLayout} or the window decor's content view,
     * whichever comes first.
     * <p/>
     * <p>Having a {@link CoordinatorLayout} in your view hierarchy allows OverSnackBar to enable
     * certain features, such as swipe-to-dismiss and automatically moving of widgets like
     * {@link FloatingActionButton}.
     *
     * @param view     The view to find a parent from.
     * @param text     The text to show.  Can be formatted text.
     * @param duration How long to display the message.  Either {@link #LENGTH_SHORT} or {@link
     *                 #LENGTH_LONG}
     */
    @NonNull
    public static OverSnackBar make(@NonNull View view, @NonNull CharSequence text, @Duration int duration) {
        OverSnackBar overSnackBar = new OverSnackBar(findSuitableParent(view), APPEAR_FROM_TOP_TO_DOWN);
        overSnackBar.setText(text);
        overSnackBar.setDuration(duration);
        return overSnackBar;
    }

    @NonNull
    public static OverSnackBar make(@NonNull View view, @NonNull CharSequence text, @Duration int duration, @OverSnackAppearDirection int appearDirection) {
        OverSnackBar overSnackBar = new OverSnackBar(findSuitableParent(view), appearDirection);
        overSnackBar.setText(text);
        overSnackBar.setDuration(duration);
        return overSnackBar;
    }

    /**
     * Make a OverSnackBar to display a message.
     * <p/>
     * <p>OverSnackBar will try and find a parent view to hold OverSnackBar's view from the value given
     * to {@code view}. OverSnackBar will walk up the view tree trying to find a suitable parent,
     * which is defined as a {@link CoordinatorLayout} or the window decor's content view,
     * whichever comes first.
     * <p/>
     * <p>Having a {@link CoordinatorLayout} in your view hierarchy allows OverSnackBar to enable
     * certain features, such as swipe-to-dismiss and automatically moving of widgets like
     * {@link FloatingActionButton}.
     *
     * @param view     The view to find a parent from.
     * @param resId    The resource id of the string resource to use. Can be formatted text.
     * @param duration How long to display the message.  Either {@link #LENGTH_SHORT} or {@link
     *                 #LENGTH_LONG}
     */
    @NonNull
    public static OverSnackBar make(@NonNull View view, @StringRes int resId, @Duration int duration) {
        return make(view, view.getResources().getText(resId), duration);
    }

    private static ViewGroup findSuitableParent(View view) {
//        ViewGroup fallback = null;
//        do {
//            if (view instanceof CoordinatorLayout) {
//                 We've found a CoordinatorLayout, use it
//                return (ViewGroup) view;
//            } else if (view instanceof FrameLayout) {
//                if (view.getId() == android.R.id.content) {
        // If we've hit the decor content view, then we didn't find a CoL in the
        // hierarchy, so use it.
//                    return (ViewGroup) view;
//                } else {
        // It's not the content view but we'll use it as our fallback
//                    fallback = (ViewGroup) view;
//                }
//            }
//            if (view != null) {
        // Else, we will loop and crawl up the view hierarchy and try to find a parent
//                final ViewParent parent = view.getParent();
//                view = parent instanceof View ? (View) parent : null;
//            }
//        } while (view != null);
        // If we reach here then we didn't find a CoL or a suitable content view so we'll fallback
//        return fallback;
        return (ViewGroup) view;
    }


    public OverSnackBar addIcon(int resource_id, int size) {
        final TextView tv = mView.getMessageView();

        tv.setCompoundDrawablesWithIntrinsicBounds(new BitmapDrawable(Bitmap.createScaledBitmap(((BitmapDrawable) (mContext.getResources().getDrawable(resource_id))).getBitmap(), size, size, true)), null, null, null);

        return this;
    }

    /**
     * Set the action to be displayed in this {@link OverSnackBar}.
     *
     * @param resId    String resource to display
     * @param listener callback to be invoked when the action is clicked
     */
    @NonNull
    public OverSnackBar setAction(@StringRes int resId, View.OnClickListener listener) {
        return setAction(mContext.getText(resId), listener);
    }

    /**
     * Set the action to be displayed in this {@link OverSnackBar}.
     *
     * @param text     Text to display
     * @param listener callback to be invoked when the action is clicked
     */
    @NonNull
    public OverSnackBar setAction(CharSequence text, final View.OnClickListener listener) {
        final TextView tv = mView.getActionView();
        if (TextUtils.isEmpty(text) || listener == null) {
            tv.setVisibility(View.GONE);
            tv.setOnClickListener(null);
        } else {
            tv.setVisibility(View.VISIBLE);
            tv.setText(text);
            tv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    listener.onClick(view);

                    dispatchDismiss(Callback.DISMISS_EVENT_ACTION);
                }
            });
        }
        return this;
    }

    /**
     * Sets the text color of the action specified in
     * {@link #setAction(CharSequence, View.OnClickListener)}.
     */
    @NonNull
    public OverSnackBar setActionTextColor(ColorStateList colors) {
        final TextView tv = mView.getActionView();
        tv.setTextColor(colors);
        return this;
    }

    /**
     * Sets the text color of the action specified in
     * {@link #setAction(CharSequence, View.OnClickListener)}.
     */
    @NonNull
    public OverSnackBar setActionTextColor(@ColorInt int color) {
        final TextView tv = mView.getActionView();
        tv.setTextColor(color);
        return this;
    }

    /**
     * Update the text in this {@link OverSnackBar}.
     *
     * @param message The new text for the Toast.
     */
    @NonNull
    public OverSnackBar setText(@NonNull CharSequence message) {
        final TextView tv = mView.getMessageView();
        tv.setText(message);
        return this;
    }

    /**
     * Update the text in this {@link OverSnackBar}.
     *
     * @param resId The new text for the Toast.
     */
    @NonNull
    public OverSnackBar setText(@StringRes int resId) {
        return setText(mContext.getText(resId));
    }

    /**
     * Set how long to show the view for.
     *
     * @param duration either be one of the predefined lengths:
     *                 {@link #LENGTH_SHORT}, {@link #LENGTH_LONG}, or a custom duration
     *                 in milliseconds.
     */
    @NonNull
    public OverSnackBar setDuration(@Duration int duration) {
        mDuration = duration;
        return this;
    }

    /**
     * Return the duration.
     *
     * @see #setDuration
     */
    @Duration
    public int getDuration() {
        return mDuration;
    }

    /**
     * Returns the {@link OverSnackBar}'s view.
     */

    @NonNull
    public View getView() {
        return mView;
    }

    /**
     * Show the {@link OverSnackBar}.
     */
    public void show() {
        SnackbarManager.getInstance().show(mDuration, mManagerCallback);
    }

    /**
     * Dismiss the {@link OverSnackBar}.
     */
    public void dismiss() {
        dispatchDismiss(Callback.DISMISS_EVENT_MANUAL);
    }

    private void dispatchDismiss(@Callback.DismissEvent int event) {
        SnackbarManager.getInstance().dismiss(mManagerCallback, event);
    }

    /**
     * Set a callback to be called when this the visibility of this {@link OverSnackBar} changes.
     */
    @NonNull
    public OverSnackBar setCallback(Callback callback) {
        mCallback = callback;
        return this;
    }

    /**
     * Return whether this {@link OverSnackBar} is currently being shown.
     */
    public boolean isShown() {
        return mView.isShown();
    }

    private final SnackbarManager.Callback mManagerCallback = new SnackbarManager.Callback() {
        @Override
        public void show() {
            sHandler.sendMessage(sHandler.obtainMessage(MSG_SHOW, OverSnackBar.this));
        }

        @Override
        public void dismiss(int event) {
            sHandler.sendMessage(sHandler.obtainMessage(MSG_DISMISS, event, 0, OverSnackBar.this));
        }
    };

    final void showView() {
        if (mView.getParent() == null) {
            final ViewGroup.LayoutParams lp = mView.getLayoutParams();
            if (lp instanceof CoordinatorLayout.LayoutParams) {
                // If our LayoutParams are from a CoordinatorLayout, we'll setup our Behavior

                final Behavior behavior = new Behavior();
                behavior.setStartAlphaSwipeDistance(0.1f);
                behavior.setEndAlphaSwipeDistance(0.6f);
                behavior.setSwipeDirection(SwipeDismissBehavior.SWIPE_DIRECTION_START_TO_END);
                behavior.setListener(new SwipeDismissBehavior.OnDismissListener() {
                    @Override
                    public void onDismiss(View view) {
                        dispatchDismiss(Callback.DISMISS_EVENT_SWIPE);
                    }

                    @Override
                    public void onDragStateChanged(int state) {
                        switch (state) {
                            case SwipeDismissBehavior.STATE_DRAGGING:
                            case SwipeDismissBehavior.STATE_SETTLING:

                                SnackbarManager.getInstance().cancelTimeout(mManagerCallback);
                                break;
                            case SwipeDismissBehavior.STATE_IDLE:

                                SnackbarManager.getInstance().restoreTimeout(mManagerCallback);
                                break;
                        }
                    }
                });
                ((CoordinatorLayout.LayoutParams) lp).setBehavior(behavior);

                ((CoordinatorLayout.LayoutParams) lp).setMargins(0, -30, 0, 0);
            }
            mParent.addView(mView);
        }
        if (ViewCompat.isLaidOut(mView)) {
            animateViewIn();
        } else {

            mView.setOnLayoutChangeListener(new SnackbarLayout.OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View view, int left, int top, int right, int bottom) {
                    animateViewIn();
                    mView.setOnLayoutChangeListener(null);
                }
            });
        }
    }

    private void animateViewIn() {
        Animation anim;

        if (appearDirection == APPEAR_FROM_TOP_TO_DOWN) {
            anim = getAnimationInFromTopToDown();
        } else {
            anim = getAnimationInFromBottomToTop();
        }

        anim.setInterpolator(pl.polak.oversnackbar.AnimationUtils.FAST_OUT_SLOW_IN_INTERPOLATOR);
        anim.setDuration(ANIMATION_DURATION);
        anim.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationEnd(Animation animation) {
                if (mCallback != null) {
                    mCallback.onShown(OverSnackBar.this);
                }
                SnackbarManager.getInstance().onShown(mManagerCallback);
            }

            @Override
            public void onAnimationStart(Animation animation) {
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }
        });
        mView.startAnimation(anim);

    }

    private Animation getAnimationInFromTopToDown() {
        return AnimationUtils.loadAnimation(mView.getContext(), R.anim.top_in);
    }

    private Animation getAnimationInFromBottomToTop() {
        return AnimationUtils.loadAnimation(mView.getContext(), R.anim.design_snackbar_in);
    }

    private void animateViewOut(final int event) {
        Animation anim;

        if (appearDirection == APPEAR_FROM_TOP_TO_DOWN) {
            anim = getAnimationOutFromTopToDown();
        } else {
            anim = getAnimationOutFromBottomToTop();
        }

        anim.setInterpolator(pl.polak.oversnackbar.AnimationUtils.FAST_OUT_SLOW_IN_INTERPOLATOR);
        anim.setDuration(ANIMATION_DURATION);
        anim.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationEnd(Animation animation) {
                onViewHidden(event);
            }

            @Override
            public void onAnimationStart(Animation animation) {
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }
        });
        mView.startAnimation(anim);

    }

    private Animation getAnimationOutFromTopToDown() {
        return AnimationUtils.loadAnimation(mView.getContext(), R.anim.top_out);
    }

    private Animation getAnimationOutFromBottomToTop() {
        return AnimationUtils.loadAnimation(mView.getContext(), R.anim.design_snackbar_out);
    }

    final void hideView(int event) {
        if (mView.getVisibility() != View.VISIBLE || isBeingDragged()) {
            onViewHidden(event);
        } else {
            animateViewOut(event);
        }
    }

    private void onViewHidden(int event) {
        // First tell the SnackbarManager that it has been dismissed
        SnackbarManager.getInstance().onDismissed(mManagerCallback);
        // Now call the dismiss listener (if available)
        if (mCallback != null) {
            mCallback.onDismissed(this, event);
        }

        // Lastly, remove the view from the parent (if attached)
        mParent.removeView(mView);

        SnackbarManager.getInstance().onDismissed(mManagerCallback);
    }

    /**
     * @return if the view is being being dragged or settled by {@link SwipeDismissBehavior}.
     */
    private boolean isBeingDragged() {
        final ViewGroup.LayoutParams lp = mView.getLayoutParams();
        if (lp instanceof CoordinatorLayout.LayoutParams) {
            final CoordinatorLayout.LayoutParams layoutParams = (CoordinatorLayout.LayoutParams) lp;
            final CoordinatorLayout.Behavior behavior = layoutParams.getBehavior();
            if (behavior instanceof SwipeDismissBehavior) {
                return ((SwipeDismissBehavior) behavior).getDragState() != SwipeDismissBehavior.STATE_IDLE;
            }
        }
        return false;
    }


    public static class SnackbarLayout extends LinearLayout {
        private TextView mMessageView;
        private Button mActionView;
        private int mMaxWidth;
        private int mMaxInlineActionWidth;

        interface OnLayoutChangeListener {
            public void onLayoutChange(View view, int left, int top, int right, int bottom);
        }

        private OnLayoutChangeListener mOnLayoutChangeListener;

        public SnackbarLayout(Context context) {
            this(context, null);
        }

        public SnackbarLayout(Context context, AttributeSet attrs) {
            super(context, attrs);
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.SnackbarLayout);
            mMaxWidth = a.getDimensionPixelSize(R.styleable.SnackbarLayout_android_maxWidth, -1);
            mMaxInlineActionWidth = a.getDimensionPixelSize(R.styleable.SnackbarLayout_maxActionInlineWidth, -1);
            if (a.hasValue(R.styleable.SnackbarLayout_elevation)) {
                ViewCompat.setElevation(this, a.getDimensionPixelSize(
                        R.styleable.SnackbarLayout_elevation, 0));
            }
            a.recycle();
            setClickable(true);

            // Now inflate our content. We need to do this manually rather than using an <include>
            // in the layout since older versions of the Android do not inflate includes with
            // the correct Context.
            LayoutInflater.from(context).inflate(R.layout.view_oversnackbar_layout_include, this);
        }

        @Override
        protected void onFinishInflate() {
            super.onFinishInflate();
            mMessageView = (TextView) findViewById(R.id.snackbar_text);
            mActionView = (Button) findViewById(R.id.snackbar_action);
        }

        TextView getMessageView() {
            return mMessageView;
        }

        Button getActionView() {
            return mActionView;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            if (mMaxWidth > 0 && getMeasuredWidth() > mMaxWidth) {
                widthMeasureSpec = MeasureSpec.makeMeasureSpec(mMaxWidth, MeasureSpec.EXACTLY);
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }
            final int multiLineVPadding = getResources().getDimensionPixelSize(
                    R.dimen.design_snackbar_padding_vertical_2lines);
            final int singleLineVPadding = getResources().getDimensionPixelSize(
                    R.dimen.design_snackbar_padding_vertical);
            final boolean isMultiLine = mMessageView.getLayout().getLineCount() > 1;
            boolean remeasure = false;
            if (isMultiLine && mMaxInlineActionWidth > 0
                    && mActionView.getMeasuredWidth() > mMaxInlineActionWidth) {
                if (updateViewsWithinLayout(VERTICAL, multiLineVPadding,
                        multiLineVPadding - singleLineVPadding)) {
                    remeasure = true;
                }
            } else {
                final int messagePadding = isMultiLine ? multiLineVPadding : singleLineVPadding;
                if (updateViewsWithinLayout(HORIZONTAL, messagePadding, messagePadding)) {
                    remeasure = true;
                }
            }
            if (remeasure) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }
        }

        void animateChildrenIn(int delay, int duration) {
            ViewCompat.setAlpha(mMessageView, 0f);
            ViewCompat.animate(mMessageView).alpha(1f).setDuration(duration)
                    .setStartDelay(delay).start();
            if (mActionView.getVisibility() == VISIBLE) {
                ViewCompat.setAlpha(mActionView, 0f);
                ViewCompat.animate(mActionView).alpha(1f).setDuration(duration)
                        .setStartDelay(delay).start();
            }
        }

        void animateChildrenOut(int delay, int duration) {
            ViewCompat.setAlpha(mMessageView, 1f);
            ViewCompat.animate(mMessageView).alpha(0f).setDuration(duration)
                    .setStartDelay(delay).start();
            if (mActionView.getVisibility() == VISIBLE) {
                ViewCompat.setAlpha(mActionView, 1f);
                ViewCompat.animate(mActionView).alpha(0f).setDuration(duration)
                        .setStartDelay(delay).start();
            }
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            super.onLayout(changed, l, t, r, b);
            if (changed && mOnLayoutChangeListener != null) {
                mOnLayoutChangeListener.onLayoutChange(this, l, t, r, b);
            }
        }

        void setOnLayoutChangeListener(OnLayoutChangeListener onLayoutChangeListener) {
            mOnLayoutChangeListener = onLayoutChangeListener;
        }

        private boolean updateViewsWithinLayout(final int orientation,
                                                final int messagePadTop, final int messagePadBottom) {
            boolean changed = false;
            if (orientation != getOrientation()) {
                setOrientation(orientation);
                changed = true;
            }
            if (mMessageView.getPaddingTop() != messagePadTop
                    || mMessageView.getPaddingBottom() != messagePadBottom) {
                updateTopBottomPadding(mMessageView, messagePadTop, messagePadBottom);
                changed = true;
            }
            return changed;
        }

        private static void updateTopBottomPadding(View view, int topPadding, int bottomPadding) {
            if (ViewCompat.isPaddingRelative(view)) {
                ViewCompat.setPaddingRelative(view,
                        ViewCompat.getPaddingStart(view), topPadding,
                        ViewCompat.getPaddingEnd(view), bottomPadding);
            } else {
                view.setPadding(view.getPaddingLeft(), topPadding,
                        view.getPaddingRight(), bottomPadding);
            }
        }
    }

    final class Behavior extends SwipeDismissBehavior<SnackbarLayout> {
        @Override
        public boolean onInterceptTouchEvent(CoordinatorLayout parent, SnackbarLayout child, MotionEvent event) {
            // We want to make sure that we disable any OverSnackBar timeouts if the user is
            // currently touching the OverSnackBar. We restore the timeout when complete
            if (parent.isPointInChildBounds(child, (int) event.getX(), (int) event.getY())) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        SnackbarManager.getInstance().cancelTimeout(mManagerCallback);
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        SnackbarManager.getInstance().restoreTimeout(mManagerCallback);
                        break;
                }
            }
            return super.onInterceptTouchEvent(parent, child, event);
        }
    }
}