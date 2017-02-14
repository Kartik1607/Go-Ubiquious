package com.example.android.sunshine;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.wearable.view.drawer.WearableActionDrawer;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Created by Kartik Sharma on 09/02/17.
 */
public class MyFace extends CanvasWatchFaceService {

    private static final Typeface BOLD_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    private static final long UPDATE_RATE = TimeUnit.SECONDS.toMillis(1);

    @Override
    public Engine onCreateEngine() {
        /* provide your watch face implementation */
        return new Engine();
    }

    /* implement service callback methods */
    private class Engine extends CanvasWatchFaceService.Engine
            implements GoogleApiClient.ConnectionCallbacks,
            GoogleApiClient.OnConnectionFailedListener, DataApi.DataListener{

        boolean mRegisteredReceiver;
        Calendar mCalendar;
        Date mDate;
        java.text.DateFormat timeFormat, dateFormat, timeFormatAmbient;
        Paint paint_time, paint_date, paint_temp, paint_weather, paint_blue, paint_white, paint_date_ambient;
        Bitmap currentBitmap;
        Resources resources;
        double mXOffset;
        boolean mLowBitAmbient;
        boolean mBurnInProtection;
        GoogleApiClient client;
        String high="", low="", description ="";

        final String WEATHER = "/WEATHER", TEMP_HIGH = "HIGH", TEMP_LOW = "LOW",
                ICON = "ICON", DESCRIPTION = "DESCRIPTION";


        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }


        final Handler mUpdateTimeHandler = new Handler() {
            @Override
            public void handleMessage(Message message) {
                switch (message.what) {
                    case 0:
                        invalidate();
                        if (shouldTimerBeRunning()) {
                            long timeMs = System.currentTimeMillis();
                            long delayMs = UPDATE_RATE - (timeMs % UPDATE_RATE);
                            mUpdateTimeHandler.sendEmptyMessageDelayed(0, delayMs);
                        }
                        break;
                }
            }
        };


        final BroadcastReceiver mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                initFormats();
                invalidate();
            }
        };

        private void initFormats() {
            timeFormatAmbient = new SimpleDateFormat("h:mm a", Locale.getDefault());
            timeFormatAmbient.setCalendar(mCalendar);
            timeFormat = new SimpleDateFormat("h:mm:ss a", Locale.getDefault());
            timeFormat.setCalendar(mCalendar);
            dateFormat = new SimpleDateFormat("MMMM d, yyyy", Locale.getDefault());
            dateFormat.setCalendar(mCalendar);
        }



        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);
            setWatchFaceStyle(new WatchFaceStyle.Builder(MyFace.this)
            .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
            .setShowSystemUiTime(false)
            .build());

            mCalendar = Calendar.getInstance();
            mDate = new Date();
            resources = getResources();

            paint_time = createTextPaint(resources.getColor(R.color.colorPrimary),NORMAL_TYPEFACE);
            paint_date_ambient = createTextPaint(Color.GRAY,NORMAL_TYPEFACE);
            paint_date = createTextPaint(Color.BLACK,NORMAL_TYPEFACE);
            paint_temp = createTextPaint(Color.WHITE, BOLD_TYPEFACE);
            paint_weather = createTextPaint(Color.WHITE, NORMAL_TYPEFACE);
            paint_white = createBackGroundPaint(Color.WHITE);
            paint_blue = createBackGroundPaint(resources.getColor(R.color.colorPrimary));

            currentBitmap = null;

            initFormats();

            client = new GoogleApiClient.Builder(MyFace.this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(Wearable.API)
                    .build();

            client.connect();

            updateTimer();

        }


        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            drawBackground(canvas,bounds);
            drawTimeandDate(canvas,bounds);
            drawImage(canvas, bounds);
            drawTemp(canvas, bounds);
            drawWeather(canvas, bounds);
        }



        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(0);
            super.onDestroy();
        }

        private void registerReceiver() {
            if (mRegisteredReceiver) {
                return;
            }
            mRegisteredReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            filter.addAction(Intent.ACTION_LOCALE_CHANGED);
            MyFace.this.registerReceiver(mReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredReceiver) {
                return;
            }
            mRegisteredReceiver = false;
            MyFace.this.unregisterReceiver(mReceiver);
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                client.connect();

                registerReceiver();
                mCalendar.setTimeZone(TimeZone.getDefault());
                initFormats();
            } else {
                unregisterReceiver();

                if (client != null &&client.isConnected()) {
                    Wearable.DataApi.removeListener(client, this);
                    client.disconnect();
                }
            }

            updateTimer();
        }

        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(0);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(0);
            }
        }

        private Paint createTextPaint(int defaultInteractiveColor, Typeface typeface) {
            Paint paint = new Paint();
            paint.setColor(defaultInteractiveColor);
            paint.setTypeface(typeface);
            paint.setAntiAlias(true);
            return paint;
        }

        private Paint createBackGroundPaint(int color){
            Paint paint = new Paint();
            paint.setColor(color);
            paint.setStyle(Paint.Style.FILL);
            return paint;
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            boolean isRound = insets.isRound();
            mXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
            float textSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);

            paint_date.setTextSize(resources.getDimension(R.dimen.digital_date_text_size));
            paint_date_ambient.setTextSize(resources.getDimension(R.dimen.digital_date_text_size));
            paint_time.setTextSize(textSize);
            paint_temp.setTextSize(resources.getDimension(R.dimen.digital_temp_size));
            paint_weather.setTextSize(resources.getDimension(R.dimen.digital_weather_size));

        }

        private void drawBackground(Canvas canvas, Rect bounds){
            canvas.drawColor(Color.BLACK);
            if(isInAmbientMode())
                return;
            canvas.drawRect(bounds.left, bounds.top, bounds.right, bounds.bottom / 10 * 7.0f, paint_blue);
            canvas.drawRect(bounds.left, bounds.bottom / 10 * 7.0f, bounds.right, bounds.bottom, paint_white);
        }

        private void drawTimeandDate(Canvas canvas, Rect bounds){
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);
            mDate.setTime(now);

            String timeString;
            if(isInAmbientMode()){
                timeString = timeFormatAmbient.format(mDate).toUpperCase();
            }else{
                timeString = timeFormat.format(mDate).toUpperCase();
            }
            String dateString = dateFormat.format(mDate);
            float temp;
            temp = paint_time.measureText(timeString);
            canvas.drawText(timeString,bounds.centerX() -  temp/2 ,bounds.bottom / 10 * 8.0f, paint_time);
            if(!isInAmbientMode()){
                temp = paint_date.measureText(dateString);
                canvas.drawText(dateFormat.format(mDate), bounds.centerX() - temp/2, bounds.bottom / 10*9.0f, paint_date);
            }else{
                temp = paint_date_ambient.measureText(dateString);
                canvas.drawText(dateFormat.format(mDate), bounds.centerX() - temp/2, bounds.bottom / 10*9.0f, paint_date_ambient);
            }

        }

        private void drawTemp(Canvas canvas, Rect bounds) {
            float temp = paint_temp.measureText(high + " / " + low);
            canvas.drawText(high + " / " + low,bounds.centerX() - temp/2, bounds.bottom/10 * 4.5f , paint_temp);
        }

        private void drawWeather(Canvas canvas, Rect bounds) {
            float temp = paint_weather.measureText(description);
            canvas.drawText(description,bounds.centerX() - temp/2, bounds.bottom / 10 * 6 , paint_weather);
        }


        private void drawImage(Canvas canvas, Rect bounds){
            if(isInAmbientMode() || currentBitmap == null)
                return;

            canvas.drawBitmap(currentBitmap,bounds.centerX() - currentBitmap.getWidth()/2 , bounds.bottom/ 10 * 0.5f ,null );
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
            mBurnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION,
                    false);
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {

            super.onAmbientModeChanged(inAmbientMode);

            if (mLowBitAmbient) {
                boolean antiAlias = !inAmbientMode;
                paint_time.setAntiAlias(antiAlias);
                paint_date.setAntiAlias(antiAlias);
                paint_date_ambient.setAntiAlias(antiAlias);
                paint_temp.setAntiAlias(antiAlias);
                paint_weather.setAntiAlias(antiAlias);
            }
            invalidate();
            updateTimer();
        }


        @Override
        public void onConnected(@Nullable Bundle bundle) {
            Wearable.DataApi.addListener(client, this);
        }

        @Override
        public void onConnectionSuspended(int i) {

        }

        @Override
        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

        }

        @Override
        public void onDataChanged(DataEventBuffer dataEventBuffer) {
            //Log.d("MY_APP","NEW FUCKING DATA");
             for (DataEvent event : dataEventBuffer) {
                if (event.getType() == DataEvent.TYPE_CHANGED) {
                    DataItem item = event.getDataItem();
                    if (item.getUri().getPath().compareTo(WEATHER) == 0) {
                        DataMap dataMap = DataMapItem.fromDataItem(item).getDataMap();
                        high = dataMap.getString(TEMP_HIGH);
                        low = dataMap.getString(TEMP_LOW);
                        description = dataMap.getString(DESCRIPTION);
                        new BitMapAsyncTask().execute(dataMap.getAsset(ICON));
                        //Log.d("MY_APP", high + " " + low + " " + description);
                    }
                }
            }
        }

        public Bitmap loadBitmapFromAsset(Asset asset) {
            if (asset== null) {
                return null;
            }

            InputStream assetInputStream = Wearable.DataApi.getFdForAsset(
                    client, asset).await().getInputStream();
            client.disconnect();

            if (assetInputStream == null) {
                return null;
            }

            Bitmap b = BitmapFactory.decodeStream(assetInputStream);
            return Bitmap.createScaledBitmap(b,50,50,false);
        }

        public class BitMapAsyncTask extends AsyncTask<Asset, Void, Void>{

            @Override
            protected Void doInBackground(Asset... params) {
                currentBitmap = loadBitmapFromAsset(params[0]);
                invalidate();
                return null;
            }
        }
    }


}