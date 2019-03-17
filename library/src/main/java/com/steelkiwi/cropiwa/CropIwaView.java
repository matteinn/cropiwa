package com.steelkiwi.cropiwa;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import com.steelkiwi.cropiwa.config.ConfigChangeListener;
import com.steelkiwi.cropiwa.config.CropIwaImageViewConfig;
import com.steelkiwi.cropiwa.config.CropIwaOverlayConfig;
import com.steelkiwi.cropiwa.config.CropIwaSaveConfig;
import com.steelkiwi.cropiwa.image.CropArea;
import com.steelkiwi.cropiwa.image.CropIwaBitmapManager;
import com.steelkiwi.cropiwa.image.CropIwaResultReceiver;
import com.steelkiwi.cropiwa.util.LoadBitmapCommand;
import com.steelkiwi.cropiwa.shape.CropIwaShapeMask;
import com.steelkiwi.cropiwa.util.CropIwaLog;

/**
 * Created by yarolegovich on 02.02.2017.
 */
public class CropIwaView extends FrameLayout {

    private CropIwaImageView imageView;
    private CropIwaOverlayView overlayView;

    private CropIwaOverlayConfig overlayConfig;
    private CropIwaImageViewConfig imageConfig;

    private CropIwaImageView.GestureProcessor gestureDetector;

    private Uri imageUri;
    private LoadBitmapCommand loadBitmapCommand;

    private ErrorListener errorListener;
    private CropSaveCompleteListener cropSaveCompleteListener;

    private CropIwaResultReceiver cropIwaResultReceiver;

    private ImageClickListener imageClickListener;

    // Used for click detection
    private int CLICK_ACTION_THRESHOLD = 6;
    private float startX;
    private float startY;
    private long startTime;

    public CropIwaView(Context context) {
        super(context);
        init(null);
    }

    public CropIwaView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    public CropIwaView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public CropIwaView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(attrs);
    }

    private void init(AttributeSet attrs) {
        imageConfig = CropIwaImageViewConfig.createFromAttributes(getContext(), attrs);
        initImageView();

        imageView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d("LOL", "LOL");
            }
        });

        overlayConfig = CropIwaOverlayConfig.createFromAttributes(getContext(), attrs);
        overlayConfig.addConfigChangeListener(new ReInitOverlayOnResizeModeChange());
        initOverlayView();

        cropIwaResultReceiver = new CropIwaResultReceiver();
        cropIwaResultReceiver.register(getContext());
        cropIwaResultReceiver.setListener(new CropResultRouter());
    }

    private void initImageView() {
        if (imageConfig == null) {
            throw new IllegalStateException("imageConfig must be initialized before calling this method");
        }
        imageView = new CropIwaImageView(getContext(), imageConfig);
        imageView.setBackgroundColor(Color.BLACK);
        gestureDetector = imageView.getImageTransformGestureDetector();
        addView(imageView);
    }

    private void initOverlayView() {
        if (imageView == null || overlayConfig == null) {
            throw new IllegalStateException("imageView and overlayConfig must be initialized before calling this method");
        }
        overlayView = overlayConfig.isDynamicCrop() ?
                new CropIwaDynamicOverlayView(getContext(), overlayConfig) :
                new CropIwaOverlayView(getContext(), overlayConfig);
        overlayView.setNewBoundsListener(imageView);
        imageView.setImagePositionedListener(overlayView);
        addView(overlayView);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (loadBitmapCommand != null) {
            loadBitmapCommand.setDimensions(w, h);
            loadBitmapCommand.tryExecute(getContext());
        }
    }

    @Override
    @SuppressWarnings("RedundantIfStatement")
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        //I think this "redundant" if statements improve code readability
        try {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            startX = ev.getX();
            startY = ev.getY();
            startTime = System.currentTimeMillis();
            gestureDetector.onDown(ev);
            return false;
        }
        if (ev.getAction() == MotionEvent.ACTION_UP) {
            float endX = ev.getX();
            float endY = ev.getY();
            long endTime = System.currentTimeMillis();
            if (imageClickListener != null && isAClick(startX, endX, startY, endY, startTime, endTime)) {
                imageClickListener.onImageClickListener();
            }
        }
        if (overlayView.isResizing() || overlayView.isDraggingCropArea()) {
            return false;
        }
        return true;
        } catch (IllegalArgumentException e) {
            //e.printStackTrace();
            return false;
        }
    }

    private boolean isAClick(float startX, float endX, float startY, float endY, long startTime, long endTime) {
        float differenceX = Math.abs(startX - endX);
        float differenceY = Math.abs(startY - endY);
        float differenceTime = endTime - startTime;
        return !(differenceX > CLICK_ACTION_THRESHOLD || differenceY > CLICK_ACTION_THRESHOLD || differenceTime > 200);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        try {
            gestureDetector.onTouchEvent(event);
            return super.onTouchEvent(event);
        } catch (IllegalArgumentException e) {
            //e.printStackTrace();
            return false;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        imageView.measure(widthMeasureSpec, heightMeasureSpec);
        overlayView.measure(
                imageView.getMeasuredWidthAndState(),
                imageView.getMeasuredHeightAndState());
        imageView.notifyImagePositioned();
        setMeasuredDimension(
                imageView.getMeasuredWidthAndState(),
                imageView.getMeasuredHeightAndState());
    }

    @Override
    public void invalidate() {
        imageView.invalidate();
        overlayView.invalidate();
    }

    public CropIwaOverlayConfig configureOverlay() {
        return overlayConfig;
    }

    public CropIwaImageViewConfig configureImage() {
        return imageConfig;
    }

    public void setImageUri(Uri uri) {
        imageUri = uri;
        loadBitmapCommand = new LoadBitmapCommand(
                uri, getWidth(), getHeight(),
                new BitmapLoadListener());
        loadBitmapCommand.tryExecute(getContext());
    }

    public void setImage(Bitmap bitmap) {
        imageView.setImageBitmap(bitmap);
        overlayView.setDrawOverlay(true);
    }

    public void crop(CropIwaSaveConfig saveConfig) {
        CropArea cropArea = CropArea.create(
                imageView.getImageRect(),
                imageView.getImageRect(),
                overlayView.getCropRect());
        CropIwaShapeMask mask = overlayConfig.getCropShape().getMask();
        CropIwaBitmapManager.get().crop(
                getContext(), cropArea, mask,
                imageUri, saveConfig);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (imageUri != null) {
            CropIwaBitmapManager loader = CropIwaBitmapManager.get();
            loader.unregisterLoadListenerFor(imageUri);
            loader.removeIfCached(imageUri);
        }
        if (cropIwaResultReceiver != null) {
            cropIwaResultReceiver.unregister(getContext());
        }
    }

    public void setErrorListener(ErrorListener errorListener) {
        this.errorListener = errorListener;
    }

    public void setCropSaveCompleteListener(CropSaveCompleteListener cropSaveCompleteListener) {
        this.cropSaveCompleteListener = cropSaveCompleteListener;
    }

    public void setImageClickListener(ImageClickListener imageClickListener) {
        this.imageClickListener = imageClickListener;
    }

    private class BitmapLoadListener implements CropIwaBitmapManager.BitmapLoadListener {

        @Override
        public void onBitmapLoaded(Uri imageUri, Bitmap bitmap) {
            setImage(bitmap);
        }

        @Override
        public void onLoadFailed(Throwable e) {
            CropIwaLog.e("CropIwa Image loading from [" + imageUri + "] failed", e);
            overlayView.setDrawOverlay(false);
            if (errorListener != null) {
                errorListener.onError(e);
            }
        }
    }

    private class CropResultRouter implements CropIwaResultReceiver.Listener {

        @Override
        public void onCropSuccess(Uri croppedUri) {
            if (cropSaveCompleteListener != null) {
                cropSaveCompleteListener.onCroppedRegionSaved(croppedUri);
            }
        }

        @Override
        public void onCropFailed(Throwable e) {
            if (errorListener != null) {
                errorListener.onError(e);
            }
        }
    }

    private class ReInitOverlayOnResizeModeChange implements ConfigChangeListener {

        @Override
        public void onConfigChanged() {
            if (shouldReInit()) {
                overlayConfig.removeConfigChangeListener(overlayView);
                boolean shouldDrawOverlay = overlayView.isDrawn();
                removeView(overlayView);

                initOverlayView();
                overlayView.setDrawOverlay(shouldDrawOverlay);

                invalidate();
            }
        }

        private boolean shouldReInit() {
            return overlayConfig.isDynamicCrop() != (overlayView instanceof CropIwaDynamicOverlayView);
        }
    }

    public interface CropSaveCompleteListener {
        void onCroppedRegionSaved(Uri bitmapUri);
    }

    public interface ImageClickListener {
        void onImageClickListener();
    }

    public interface ErrorListener {
        void onError(Throwable e);
    }
}
