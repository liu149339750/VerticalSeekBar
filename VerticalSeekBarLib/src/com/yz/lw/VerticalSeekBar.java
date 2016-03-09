/*
 *    Copyright (C) 2015 Haruki Hasegawa
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

/* This file contains AOSP code copied from /frameworks/base/core/java/android/widget/AbsSeekBar.java */
/*============================================================================*/
/*
 * Copyright (C) 2007 The Android Open Source Project
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
/*============================================================================*/

package com.yz.lw;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import com.example.verticalseekbarlib.R;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Paint.Align;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
import android.graphics.drawable.shapes.Shape;
import android.os.Build;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ViewParent;
import android.widget.SeekBar;

//mDirect与xml文件中的gravity配合使用，否则进度方向会反。ClipeDrawable没法修改方向

public class VerticalSeekBar extends SeekBar {
	public static final int FROM_TOP_TO_BUTTOM = 1;
	public static final int FROM_BUTTOM_TO_TOP = 2;

	private boolean mIsDragging;
	private Drawable mThumb_;
//	private Drawable mTouchThumb;
	private Method mMethodSetProgress;
	private int mDirect = FROM_BUTTOM_TO_TOP; //change with clip gravity in progress_drawable.xml

	private Drawable mProgressDrawable;
	private static final int MAX_LEVEL = 10000;

	private int mMaxHeight = 48;
	private int mMaxWidth = 0;

	private Paint mPaint;
	private int textSize = 28;
	private int color = Color.parseColor("#FA7777");
	
	private int mMinProgress = 0;
	
	private final int minWidth = 3;
	
	private PositionListener mListener;
	private int cx;
	
	public VerticalSeekBar(Context context) {
		super(context);
		initialize(context, null, 0, 0);
	}

	public VerticalSeekBar(Context context, AttributeSet attrs) {
		super(context, attrs);
		initialize(context, attrs, 0, 0);
	}

	public VerticalSeekBar(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		initialize(context, attrs, defStyle, 0);
	}

	private void initialize(Context context, AttributeSet attrs,
			int defStyleAttr, int defStyleRes) {
		TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.ThemeableVerticalSeekBar, defStyleAttr, 0);
		
		mMinProgress = ta.getInt(R.styleable.ThemeableVerticalSeekBar_min, 0);
		ta.recycle();
		Drawable d = getResources().getDrawable(R.drawable.progress_drawable);
		mPaint = new Paint();
		mPaint.setTextSize(textSize);
		mPaint.setColor(color);
		mPaint.setTextAlign(Align.CENTER);
		
		if (d != null) {
			d = tileify(d, false);
			setProgressDrawable(d);
		}
	}
	
	public void setPositionListener(PositionListener listener) {
		mListener = listener;
	}

	public void setProgressDrawable(Drawable d) {
		// super.setProgressDrawable(d);  //交给父View处理进度问题,可以托管，也可以自己管理
		boolean needUpdate;
		if (mProgressDrawable != null && d != mProgressDrawable) {
			mProgressDrawable.setCallback(null);
			needUpdate = true;
		} else {
			needUpdate = false;
		}

		if (d != null) {
			d.setCallback(this);

			// Make sure the ProgressBar is always wide enough
			int drawableWidth = d.getMinimumWidth();
			if (mMaxWidth < drawableWidth) {
				mMaxWidth = drawableWidth;
				requestLayout();
			}
		}
//		pw = d.getIntrinsicWidth();
		mProgressDrawable = d;
		postInvalidate();

		if (needUpdate) {
//			updateDrawableBounds(getWidth(), getHeight());
			// updateDrawableState();
			doRefreshProgress(android.R.id.progress, super.getProgress(), false,
					false);
		}
	}

	@Override
	public Drawable getProgressDrawable() {
		return mProgressDrawable;
	}
	
	@Override
	public synchronized int getProgress() {
		return super.getProgress() + mMinProgress;
	}
	
	@Override
	public synchronized void setMax(int max) {
		super.setMax(max - mMinProgress);
	}
	
	public int getMinProgress(){
		return mMinProgress;
	}

	/**no need now.update in updateThumbPos*/
	private void updateDrawableBounds(int w, int h) {
		// onDraw will translate the canvas so we draw starting at 0,0.
		// Subtract out padding for the purposes of the calculations below.
		w -= getPaddingRight() + getPaddingLeft();
		h -= getPaddingTop() + getPaddingBottom();
		if(w < minWidth)
			w = minWidth;
		int right = w;
		int bottom = h;

		if (mProgressDrawable != null) {
			mProgressDrawable.setBounds(0, 0, right, bottom);
		}
	}
	
	
	@Override
	protected void onLayout(boolean changed, int l, int top, int r, int bottom) {
		super.onLayout(changed, l, top, r, bottom);
		cx = l + (r - l)/2;
	}
	
	private Drawable tileify(Drawable drawable, boolean clip) {
		if (drawable instanceof LayerDrawable) {
			LayerDrawable background = (LayerDrawable) drawable;
			final int N = background.getNumberOfLayers();
			Drawable[] outDrawables = new Drawable[N];

			for (int i = 0; i < N; i++) {
				int id = background.getId(i);
				outDrawables[i] = tileify(background.getDrawable(i),
						(id == android.R.id.progress));
			}

			LayerDrawable newBg = new LayerDrawable(outDrawables);

			for (int i = 0; i < N; i++) {
				newBg.setId(i, background.getId(i));
			}
			return newBg;

		} else if (drawable instanceof BitmapDrawable) {
			final Bitmap tileBitmap = ((BitmapDrawable) drawable).getBitmap();
			// if (mSampleTile == null) {
			// mSampleTile = tileBitmap;
			// }

			final ShapeDrawable shapeDrawable = new ShapeDrawable(
					getDrawableShape());

			final BitmapShader bitmapShader = new BitmapShader(tileBitmap,
					Shader.TileMode.REPEAT, Shader.TileMode.CLAMP);
			shapeDrawable.getPaint().setShader(bitmapShader);
			return (clip) ? new ClipDrawable(shapeDrawable, mDirect == FROM_TOP_TO_BUTTOM ? Gravity.TOP : Gravity.BOTTOM,
					ClipDrawable.VERTICAL) : shapeDrawable;
		} else if(drawable instanceof ClipDrawable) {
			// no way to do
		}

		return drawable;
	}

	private synchronized void doRefreshProgress(int id, int progress,
			boolean fromUser, boolean callBackToApp) {
		int mMax = getMax();
		float scale = mMax > 0 ? (float) progress / (float) (mMax - mMinProgress) : 0;
		final Drawable d = getProgressDrawable();
		if (d != null) {
			Drawable progressDrawable = null;

			if (d instanceof LayerDrawable) {
				progressDrawable = ((LayerDrawable) d)
						.findDrawableByLayerId(id);
			}

			final int level = (int) (scale * MAX_LEVEL);
			(progressDrawable != null ? progressDrawable : d).setLevel(level);
		} else {
			invalidate();
		}
		if (callBackToApp && id == android.R.id.progress) {
			// onProgressRefresh(scale, fromUser);
		}
	}

	Shape getDrawableShape() {
		final float[] roundedCorners = new float[] { 5, 5, 5, 5, 5, 5, 5, 5 };
		return new RoundRectShape(roundedCorners, null, null);
	}

	@Override
	protected synchronized void onMeasure(int widthMeasureSpec,
			int heightMeasureSpec) {
		Drawable d = getProgressDrawable();
		Drawable mThumb = mThumb_;

		int thumbHeight = mThumb == null ? 0 : mThumb.getIntrinsicHeight();
		int thumbWidth = mThumb == null ? 0 : mThumb.getIntrinsicWidth();
		int dw = 0;
		int dh = 0;
		if (d != null) {
			dw = d.getIntrinsicWidth();
			dw = Math.max(thumbWidth, Math.min(mMaxWidth, dw));
			dh = Math.max(getMinimumHeight(), d.getIntrinsicHeight());
			dh = Math.max(thumbHeight, dh);
		}
		dw += getPaddingLeft() + getPaddingRight();
		dh += getPaddingTop() + getPaddingBottom();

		setMeasuredDimension(resolveSizeAndState(dw, widthMeasureSpec, 0),
				resolveSizeAndState(dh, heightMeasureSpec, 0));
		
	}

	@Override
	public void setThumb(Drawable thumb) {
		mThumb_ = thumb;
//		mTouchThumb = getResources().getDrawable(R.drawable.oval);
		int h = thumb.getIntrinsicHeight();
		int pt = getPaddingTop();
		int pd = getPaddingBottom();
		if (pt < h / 2 || pd < h / 2) {
			if (pt < h / 2)
				pt = h / 2;
			if (pd < h / 2)
				pd = h / 2;
			setPadding(getPaddingLeft(), pt, getPaddingRight(), pd);
		}
		super.setThumb(thumb);
		setThumbOffset(mThumb_.getIntrinsicHeight() / 2);
		updateThumbPos(getWidth(), getHeight());
	}

	private Drawable getThumbCompat() {
		return mThumb_;
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		return onTouchEventTraditionalRotation(event);
	}

	private boolean onTouchEventTraditionalRotation(MotionEvent event) {
		if (!isEnabled()) {
			return false;
		}

		final Drawable mThumb = getThumbCompat();

		switch (event.getAction()) {
		case MotionEvent.ACTION_DOWN:
			setPressed(true);
			if (mThumb != null) {
				// This may be within the padding region
				invalidate(mThumb.getBounds());
			}
			if(mListener != null)
				mListener.onTouchStart(this,cx,event.getRawY(),getProgress());
			onStartTrackingTouch();
			trackTouchEvent(event);
			attemptClaimDrag(true);
			break;

		case MotionEvent.ACTION_MOVE:
			if (mIsDragging) {
				trackTouchEvent(event);
			}
			break;

		case MotionEvent.ACTION_UP:
			if (mIsDragging) {
				trackTouchEvent(event);
				onStopTrackingTouch();
				setPressed(false);
			} else {
				// Touch up when we never crossed the touch slop threshold
				// should
				// be interpreted as a tap-seek to that location.
				onStartTrackingTouch();
				trackTouchEvent(event);
				onStopTrackingTouch();
				attemptClaimDrag(false);
			}
			if(mListener != null)
				mListener.onTouchStop(this);
			// ProgressBar doesn't know to repaint the thumb drawable
			// in its inactive state when the touch stops (because the
			// value has not apparently changed)
			invalidate();
			break;

		case MotionEvent.ACTION_CANCEL:
			if (mIsDragging) {
				onStopTrackingTouch();
				setPressed(false);
			}
			if(mListener != null)
				mListener.onTouchStop(this);
			invalidate(); // see above explanation
			break;
		}
		return true;
	}

	private void trackTouchEvent(MotionEvent event) {
		final int paddingTop = super.getPaddingTop();
		final int paddingBottom = super.getPaddingBottom();
		final int height = getHeight();
		
		final int available = height - paddingTop - paddingBottom;
		int y = (int) event.getY();
		final float scale;
		float value = 0;

		switch (mDirect) {
		case FROM_TOP_TO_BUTTOM:
			value = y - paddingTop;
			break;
		case FROM_BUTTOM_TO_TOP:
			value = (height - paddingBottom) - y;
			break;
		}

		if (value < 0 || available == 0) {
			scale = 0.0f;
		} else if (value > available) {
			scale = 1.0f;
		} else {
			scale = value / (float) available;
		}

		final int max = getMax();
		final float progress = scale * (max - mMinProgress);
		setProgress((int) progress, true);
		
		if(mListener != null ){
			mListener.position(this,cx, event.getRawY(),getProgress());
		}
	}

	/**
	 * Tries to claim the user's drag motion, and requests disallowing any
	 * ancestors from stealing events in the drag.
	 */
	private void attemptClaimDrag(boolean active) {
		final ViewParent parent = getParent();
		if (parent != null) {
			parent.requestDisallowInterceptTouchEvent(active);
		}
	}

	/**
	 * This is called when the user has started touching this widget.
	 */
	private void onStartTrackingTouch() {
		mIsDragging = true;
	}

	/**
	 * This is called when the user either releases his touch or the touch is
	 * canceled.
	 */
	private void onStopTrackingTouch() {
		mIsDragging = false;
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (isEnabled()) {
			final boolean handled;
			int direction = 0;

			switch (keyCode) {
			case KeyEvent.KEYCODE_DPAD_DOWN:
				direction = (mDirect == FROM_TOP_TO_BUTTOM) ? 1 : -1;
				handled = true;
				break;
			case KeyEvent.KEYCODE_DPAD_UP:
				direction = (mDirect == FROM_BUTTOM_TO_TOP) ? 1 : -1;
				handled = true;
				break;
			case KeyEvent.KEYCODE_DPAD_LEFT:
			case KeyEvent.KEYCODE_DPAD_RIGHT:
				handled = true;
				break;
			default:
				handled = false;
				break;
			}

			if (handled) {
				final int keyProgressIncrement = getKeyProgressIncrement();
				int progress = super.getProgress();

				progress += (direction * keyProgressIncrement);

				if (progress >= 0 && progress <= getMax()) {
					setProgress(progress - keyProgressIncrement, true);
				}

				return true;
			}
		}

		return super.onKeyDown(keyCode, event);
	}

	@Override
	public synchronized void setProgress(int progress) {
		super.setProgress(progress);
		refreshThumb();
	}
	
	public void setMinProgress(int progress){
		mMinProgress = progress;
		
	}

	private synchronized void setProgress(int progress, boolean fromUser) {
		if (mMethodSetProgress == null) {
			try {
				Method m;
				m = this.getClass().getMethod("setProgress", int.class,
						boolean.class);
				m.setAccessible(true);
				mMethodSetProgress = m;
			} catch (NoSuchMethodException e) {
			}
		}

		if (mMethodSetProgress != null) {
			try {
				System.out.println("begin invoke setProgress");
				mMethodSetProgress.invoke(this, progress, fromUser);
			} catch (IllegalArgumentException e) {
			} catch (IllegalAccessException e) {
			} catch (InvocationTargetException e) {
			}
		} else {
			super.setProgress(progress);
		}
		doRefreshProgress(android.R.id.progress, progress, fromUser, true);  //如果progressDrawable交给了父View处理，此方法就没必要了
		refreshThumb();
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);
//		updateDrawableBounds(w, h);
		updateThumbPos(w, h);
		
	}

	private void updateThumbPos(int w, int h) {
		Drawable d = getProgressDrawable();
		Drawable thumb = mThumb_;
		int thumbWidth = thumb == null ? 0 : thumb.getIntrinsicWidth();

		// int pw = d.getIntrinsicWidth();
		// if(mMaxWidth < pw)
		// mMaxWidth = pw;
		// The max height does not incorporate padding, whereas the height
		// parameter does
		int trackWidth = Math.min(mMaxWidth, w - getPaddingLeft()
				- getPaddingRight());

		int max = getMax();
		float scale = max > 0 ? (float) super.getProgress() / (float) (max - mMinProgress) : 0;
		if (thumbWidth > trackWidth) {
			if (thumb != null) {
				setThumbPos(h, thumb, scale, 0);
			}
			int gapForCenteringTrack = (thumbWidth - trackWidth) / 2;
			if (d != null) {
				// Canvas will be translated by the padding, so 0,0 is where we
				// start drawing
//				d.setBounds(gapForCenteringTrack, 0, w - getPaddingRight()
//						- getPaddingLeft() - gapForCenteringTrack, h
//						- getPaddingBottom() - getPaddingTop());
				d.setBounds(gapForCenteringTrack, 0, gapForCenteringTrack + trackWidth, h
						- getPaddingBottom() - getPaddingTop());
			}
		} else {
			if (d != null) {
				// Canvas will be translated by the padding, so 0,0 is where we
				// start drawing
				d.setBounds(0, 0, w - getPaddingRight() - getPaddingLeft(), h
						- getPaddingBottom() - getPaddingTop());
			}
			int gap = (trackWidth - thumbWidth) / 2;
			if (thumb != null) {
				setThumbPos(h, thumb, scale, gap);
			}
		}
	}

	private void setThumbPos(int h, Drawable thumb, float scale, int gap) {
		int available = h - getPaddingBottom() - getPaddingTop();
		int thumbWidth = thumb.getIntrinsicWidth();
		int thumbHeight = thumb.getIntrinsicHeight();
		available -= thumbHeight;

		// The extra space for the thumb to move on the track
		available += getThumbOffset() * 2;

		int thumbPos = (int) (scale * available);

		int leftBound, rightBound;
		if (gap == Integer.MIN_VALUE) {
			Rect oldBounds = thumb.getBounds();
			leftBound = oldBounds.left;
			rightBound = oldBounds.right;
		} else {
			leftBound = gap;
			rightBound = gap + thumbWidth;
		}

		// Canvas will be translated, so 0,0 is where we start drawing
		final int top = mDirect == FROM_TOP_TO_BUTTOM ? thumbPos : (available - thumbPos) ;

		thumb.setBounds(leftBound, top, rightBound, top + thumbHeight);
//		if(mTouchThumb != null){
//			int pl = getPaddingLeft();
//			int pr = getPaddingRight();
//			int height = mTouchThumb.getIntrinsicHeight();
//			int width = mTouchThumb.getIntrinsicWidth();
//			int fh = height * (rightBound - leftBound + pl + pr)/width;
//			mTouchThumb.setBounds(leftBound - pl, top + thumbHeight - fh, rightBound + pr, top + thumbHeight);
//		}
	}

	@Override
	protected synchronized void onDraw(Canvas canvas) {
		if (mProgressDrawable != null) {
//			canvas.drawColor(Color.BLACK);
			canvas.save();
			canvas.translate(getPaddingLeft(), getPaddingTop());
			mProgressDrawable.draw(canvas);
			canvas.restore();
			drawThumb(canvas);
			
		}
	}

	private void drawThumbText(Canvas canvas) {
		Rect r = mThumb_.getBounds();
		int x = r.left + r.width()/2;
		int y = r.top + r.height()/2;
		float m = (mPaint.descent() + mPaint.ascent())/2;
		canvas.drawText(getProgress()+"", x, y - m, mPaint);
	}

	private void drawThumb(Canvas canvas) {
		if(mThumb_ != null && (!mIsDragging || mListener == null)){
//		if (mThumb_ != null ) {
			canvas.save();
			canvas.translate(getPaddingLeft(), getPaddingTop()
					- getThumbOffset());
			mThumb_.draw(canvas);
			drawThumbText(canvas);
			canvas.restore();
		} else {
//			canvas.save();
//			canvas.translate(getPaddingLeft(), getPaddingTop()
//					- getThumbOffset());
//			mTouchThumb.draw(canvas);
//			drawThumbText(canvas);
//			canvas.restore();
		}
	}

	// refresh thumb position
	private void refreshThumb() {
		onSizeChanged(super.getWidth(), super.getHeight(), 0, 0);
	}
	
	public interface PositionListener {
		public void position(VerticalSeekBar seek,float x , float y,int p);
		public void onTouchStart(VerticalSeekBar seek,float x , float y,int p);
		public void onTouchStop(VerticalSeekBar seek);
	}
}
