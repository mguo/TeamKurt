package edu.sfsu.cs.orange.ocr;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Iterator;
import java.util.Set;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.media.Image;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;
import android.text.ClipboardManager;
import android.text.SpannableStringBuilder;
import android.text.style.CharacterStyle;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.opencv.android.*;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener;
import org.opencv.core.*;
import org.opencv.highgui.Highgui;
import org.opencv.imgproc.Imgproc;
import org.opencv.android.Utils;
import org.opencv.objdetect.CascadeClassifier;

/**
 * Created by Zareen on 11/20/2015.
 */
public class FindContour {

    private Bitmap bmp;

    public FindContour(Bitmap bitmap) {
//        this.image_name = im_name;
        this.bmp = bitmap;
        Log.v("MyActivity", "In FindContour constructor");
        // findButtons();
    }

    public ArrayList<int[]> findText() {

        // -------------------- //
        // Pre-processing steps //
        // -------------------- //

        // Read image
        Log.v("MyActivity", "In findText method");
        if (!OpenCVLoader.initDebug()) {
            Log.v("MyActivity", "Loading OpenCV...");
        }

        // Initialize width and height
        int bmp_width = this.bmp.getWidth();
        int bmp_height = this.bmp.getHeight();

        // Initialize variables
        Mat image = new Mat();
        Utils.bitmapToMat(this.bmp, image);
        Mat imageGray = new Mat();
        Mat imageFiltered = new Mat();
        Mat imageEdged = new Mat();
        ArrayList<int[]> rectPoints = new ArrayList<int[]>();

        // Convert to gray scale
        Imgproc.cvtColor(image, imageGray, Imgproc.COLOR_BGR2GRAY);

        // Find edges in image
        Imgproc.bilateralFilter(imageGray,imageFiltered,11,17,17);
        Imgproc.Canny(imageFiltered, imageEdged, 30, 200);

        // Find contours
        ArrayList<MatOfPoint> contours = new ArrayList<MatOfPoint>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(imageEdged, contours, hierarchy, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);


        // ------------------------------ //
        // Pass 1: Filter by contour area //
        // ------------------------------ //
        Mat image1 = image.clone();
        ArrayList<Double> areas = new ArrayList<Double>();
        ArrayList<MatOfPoint> contours2 = new ArrayList<>();
        float areaThreshold = 20; // Eliminates contours intersecting with borders
        MatOfPoint contour = null;
        for (int i=0; i<contours.size(); i++) {
            contour = contours.get(i);
            Log.v("MyActivity", "Contour area: " + Imgproc.contourArea(contour));
            if (Imgproc.contourArea(contour) >= areaThreshold) {
                areas.add(Imgproc.contourArea(contour));
                contours2.add(contour);
            }
        }

        // Draw contours on image
        Bitmap pass1_bmp = null;
        Imgproc.drawContours(image1, contours2, -1, new Scalar(0, 255, 0), 2);
        pass1_bmp = Bitmap.createBitmap(image1.cols(), image1.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(image1, pass1_bmp);


        // ----------------------------- //
        // Pass 2: Find rectangle bounds //
        // ----------------------------- //
        Mat image2 = image.clone();
        ArrayList<org.opencv.core.Rect> rectangles = new ArrayList<>();
        for (int i=0; i<contours2.size(); i++) {
            contour = contours2.get(i);
            org.opencv.core.Rect rect = Imgproc.boundingRect(contour);
            rectangles.add(rect);
            Core.rectangle(image2, new Point(rect.x, rect.y), new Point(rect.x + rect.width, rect.y + rect.height), new Scalar(0, 0, 255));
        }
        // Draw rectangles on image
        Bitmap pass2_bmp = null;
        pass2_bmp = Bitmap.createBitmap(image2.cols(), image2.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(image2, pass2_bmp);


        // ------------------------------------------- //
        // Pass 3: Delete rectangles within each other //
        // ------------------------------------------- //
        org.opencv.core.Rect innerRect = null;
        org.opencv.core.Rect outerRect = null;

        // Remove duplicates in rectangles
        Set<org.opencv.core.Rect> rectSet = new LinkedHashSet<>(rectangles);
        rectangles.clear();
        rectangles.addAll(rectSet);
        ArrayList<org.opencv.core.Rect> rectangles2 = (ArrayList<org.opencv.core.Rect>) rectangles.clone();

        // Remove duplicates in contours
        Set<MatOfPoint> contourSet = new LinkedHashSet<>(contours2);
        contours2.clear();
        contours2.addAll(contourSet);
        ArrayList<MatOfPoint> contours3 = (ArrayList<MatOfPoint>)contours2.clone();

        for (int i=0; i<rectangles.size(); i++) {
            innerRect = rectangles.get(i);
            contour = contours2.get(i);
            for (int j=0; j<rectangles.size(); j++) {
                if (i != j)
                {
                    outerRect = rectangles.get(j);
                    if (innerRect.x >= outerRect.x && innerRect.y >= outerRect.y &&
                            innerRect.x+innerRect.width <= outerRect.x+outerRect.width &&
                            innerRect.y+innerRect.height <= outerRect.y+outerRect.height)
                    {
                        Log.v("MyActivity", "Removing inner rectangle: " + innerRect.x + ", " + innerRect.y + ", " + innerRect.width + ", " + innerRect.height);
                        Log.v("MyActivity", "From outer rectangle: " + outerRect.x + ", " + outerRect.y + ", " + outerRect.width + ", " + outerRect.height);
                        rectangles2.remove(innerRect);
                        contours3.remove(contour);
                        j = rectangles.size();
                    }
                }
            }
        }

        Log.v("MyActivity", "Outside pass 3");
        Mat image3 = image.clone();
//        double areaDiffThresh = 10;
        for (int i=0; i<rectangles2.size(); i++) {

//            // Check for boxes
//            org.opencv.core.Rect rect = rectangles2.get(i);
//            contour = contours3.get(i);
//            double contourArea = Imgproc.contourArea(contour);
//            double rectArea = rect.area();
//            double areaDiff = Math.abs(rectArea - contourArea);
//            Log.v("MyActivity", "Difference in areas: " + areaDiff);
//
//            MatOfPoint2f approxCurve = new MatOfPoint2f();
//            double perimeter = Imgproc.arcLength(contour, Boolean.TRUE);
//            Imgproc.approxPolyDP(new MatOfPoint2f(contour.toArray()), approxCurve, 0.02*perimeter, Boolean.TRUE);
//            Point[] approxPoints = approxCurve.toArray();
//
//            // If it is a box, add to output array and remove from further processing
////            if (areaDiff <= areaDiffThresh) {
//            if (approxPoints.length == 4) {
//                rectPoints.add(new int[]{rect.x, rect.y, rect.width, rect.height});
//                Core.rectangle(image4, new Point(rect.x, rect.y), new Point(rect.x + rect.width, rect.y + rect.height), new Scalar(0, 0, 255));
//                Log.v("MyActivity", "Added box to output array");
//                rectangles2.remove(rect);
//                contours3.remove(contour);
//            }
//
//            // Draw rectangle on image
//            else {
//                Core.rectangle(image3, new Point(rect.x, rect.y), new Point(rect.x + rect.width, rect.y + rect.height), new Scalar(0, 0, 255));
//            }

            org.opencv.core.Rect rect = rectangles2.get(i);
            Core.rectangle(image3, new Point(rect.x, rect.y), new Point(rect.x + rect.width, rect.y + rect.height), new Scalar(0, 0, 255));

        }
        Bitmap pass3_bmp = null;
        pass3_bmp = Bitmap.createBitmap(image3.cols(), image3.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(image3, pass3_bmp);



        // --------------------------------------- //
        // Pass 4: Group adjacent rectangle bounds //
        // --------------------------------------- //

        Mat image4 = image.clone();
        if (rectangles2.size() == 1)
        {
            org.opencv.core.Rect rect = rectangles2.get(0);
            double widthMargin = 0;
            double heightMargin = 0;

            // Left & right bounds
            int cropWidth = (int)((1 + 2*widthMargin)*rect.width);
            int cropX = (int)(rect.x - (widthMargin*rect.width));
            if (cropX < 0)
                cropX = 0;
            if (cropX + cropWidth > bmp_width)
                cropWidth = bmp_width - cropX;

            // Upper & lower bounds
            int cropY = (int)(rect.y - heightMargin*rect.height);
            int cropHeight = (int)((1 + 2*heightMargin)*rect.height);
            if (cropY < 0)
                cropY = 0;
            if (cropY + cropHeight > bmp_height)
                cropHeight = bmp_height - cropY;

            // Add to output array
            rectPoints.add(new int[]{rect.x, rect.y, rect.width, rect.height});
            Core.rectangle(image4, new Point(cropX, cropY), new Point(cropX + cropWidth, cropY + cropHeight), new Scalar(255,0,0));
        }

        if (rectangles2.size() > 1)
        {
            // Initialize reference rectangle and bounds
            org.opencv.core.Rect refRect = rectangles2.get(0);
            double checkMargin = 0.5;
            double widthMargin = 0;
            double heightMargin = 0;
            double upperBound = refRect.y - (checkMargin * refRect.height);
            double lowerBound = refRect.y + (1 + checkMargin)*refRect.height;
            ArrayList<org.opencv.core.Rect> textRectangles = new ArrayList<>();
            ArrayList<Integer> textRightBounds = new ArrayList<>();
            textRightBounds.add(refRect.x + refRect.width);
            ArrayList<Integer> textLeftBounds = new ArrayList<>();
            textLeftBounds.add(refRect.x);
            ArrayList<Integer> textUpperBounds = new ArrayList<>();
            textUpperBounds.add(refRect.y);
            ArrayList<Integer> textLowerBounds = new ArrayList<>();
            textLowerBounds.add(refRect.y + refRect.height);


            for (int i=1; i<rectangles2.size(); i++) {

                // Check if rectangle falls within bounds
                org.opencv.core.Rect checkRect = rectangles2.get(i);
                Boolean inBounds = (checkRect.y >= upperBound && checkRect.y + checkRect.height <= lowerBound);
                Boolean lastRect = (i == rectangles2.size()-1);

                // If true, add to considered grouping
                if (inBounds) {
                    textRectangles.add(checkRect);
                    textRightBounds.add((int)(checkRect.x + checkRect.width));
                    textLeftBounds.add((int)checkRect.x);
                    textUpperBounds.add((int)checkRect.y);
                    textLowerBounds.add((int)(checkRect.y + checkRect.height));
                    Log.v("MyActivity", "Added adjacent rectangle: " + checkRect.x + ", " + checkRect.y + ", " + checkRect.width + ", " + checkRect.height);
                }

                // Else, group into one text box
                if (!inBounds || lastRect) {
                    Log.v("MyActivity", "Connecting into one box");

                    // Left & right bounds
                    Collections.sort(textLeftBounds);
                    double leftBound = textLeftBounds.get(0);
                    Collections.sort(textRightBounds);
                    double rightBound = textRightBounds.get(textRightBounds.size()-1); // checkRect.x;
                    double width = rightBound - leftBound;
                    int cropWidth = (int)((1 + 2*widthMargin)*width);
                    int cropX = (int)(leftBound - (widthMargin*width));
                    if (cropX < 0)
                        cropX = 0;
                    if (cropX + cropWidth > bmp_width)
                        cropWidth = bmp_width - cropX;

                    // Upper & lower bounds
                    Collections.sort(textUpperBounds);
                    upperBound = textUpperBounds.get(0);
                    Collections.sort(textLowerBounds);
                    lowerBound = textLowerBounds.get(textLowerBounds.size()-1);
                    double height = lowerBound - upperBound;
                    int cropY = (int)(upperBound - heightMargin*height);
                    int cropHeight = (int)((1 + 2*heightMargin)*height);
                    if (cropY < 0)
                        cropY = 0;
                    if (cropY + cropHeight > bmp_height)
                        cropHeight = bmp_height - cropY;

                    // Add to output array and draw image
                    Core.rectangle(image4, new Point(cropX, cropY), new Point(cropX + cropWidth, cropY + cropHeight), new Scalar(255,0,0));
                    rectPoints.add(new int[]{cropX, cropY, cropWidth, cropHeight});

                    // Reset upper and lower bounds
                    refRect = checkRect;
                    textLeftBounds.clear();
                    textRightBounds.clear();
                    textUpperBounds.clear();
                    textLowerBounds.clear();
                    upperBound = checkRect.y - (checkMargin*checkRect.height);
                    lowerBound = checkRect.y + (1 + checkMargin)*checkRect.height;
                    textLeftBounds.add(checkRect.x);
                    textRightBounds.add(checkRect.x + checkRect.width);
                    textUpperBounds.add(checkRect.y);
                    textLowerBounds.add(checkRect.y + checkRect.height);
                }
            }
        }

        // Output as bitmap
        Log.v("MyActivity", "Outside pass 4");
        Bitmap pass4_bmp = null;
        pass4_bmp = Bitmap.createBitmap(image4.cols(), image4.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(image4, pass4_bmp);

        return rectPoints;
    }

}