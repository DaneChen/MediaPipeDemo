package com.danechen.mediapipedemo2.demo2;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import com.danechen.mediapipedemo2.R;


public class BmpProducer extends Thread {

    CustomFrameAvailableListner customFrameAvailableListner;

    public int height = 513,width = 513;
    Bitmap bmp;

    BmpProducer(Context context){
        bmp = BitmapFactory.decodeResource(context.getResources(), R.drawable.testface);
       // bmp = Bitmap.createScaledBitmap(bmp,480,640,true);
        height = bmp.getHeight();
        width = bmp.getWidth();
        start();
    }

    public void setCustomFrameAvailableListner(CustomFrameAvailableListner customFrameAvailableListner){
        this.customFrameAvailableListner = customFrameAvailableListner;
    }

    int count = 0;

    public static final String TAG="BmpProducer";
    @Override
    public void run() {
        super.run();
        while ((true)){
            if(bmp==null || customFrameAvailableListner == null)
                continue;
            Log.d(TAG,"Writing frame");
            customFrameAvailableListner.onFrame(bmp);
            count ++;
            /*OTMainActivity.imageView.post(new Runnable() {
                @Override
                public void run() {
                    OTMainActivity.imageView.setImageBitmap(bg);
                }
            });*/
            try{
                Thread.sleep(10);
            }catch (Exception e){
                Log.d(TAG,e.toString());
            }
        }
    }
}
