package edu.sfsu.cs.orange.ocr;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Iterator;

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
import org.opencv.objdetect.CascadeClassifier;

/**
 * Created by Zareen on 11/20/2015.
 */
public class FindContour {

    private String image_name;

    public FindContour(String im_name) {
        this.image_name = im_name;
        findText();
        findButtons();
    }

    public ArrayList<Mat> findText() {
        // Read image
        Mat imageGray = new Mat();
        Mat imageEdged = new Mat();
        Mat image = Highgui.imread(image_name, Highgui.CV_LOAD_IMAGE_GRAYSCALE);
        // Convert to gray scale
        Imgproc.cvtColor(image, imageGray, Imgproc.COLOR_BGR2GRAY);
        // Find edges in image
        Imgproc.bilateralFilter(imageGray,imageGray,11,17,17);
        Imgproc.Canny(imageGray,imageEdged,30,200);

        // Find contours
        ArrayList<MatOfPoint> contours = new ArrayList<MatOfPoint>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(imageEdged, contours, hierarchy, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);

        // Pass 1: Filter by contour area
        ArrayList<Double> areas = new ArrayList<Double>();
        float areaThreshold = 50;
        Iterator<MatOfPoint> each = contours.iterator();
        while (each.hasNext()) {
            MatOfPoint contour = each.next();
            if (Imgproc.contourArea(contour) >= areaThreshold) {
                areas.add(Imgproc.contourArea(contour));
            } else {
                contours.remove(contour);
            }
        }
        Imgproc.drawContours(image,contours,0,new Scalar(0,255,0),5);

        // Pass 2: Find rectangle bounds
        ArrayList<org.opencv.core.Rect> rectangles = new ArrayList<>();
        for (int i=0; i<contours.size(); i++) {
            MatOfPoint contour = contours.get(i);
            org.opencv.core.Rect rect = Imgproc.boundingRect(contour);
            rectangles.add(rect);
        }
        Imgproc.drawContours(image, contours, 0, new Scalar(0,0,255),5);

        // Pass 3: Find contiguous rectangle bounds
        ArrayList<Mat> croppedImages = new ArrayList<>();
        org.opencv.core.Rect refRect = rectangles.get(0);
        double heightMargin = 0.3;
        double upperBound = refRect.y + (heightMargin*refRect.height);
        double lowerBound = (refRect.y - refRect.height) - (heightMargin*refRect.height);
        ArrayList<org.opencv.core.Rect> textRectangles = new ArrayList<>();

        for (int i=0; i<rectangles.size(); i++) {
            org.opencv.core.Rect checkRect = rectangles.get(i);
            double checkRectTop = checkRect.y;
            double checkRectBottom = checkRect.y - checkRect.height;

            // Check if falls within bounds
            if (checkRectTop <= upperBound && checkRectBottom >= lowerBound) {
                textRectangles.add(checkRect);
            } else {
                // Crop image and add to array
                double leftBound = refRect.x;
                double rightBound = checkRect.x;
                double width = rightBound - leftBound;
                int cropX = (int)(leftBound - (0.3*width));
                int cropY = (int)upperBound;
                int cropWidth = (int)(1.6*width);
                int cropHeight = (int)(upperBound - lowerBound);
                org.opencv.core.Rect cropRect = new org.opencv.core.Rect(cropX,cropY,cropWidth,cropHeight);
                Mat croppedImage = new Mat(image,cropRect);
                croppedImages.add(croppedImage);

                // Reset upper and lower bounds
                refRect = checkRect;
                upperBound = checkRect.y + (heightMargin*checkRect.height);
                lowerBound = (checkRect.y - checkRect.height) - (heightMargin*checkRect.height);
            }
        }

        return croppedImages;
    }

    public void findButtons() {
        // Read image
        Mat imageGray = new Mat();
        Mat imageEdged = new Mat();
        Mat image = Highgui.imread(image_name, Highgui.CV_LOAD_IMAGE_GRAYSCALE);
        // Convert to gray scale
        Imgproc.cvtColor(image, imageGray, Imgproc.COLOR_BGR2GRAY);
        // Find edges in image
        Imgproc.bilateralFilter(imageGray,imageGray,11,17,17);
        Imgproc.Canny(imageGray,imageEdged,30,200);

        // Find contours
        ArrayList<MatOfPoint> contours = new ArrayList<MatOfPoint>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(imageEdged, contours, hierarchy, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);


    }

}
