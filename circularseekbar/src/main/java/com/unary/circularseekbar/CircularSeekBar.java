/*
 * Copyright 2020 Christopher Zaborsky
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
 */
package com.unary.circularseekbar;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Scroller;

import androidx.annotation.AttrRes;
import androidx.annotation.CallSuper;
import androidx.annotation.ColorInt;
import androidx.annotation.Dimension;
import androidx.annotation.DrawableRes;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * A styleable circular SeekBar widget. The user can initiate changes to the progress level by
 * moving a draggable thumb or touching the drawn arc.
 *
 * <p><strong>XML attributes</strong></p>
 * <p>The following optional attributes can be used to change the look and feel of the view:</p>
 * <pre>
 *   app:max="integer"                   // Default value of 100
 *   app:min="integer"                   // Should not be less than 0
 *   app:progress="integer"              // Default of 0 and must be within the min/max range
 *   app:progressColor="reference|color" // Reference to a color selector or simple color
 *   app:scrollMode="drift|gravity|snap" // Default mode is "drift"
 *   app:startAngle="float"              // Starting angle, relative to 90 degrees clockwise
 *   app:strokeWidth="dimension"         // Thickness of the arc. Default is "14dp"
 *   app:sweepAngle="float"              // Arc sweep angle, clockwise from the starting angle
 *   app:sweepColor="color"              // Color used to draw the arc
 *   app:thumbDrawable="reference"       // Reference to a drawable
 *   app:thumbRadius="dimension"         // Radius of the drawable. Default is "12dp"
 *   app:touchInside="boolean"           // Respond to touch inside the ellipse
 *
 *   android:enabled="boolean"           // Changes the view state and progress color
 * </pre>
 * <p>See {@link R.styleable#CircularSeekBar CircularSeekBar Attributes}, {@link R.styleable#View View Attributes}</p>
 */
public class CircularSeekBar extends View {

    private static final float VIEW_WIDTH = 256; // dp
    private static final float VIEW_HEIGHT = 256; // dp
    private static final float START_ANGLE = 90;
    private static final float SWEEP_ANGLE = 359.9f;
    private static final float STROKE_WIDTH = 14; // dp
    private static final int SWEEP_COLOR = R.attr.colorControlHighlight;
    private static final int PROGRESS_COLOR = R.attr.colorControlHighlight;
    private static final int ACTIVATED_COLOR = R.attr.colorControlActivated;
    private static final int THUMB_COLOR = 0xFFECECEC;
    private static final int RIPPLE_COLOR = R.attr.colorControlHighlight;
    private static final float THUMB_RADIUS = 12; // dp
    @ScrollMode
    private static final int SCROLL_MODE = ScrollMode.DRIFT;
    private static final boolean TOUCH_INSIDE = true;
    private static final int MIN = 0;
    private static final int MAX = 100;
    private static final int PROGRESS = 0;
    private static final float MULTIPLIER = 100;
    private static final int MAX_LEVEL = 10000;

    private float mStartAngle;
    private float mStrokeWidth;
    private RectF mDrawRectF;
    private float mSweepAngle;
    private int mSweepColor;
    private Path mSweepPath;
    private Paint mSweepPaint;
    private ColorStateList mProgressColor;
    private Path mProgressPath;
    private Paint mProgressPaint;
    private Drawable mThumbDrawable;
    private float mThumbRadius;
    private float mTouchRadius;
    @ScrollMode
    private int mScrollMode;
    private boolean mTouchInside;
    private int mMin;
    private int mMax;
    private int mProgress;
    private Scroller mScroller;
    private Point mThumbOrb;
    private Point mStartOrb;
    private Point mEndOrb;
    private int mLastUpdate;
    private OnProgressChangeListener mOnProgressChangeListener;

    /**
     * Annotation for the ScrollMode typedef. The enumeration values are shared with a styleable XML
     * attribute of the same name.
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({ScrollMode.DRIFT, ScrollMode.GRAVITY, ScrollMode.SNAP})
    public @interface ScrollMode {
        /**
         * Drift style for the scroll.
         */
        int DRIFT = 0;
        /**
         * Gravity style for the scroll.
         */
        int GRAVITY = 1;
        /**
         * Snap style for the scroll.
         */
        int SNAP = 2;
    }

    /**
     * Interface to notify the client of any progress changes. This only reflects touch initiated
     * updates and not client changes.
     */
    public interface OnProgressChangeListener {

        /**
         * Notification that the progress level is changing. The client has an opportunity to
         * approve or disapprove of the update.
         *
         * @param seekBar  SeekBar object that initiated the change.
         * @param progress The updated progress. This will be within the set min and max levels.
         * @return True if the client accepts the change.
         */
        boolean onProgressChanging(@NonNull CircularSeekBar seekBar, int progress);

        /**
         * Notification that the progress level has changed. The client can use this to update a
         * setting or preference.
         *
         * @param seekBar  SeekBar object that initiated the change.
         * @param progress The updated progress. This will be within the set min and max levels.
         * @param finished True if the touch event has ended.
         */
        void onProgressChanged(@NonNull CircularSeekBar seekBar, int progress, boolean finished);
    }

    /**
     * Simple constructor to use when creating the view from code.
     *
     * @param context Context given for the view. This determines the resources and theme.
     */
    public CircularSeekBar(Context context) {
        super(context);
        init(context, null, 0, 0);
    }

    /**
     * Constructor that is called when inflating the view from XML.
     *
     * @param context Context given for the view. This determines the resources and theme.
     * @param attrs   The attributes for the inflated XML tag.
     */
    public CircularSeekBar(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs, 0, 0);
    }

    /**
     * Constructor called when inflating from XML and applying a style.
     *
     * @param context      Context given for the view. This determines the resources and theme.
     * @param attrs        The attributes for the inflated XML tag.
     * @param defStyleAttr Default style attributes to apply to this view.
     */
    public CircularSeekBar(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs, defStyleAttr, 0);
    }

    /**
     * Constructor that is used when given a default shared style.
     *
     * @param context      Context given for the view. This determines the resources and theme.
     * @param attrs        The attributes for the inflated XML tag.
     * @param defStyleAttr Default style attributes to apply to this view.
     * @param defStyleRes  Default style resource to apply to this view.
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public CircularSeekBar(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context, attrs, defStyleAttr, defStyleRes);
    }

    /**
     * Shared method to initialize the member variables from the XML and create the drawing objects.
     * Some input values are checked for sanity.
     *
     * @param context      Context given for the view. This determines the resources and theme.
     * @param attrs        The attributes for the inflated XML tag.
     * @param defStyleAttr Default style attributes to apply to this view.
     * @param defStyleRes  Default style resource to apply to this view.
     */
    private void init(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        TypedArray typedArray = context.getTheme().obtainStyledAttributes(attrs, R.styleable.CircularSeekBar, defStyleAttr, defStyleRes);
        boolean enabled;

        try {
            mStartAngle = typedArray.getFloat(R.styleable.CircularSeekBar_startAngle, START_ANGLE);
            mSweepAngle = typedArray.getFloat(R.styleable.CircularSeekBar_sweepAngle, SWEEP_ANGLE);
            mStrokeWidth = typedArray.getDimension(R.styleable.CircularSeekBar_strokeWidth, dpToPixels(context, STROKE_WIDTH));
            mSweepColor = typedArray.getColor(R.styleable.CircularSeekBar_sweepColor, getAttrColor(context, SWEEP_COLOR));
            mProgressColor = typedArray.getColorStateList(R.styleable.CircularSeekBar_progressColor);
            mThumbDrawable = typedArray.getDrawable(R.styleable.CircularSeekBar_thumbDrawable);
            mThumbRadius = typedArray.getDimension(R.styleable.CircularSeekBar_thumbRadius, dpToPixels(context, THUMB_RADIUS));
            mScrollMode = typedArray.getInt(R.styleable.CircularSeekBar_scrollMode, SCROLL_MODE);
            mTouchInside = typedArray.getBoolean(R.styleable.CircularSeekBar_touchInside, TOUCH_INSIDE);
            mMin = typedArray.getInt(R.styleable.CircularSeekBar_min, MIN);
            mMax = typedArray.getInt(R.styleable.CircularSeekBar_max, MAX);
            mProgress = typedArray.getInt(R.styleable.CircularSeekBar_progress, PROGRESS);

            enabled = typedArray.getBoolean(R.styleable.CircularSeekBar_android_enabled, isEnabled());
        } finally {
            typedArray.recycle();
        }

        // Provide some default colors
        if (mProgressColor == null) {
            int[][] states = new int[][]{new int[]{android.R.attr.state_pressed}, new int[]{-android.R.attr.state_enabled}, new int[]{}};
            int[] colors = new int[]{getAttrColor(context, ACTIVATED_COLOR), 0, getAttrColor(context, PROGRESS_COLOR)};

            mProgressColor = new ColorStateList(states, colors);
        }

        // Create a default drawable
        if (mThumbDrawable == null) {
            mThumbDrawable = createThumbDrawable(context);
        }

        mThumbDrawable.setCallback(this);

        // Create a ripple background
        if (getBackground() == null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                setBackground(createRippleDrawable(context));
            }
        }

        // Sanitize the input values
        mStartAngle = mStartAngle % 360;
        mSweepAngle = (360 + mSweepAngle % 360) % 360;

        mMin = mMin < 0 ? 0 : Math.min(mMin, mMax);
        mProgress = mProgress < mMin ? mMin : Math.min(mProgress, mMax);

        mScroller = new Scroller(context);
        mScroller.setFinalX((int) (getStepAngleFromStep(mProgress - mMin) * MULTIPLIER));

        // Initialize the drawing objects
        mDrawRectF = new RectF();
        mSweepPath = new Path();
        mProgressPath = new Path();

        mSweepPaint = new Paint();
        mSweepPaint.setAntiAlias(true);
        mSweepPaint.setStrokeCap(Paint.Cap.ROUND);
        mSweepPaint.setStyle(Paint.Style.STROKE);

        mProgressPaint = new Paint();
        mProgressPaint.setAntiAlias(true);
        mProgressPaint.setStrokeCap(Paint.Cap.ROUND);
        mProgressPaint.setStyle(Paint.Style.STROKE);

        // Create reusable point objects
        mThumbOrb = new Point();
        mStartOrb = new Point();
        mEndOrb = new Point();

        // Updates refreshDrawableState()
        setEnabled(enabled);
    }

    @Nullable
    @Override
    protected Parcelable onSaveInstanceState() {
        SavedState savedState = new SavedState(super.onSaveInstanceState());
        savedState.progressAngle = mScroller.getFinalX() / MULTIPLIER;

        return savedState;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        SavedState savedState = (SavedState) state;
        super.onRestoreInstanceState(savedState.getSuperState());

        // Use angle for ScrollMode.DRIFT
        mProgress = getStepFromAngle(savedState.progressAngle);
        mLastUpdate = mProgress;
        mScroller.setFinalX((int) (savedState.progressAngle * MULTIPLIER));

        onProgressChanged();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = getMeasurement(widthMeasureSpec, dpToPixels(getContext(), VIEW_WIDTH));
        int height = getMeasurement(heightMeasureSpec, dpToPixels(getContext(), VIEW_HEIGHT));

        setMeasuredDimension(width, height);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        onUpdateDrawableState();
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        return who == mThumbDrawable || super.verifyDrawable(who);
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        onUpdateDrawableState();
    }

    @Override
    public void drawableHotspotChanged(float x, float y) {
        super.drawableHotspotChanged(x, y);

        if (mThumbDrawable != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mThumbDrawable.setHotspot(x, y);
            }
        }
    }

    @Override
    public void jumpDrawablesToCurrentState() {
        super.jumpDrawablesToCurrentState();

        if (mThumbDrawable != null) {
            mThumbDrawable.jumpToCurrentState();
        }
    }

    @Override
    public void setLayoutDirection(int layoutDirection) {
        super.setLayoutDirection(layoutDirection);
        onUpdateDrawableState();
    }

    @Override
    public void setPadding(int left, int top, int right, int bottom) {
        super.setPadding(left, top, right, bottom);
        onUpdateDrawableState();
    }

    @Override
    public void setPaddingRelative(int start, int top, int end, int bottom) {
        super.setPaddingRelative(start, top, end, bottom);
        onUpdateDrawableState();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Get angle and scroll status
        boolean scrolling = mScroller.computeScrollOffset();
        float angle = mScroller.getCurrX() / MULTIPLIER;

        if (scrolling) {
            mProgress = getStepFromAngle(angle) + mMin;
            onProgressChanged();
        }

        // (Re)draw the sweep arc
        mSweepPath.reset();
        mSweepPath.addArc(mDrawRectF, mStartAngle, mSweepAngle);
        canvas.drawPath(mSweepPath, mSweepPaint);

        // (Re)draw the progress arc
        mProgressPath.reset();
        mProgressPath.addArc(mDrawRectF, mStartAngle, angle);
        canvas.drawPath(mProgressPath, mProgressPaint);

        getPoint(mThumbOrb, angle);

        // Downcast the floats here
        int left = mThumbOrb.x - (int) mThumbRadius;
        int top = mThumbOrb.y - (int) mThumbRadius;
        int right = left + (int) mThumbRadius * 2;
        int bottom = top + (int) mThumbRadius * 2;

        if (mThumbDrawable != null) {
            // Ratio for MAX_LEVEL compatibility
            int level = (int) (angle / mSweepAngle * MAX_LEVEL + 0.5);

            mThumbDrawable.setLevel(level);
            mThumbDrawable.setBounds(left, top, right, bottom);
            mThumbDrawable.draw(canvas);
        }

        if (getBackground() != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                getBackground().setHotspotBounds(left, top, right, bottom);
            }
        }

        if (scrolling) {
            invalidate();
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        super.onTouchEvent(event);

        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (!isEnabled()) return false;
                boolean state = startScroll(x, y);
                drawableHotspotChanged(x, y);
                getParent().requestDisallowInterceptTouchEvent(state);
                setPressed(state);
                return state;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                getParent().requestDisallowInterceptTouchEvent(false);
                setPressed(false);
                finishScroll();
                return true;
            case MotionEvent.ACTION_MOVE:
                updateScroll(x, y);
                drawableHotspotChanged(x, y);
                return true;
            default:
                return false;
        }
    }

    /**
     * Shared method to update the parameters and drawing objects before invalidating the view.
     * Overriding classes should call super.
     */
    @CallSuper
    protected void onUpdateDrawableState() {
        mTouchRadius = Math.max(mThumbRadius, mStrokeWidth / 2);

        float paddingStart = getPaddingLeft();
        float paddingEnd = getPaddingRight();

        // Use RTL if available
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            paddingStart = getPaddingStart();
            paddingEnd = getPaddingEnd();
        }

        // Calculate the drawing space
        float left = mTouchRadius + paddingStart;
        float top = mTouchRadius + getPaddingTop();
        float right = getWidth() - mTouchRadius - paddingEnd;
        float bottom = getHeight() - mTouchRadius - getPaddingBottom();

        mDrawRectF.set(left, top, right, bottom);

        // Update sweep paint
        mSweepPaint.setColor(mSweepColor);
        mSweepPaint.setStrokeWidth(mStrokeWidth);

        int statefulColor = mProgressColor.getColorForState(getDrawableState(), mProgressColor.getDefaultColor());

        // Update progress paint
        mProgressPaint.setColor(statefulColor);
        mProgressPaint.setStrokeWidth(mStrokeWidth);

        // Update the drawable state
        if (mThumbDrawable != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                mThumbDrawable.setLayoutDirection(getLayoutDirection());
            }

            mThumbDrawable.setState(getDrawableState());
        }

        // Adjust background drawable
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (getBackground() instanceof RippleDrawable) {
                ((RippleDrawable) getBackground()).setRadius((int) mThumbRadius * 2);
            }
        }

        // Reuse the point objects
        getPoint(mStartOrb, 0);
        getPoint(mEndOrb, mSweepAngle);

        invalidate();
    }

    /**
     * Check to see if a given point is within the ellipse. If touchInside is false it will use the
     * greater of thumbRadius or half the strokeWidth to section out the core.
     *
     * @param x The X axis.
     * @param y The Y axis.
     * @return True if it is within the orbit.
     */
    public boolean isInsideOrbit(float x, float y) {
        float paddingStart = Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 ? getPaddingStart() : getPaddingLeft();

        // Calculate ellipse parameters
        float axisX = x - mDrawRectF.centerX();
        float axisY = y - mDrawRectF.centerY();
        float a = mDrawRectF.centerX() - paddingStart;
        float b = mDrawRectF.centerY() - getPaddingTop();

        // Check if inside bounds
        boolean outer = isInsideEllipse(axisX, axisY, a, b);
        boolean inner = isInsideEllipse(axisX, axisY, a - mTouchRadius * 2, b - mTouchRadius * 2);

        return outer && !(inner && !mTouchInside);
    }

    /**
     * Find the angle of a point from the center of the ellipse. This is relative to the startAngle.
     *
     * @param x The X axis.
     * @param y The Y axis.
     * @return Angle from the ellipse.
     */
    public float getAngle(float x, float y) {
        float axisX = x - mDrawRectF.centerX();
        float axisY = y - mDrawRectF.centerY();

        // t = atan2(cx - x / cy - y) * 180 / PI
        double angle = Math.toDegrees(Math.atan2(axisY, axisX)) - mStartAngle;
        return (float) (360 + angle % 360) % 360;
    }

    /**
     * Find the point of an angle from the center of the ellipse. This is on the ellipse line.
     *
     * @param angle The given angle.
     * @return A point on the ellipse.
     */
    public Point getPoint(float angle) {
        return getPoint(new Point(), angle);
    }

    /**
     * Find the point of an angle from the center of the ellipse. This is on the ellipse line.
     *
     * @param point Point object to set.
     * @param angle The given angle.
     * @return A point on the ellipse.
     */
    private Point getPoint(Point point, float angle) {
        // x = a cos(t), y = b sin(t)
        double axisX = mDrawRectF.width() / 2 * Math.cos(Math.toRadians(angle + mStartAngle)) + mDrawRectF.centerX();
        double axisY = mDrawRectF.height() / 2 * Math.sin(Math.toRadians(angle + mStartAngle)) + mDrawRectF.centerY();

        point.set((int) (axisX + 0.5), (int) (axisY + 0.5));
        return point;
    }

    /**
     * Find if a given point is inside an ellipse. This is relative to the given axis.
     *
     * @param x The X axis.
     * @param y The Y axis.
     * @param a Semi-major axis.
     * @param b Semi-minor axis.
     * @return True if it is inside the ellipse.
     */
    private boolean isInsideEllipse(float x, float y, float a, float b) {
        // 1 = (x^2 / a^2) + (y^2 / b^2)
        return 1 >= (x * x) / (a * a) + (y * y) / (b * b);
    }

    /**
     * Find the progress step of an angle. This is based on the sweepAngle.
     *
     * @param angle The given angle.
     * @return Progress step.
     */
    private int getStepFromAngle(float angle) {
        float steps = mMax - mMin;
        float rise = mSweepAngle / steps;

        // Downcast the float here
        return (int) (((angle + (rise / 2)) / rise) % (steps + 1));
    }

    /**
     * Find the progress step angle of an angle. This is based on the sweepAngle.
     *
     * @param angle The given angle.
     * @return Progress step angle.
     */
    private float getStepAngleFromAngle(float angle) {
        return mSweepAngle / (mMax - mMin) * getStepFromAngle(angle);
    }

    /**
     * Find the progress step angle of a step. This is based on the sweepAngle.
     *
     * @param step Progress step.
     * @return Progress step angle.
     */
    private float getStepAngleFromStep(int step) {
        return mSweepAngle / (mMax - mMin) * step;
    }

    /**
     * Check to see if a scroll event should be initiated. If touchInside is false it will use the
     * sweepAngle to section out the slice.
     *
     * @param x The X axis.
     * @param y The Y axis.
     * @return True if the event should be allowed.
     */
    private boolean startScroll(float x, float y) {
        mLastUpdate = -1;

        boolean start = isInsideEllipse(x - mStartOrb.x, y - mStartOrb.y, mTouchRadius, mTouchRadius);
        boolean end = isInsideEllipse(x - mEndOrb.x, y - mEndOrb.y, mTouchRadius, mTouchRadius);

        // Section out the arc orbit
        if (isInsideOrbit(x, y) && (mTouchInside || (getAngle(x, y) < mSweepAngle + 1 || start || end))) {
            return updateScroll(x, y);
        }

        return false;
    }

    /**
     * Process a scroll event to update the scroller. If the onChanging listener returns false the
     * change is abandoned.
     *
     * @param x The X axis.
     * @param y The Y axis.
     * @return True if the scroller is updated.
     */
    private boolean updateScroll(float x, float y) {
        float angle = getAngle(x, y);
        boolean thumb = isInsideEllipse(x - mThumbOrb.x, y - mThumbOrb.y, mTouchRadius, mTouchRadius);

        // Divide up the pie slice
        if (angle > mSweepAngle && !thumb) {
            angle = angle > 360 - ((360 - mSweepAngle) / 2) ? 0 : mSweepAngle;
        }

        // Process the touch angle
        if (angle < mSweepAngle + 1) {
            if (mScroller.computeScrollOffset()) {
                mScroller.forceFinished(true);
            }

            // Check the onChanging listener
            if (onProgressChanging(getStepFromAngle(angle) + mMin)) {
                switch (mScrollMode) {
                    case ScrollMode.DRIFT:
                        mScroller.startScroll(mScroller.getCurrX(), 0, (int) (angle * MULTIPLIER) - mScroller.getCurrX(), 0);
                        break;
                    case ScrollMode.GRAVITY:
                        mScroller.startScroll(mScroller.getCurrX(), 0, (int) (getStepAngleFromAngle(angle) * MULTIPLIER) - mScroller.getCurrX(), 0);
                        break;
                    case ScrollMode.SNAP:
                        mScroller.setFinalX((int) (getStepAngleFromAngle(angle) * MULTIPLIER));
                        break;
                }
            }

            invalidate();
            return true;
        }

        // Update the onChanged listener
        if (mScroller.isFinished()) {
            mScroller.setFinalX(mScroller.getCurrX());
        }

        return mTouchInside || thumb;
    }

    /**
     * Complete a scroll event by invalidating the view and updating the onChanged listener.
     */
    private void finishScroll() {
        //mScroller.startScroll(mScroller.getCurrX(), 0, (int) (getStepAngleFromAngle(mScroller.getFinalX() / MULTIPLIER) * MULTIPLIER) - mScroller.getCurrX(), 0);
        //mScroller.setFinalX((int) (getStepAngleFromAngle(mScroller.getFinalX() / MULTIPLIER) * MULTIPLIER));

        invalidate();
        onProgressChanged();
    }

    /**
     * Update the onProgressChanging listener with the given progress level. If the client returns
     * false any changes should be abandoned.
     *
     * @param progress Progress level.
     * @return True if the client approves.
     */
    protected boolean onProgressChanging(int progress) {
        if (mOnProgressChangeListener != null) {
            return mOnProgressChangeListener.onProgressChanging(this, progress);
        }

        return true;
    }

    /**
     * Update the onProgressChanged listener with the current progress level. When the touch event
     * is completed finish will be set to true.
     */
    protected void onProgressChanged() {
        boolean finished = !isPressed() && mScroller.isFinished();

        // Prevent noisy updates
        if (mOnProgressChangeListener != null && (mProgress != mLastUpdate || finished)) {
            mLastUpdate = mProgress;
            mOnProgressChangeListener.onProgressChanged(this, mProgress, finished);
        }
    }

    /**
     * Create a default thumb drawable. Material Design recommendations are followed for lighting.
     *
     * @param context Context given for the view. This determines the resources and theme.
     * @return The thumb drawable.
     */
    private static Drawable createThumbDrawable(Context context) {
        int px = dpToPixels(context, 1);

        ShapeDrawable spacer = new ShapeDrawable(new OvalShape());
        spacer.getPaint().setColor(0x00000000);
        spacer.setPadding(px, px, px, 0);

        ShapeDrawable ambient = new ShapeDrawable(new OvalShape());
        ambient.getPaint().setColor(0x10000000);
        ambient.setPadding(px, px, px, px);

        ShapeDrawable key = new ShapeDrawable(new OvalShape());
        key.getPaint().setColor(0x20000000);
        key.setPadding(0, 0, 0, px);

        ShapeDrawable thumb = new ShapeDrawable(new OvalShape());
        thumb.getPaint().setColor(THUMB_COLOR);

        return new LayerDrawable(new Drawable[]{spacer, ambient, key, thumb});
    }

    /**
     * Create a default ripple drawable. It is used for visual confirmation of touch interaction.
     *
     * @param context Context given for the view. This determines the resources and theme.
     * @return The ripple drawable.
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private static Drawable createRippleDrawable(Context context) {
        ColorStateList rippleColor = ColorStateList.valueOf(getAttrColor(context, RIPPLE_COLOR));
        return new RippleDrawable(rippleColor, null, null);
    }

    /**
     * Utility method to find the preferred measurements of this view for the view parent.
     *
     * @param measureSpec Constraint imposed by the parent.
     * @param defaultSize Default size of the view.
     * @return Preferred size for this view.
     * @see #getDefaultSize(int, int)
     */
    private static int getMeasurement(int measureSpec, int defaultSize) {
        int mode = MeasureSpec.getMode(measureSpec);
        int size = MeasureSpec.getSize(measureSpec);

        switch (mode) {
            case MeasureSpec.EXACTLY:
                return size;
            case MeasureSpec.AT_MOST:
                return Math.min(size, defaultSize);
            case MeasureSpec.UNSPECIFIED:
            default:
                return defaultSize;
        }
    }

    /**
     * Utility method to find a color as defined in the attribute of a theme.
     *
     * @param context   Context given for the attribute. This determines the resources and theme.
     * @param attrResId The color resource.
     * @return An ARGB color integer.
     */
    @ColorInt
    private static int getAttrColor(Context context, @AttrRes int attrResId) {
        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(attrResId, typedValue, true);

        return typedValue.data;
    }

    /**
     * Utility method to find the pixel resolution of a density pixel value.
     *
     * @param context Context given for the metrics. This determines the resources and theme.
     * @param dp      Density pixels to convert.
     * @return The pixel resolution.
     */
    private static int dpToPixels(Context context, @Dimension float dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density + 0.5);
    }

    /**
     * Utility method to find the density pixel value of a pixel resolution.
     *
     * @param context Context given for the metrics. This determines the resources and theme.
     * @param px      Pixel resolution to convert.
     * @return The density pixels.
     */
    @Dimension
    private static float pixelsToDp(Context context, int px) {
        return px / context.getResources().getDisplayMetrics().density;
    }

    /**
     * Get the start angle of the arc. This is relative to 90 degrees clockwise.
     *
     * @return The start angle.
     */
    public float getStartAngle() {
        return mStartAngle;
    }

    /**
     * Set the start angle of the arc. This is relative to 90 degrees clockwise.
     *
     * @param startAngle The start angle.
     */
    public void setStartAngle(float startAngle) {
        mStartAngle = startAngle % 360;
        onUpdateDrawableState();
    }

    /**
     * Get the stroke width of the arc. This is also used to define the arc padding and touch area
     * if it is larger than twice the thumbRadius.
     *
     * @return The stroke width.
     */
    public float getStrokeWidth() {
        return mStrokeWidth;
    }

    /**
     * Set the stroke width of the arc. This is also used to define the arc padding and touch area
     * if it is larger than twice the thumbRadius.
     *
     * @param strokeWidth The stroke width.
     */
    public void setStrokeWidth(float strokeWidth) {
        mStrokeWidth = strokeWidth;
        onUpdateDrawableState();
    }

    /**
     * Get the sweep angle of the arc. This is clockwise from the startAngle.
     *
     * @return The sweep angle.
     */
    public float getSweepAngle() {
        return mSweepAngle;
    }

    /**
     * Set the sweep angle of the arc. This is clockwise from the startAngle and may normalize the
     * progress angle if necessary.
     *
     * @param sweepAngle The sweep angle.
     */
    public void setSweepAngle(float sweepAngle) {
        mSweepAngle = (360 + sweepAngle % 360) % 360;
        setProgress(mProgress);
        //onUpdateDrawableState();
    }

    /**
     * Get the sweep color of the arc. This is the primary color used to draw the widget.
     *
     * @return The sweep color.
     */
    @ColorInt
    public int getSweepColor() {
        return mSweepColor;
    }

    /**
     * Set the sweep color of the arc. This is the primary color used to draw the widget.
     *
     * @param sweepColor The sweep color.
     */
    public void setSweepColor(@ColorInt int sweepColor) {
        mSweepColor = sweepColor;
        onUpdateDrawableState();
    }

    /**
     * Get the progress color state list. This is the active color state used to draw the progress.
     *
     * @return The progress color.
     */
    public ColorStateList getProgressColor() {
        return mProgressColor;
    }

    /**
     * Set the progress color state list. This is the color state used to draw the progress arc.
     *
     * @param colorStateList The progress color.
     */
    public void setProgressColor(@Nullable ColorStateList colorStateList) {
        mProgressColor = colorStateList == null ? ColorStateList.valueOf(0) : colorStateList;
        onUpdateDrawableState();
    }

    /**
     * Set the progress color. This is the color used to draw the progress arc.
     *
     * @param progressColor The progress color.
     */
    public void setProgressColor(@ColorInt int progressColor) {
        mProgressColor = ColorStateList.valueOf(progressColor);
        onUpdateDrawableState();
    }

    /**
     * Get the thumb drawable. A default drawable is assigned during object initiation if one has
     * not been provided by the client. This can be used to reference it.
     *
     * @return The thumb drawable.
     */
    @Nullable
    public Drawable getThumbDrawable() {
        return mThumbDrawable;
    }

    /**
     * Set the thumb drawable. A default drawable is assigned during object initiation if one has
     * not been provided by the client. This can be set to null to remove it.
     *
     * @param drawable The thumb drawable.
     */
    public void setThumbDrawable(@Nullable Drawable drawable) {
        if (mThumbDrawable != null) {
            mThumbDrawable.setCallback(null);
            unscheduleDrawable(mThumbDrawable);
        }

        if (drawable != null) {
            drawable.setCallback(this);
        }

        mThumbDrawable = drawable;
        onUpdateDrawableState();
    }

    /**
     * Set the thumb drawable resource. A default drawable is assigned during object initiation if
     * one has not been provided by the client.
     *
     * @param drawableResId The thumb resource.
     */
    @SuppressLint("UseCompatLoadingForDrawables")
    public void setThumbDrawableResource(@DrawableRes int drawableResId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setThumbDrawable(getResources().getDrawable(drawableResId, getContext().getTheme()));
        } else {
            setThumbDrawable(getResources().getDrawable(drawableResId));
        }

        //onUpdateDrawableState();
    }

    /**
     * Get the radius of the thumb drawable. This is also used to define the arc padding and touch
     * area if it is larger than half the strokeWidth.
     *
     * @return The thumb drawable radius.
     */
    public float getThumbRadius() {
        return mThumbRadius;
    }

    /**
     * Set the radius of the thumb drawable. This is also used to define the arc padding and touch
     * area if it is larger than half the strokeWidth.
     *
     * @param thumbRadius The thumb drawable radius.
     */
    public void setThumbRadius(float thumbRadius) {
        mThumbRadius = thumbRadius;
        onUpdateDrawableState();
    }

    /**
     * Get the scroll mode used for touch events. The enumeration values are shared with a styleable
     * XML attribute of the same name.
     *
     * @return The scroll mode.
     */
    @ScrollMode
    public int getScrollMode() {
        return mScrollMode;
    }

    /**
     * Set the scroll mode used for touch events. The enumeration values are shared with a styleable
     * XML attribute of the same name.
     *
     * @param scrollMode The scroll mode.
     */
    public void setScrollMode(@ScrollMode int scrollMode) {
        mScrollMode = scrollMode;

        // Home the angle if necessary
        if (mScrollMode == ScrollMode.GRAVITY) {
            setProgress(mProgress, true);
        } else if (mScrollMode == ScrollMode.SNAP) {
            setProgress(mProgress);
        }

        //onUpdateDrawableState();
    }

    /**
     * Check the touch inside state. Touch events may be initiated from inside the ellipse if this
     * is enabled.
     *
     * @return True if touch inside is enabled.
     */
    public boolean hasTouchInside() {
        return mTouchInside;
    }

    /**
     * Set the touch inside state. Touch events may be initiated from inside the ellipse if this is
     * enabled.
     *
     * @param touchInside True if touch inside is enabled.
     */
    public void setTouchInside(boolean touchInside) {
        mTouchInside = touchInside;
    }

    /**
     * Get the min progress. This will be less than or equal to the max level.
     *
     * @return The min progress level.
     */
    public int getMin() {
        return mMin;
    }

    /**
     * Set the min progress. This should be less than or equal to the max level and may normalize
     * the progress level if necessary.
     *
     * @param min The min progress level.
     */
    public void setMin(int min) {
        mMin = min < 0 ? 0 : Math.min(min, mMax);
        setProgress(mProgress);
        //onUpdateDrawableState();
    }

    /**
     * Get the max progress. This will be greater than or equal to the min level.
     *
     * @return The max progress level.
     */
    public int getMax() {
        return mMax;
    }

    /**
     * Set the max progress. This should be greater than or equal to the min level and may normalize
     * the progress level if necessary.
     *
     * @param max The max progress level.
     */
    public void setMax(int max) {
        mMax = Math.max(max, mMin);
        setProgress(mProgress);
        //onUpdateDrawableState();
    }

    /**
     * Get the current progress. This will be within the set min and max levels.
     *
     * @return The current progress level.
     */
    public int getProgress() {
        return mProgress;
    }

    /**
     * Set the current progress. This should be within the set min and max levels and may be
     * normalized if necessary.
     *
     * @param progress The current progress level.
     */
    public void setProgress(int progress) {
        setProgress(progress, false);
        //onUpdateDrawableState();
    }

    /**
     * Set the current progress. This should be within the set min and max levels and may be
     * normalized if necessary.
     *
     * @param progress The current progress level.
     * @param animate  True if it should animate the change.
     */
    public void setProgress(int progress, boolean animate) {
        mProgress = progress < mMin ? mMin : Math.min(progress, mMax);

        // Don't block setScrollMode() updates
        if (animate) {
            mScroller.computeScrollOffset();
            mScroller.startScroll(mScroller.getCurrX(), 0, (int) (getStepAngleFromStep(mProgress - mMin) * MULTIPLIER) - mScroller.getCurrX(), 0);
        } else {
            mScroller.setFinalX((int) (getStepAngleFromStep(mProgress - mMin) * MULTIPLIER));
        }

        onUpdateDrawableState();
    }

    /**
     * Get the progress listener for this instance. The interface is used to notify the client of
     * any touch initiated changes.
     *
     * @return SeekBar notification listener.
     */
    @Nullable
    public OnProgressChangeListener getOnProgressChangeListener() {
        return mOnProgressChangeListener;
    }

    /**
     * Set the progress listener for this instance. The interface is used to notify the client of
     * any touch initiated changes.
     *
     * @param onProgressChangeListener SeekBar notification listener.
     */
    public void setOnProgressChangeListener(@Nullable OnProgressChangeListener onProgressChangeListener) {
        mOnProgressChangeListener = onProgressChangeListener;
    }

    /**
     * Inner class to save and restore the instance state. This extends a base class used by the
     * parent to package data using the Parcelable.Creator interface.
     */
    static class SavedState extends BaseSavedState {

        private float progressAngle;

        public SavedState(Parcelable superState) {
            super(superState);
        }

        protected SavedState(Parcel in) {
            super(in);
            progressAngle = in.readFloat();
        }

        public static final Creator<SavedState> CREATOR =
                new Creator<SavedState>() {
                    @Override
                    public SavedState createFromParcel(Parcel in) {
                        return new SavedState(in);
                    }

                    @Override
                    public SavedState[] newArray(int size) {
                        return new SavedState[size];
                    }
                };

        @Override
        public int describeContents() {
            return super.describeContents();
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeFloat(progressAngle);
        }
    }
}