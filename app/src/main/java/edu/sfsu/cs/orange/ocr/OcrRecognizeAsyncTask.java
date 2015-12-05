/*
 * Copyright 2011 Robert Theis
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
package edu.sfsu.cs.orange.ocr;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.googlecode.leptonica.android.ReadFile;
import com.googlecode.tesseract.android.ResultIterator;
import com.googlecode.tesseract.android.TessBaseAPI;
import com.googlecode.tesseract.android.TessBaseAPI.PageIteratorLevel;
/**
 * Class to send OCR requests to the OCR engine in a separate thread, send a success/failure message,
 * and dismiss the indeterminate progress dialog box. Used for non-continuous mode OCR only.
 */
final class OcrRecognizeAsyncTask extends AsyncTask<Void, Void, Boolean> {

  //  private static final boolean PERFORM_FISHER_THRESHOLDING = false; 
  //  private static final boolean PERFORM_OTSU_THRESHOLDING = false; 
  //  private static final boolean PERFORM_SOBEL_THRESHOLDING = false; 

  private CaptureActivity activity;
  private TessBaseAPI baseApi;
  private byte[] data;
  private int width;
  private int height;
  private OcrResult ocrResult;
  private long timeRequired;

  OcrRecognizeAsyncTask(CaptureActivity activity, TessBaseAPI baseApi, byte[] data, int width, int height) {
    this.activity = activity;
    this.baseApi = baseApi;
    this.data = data;
    this.width = width;
    this.height = height;
  }

  @Override
  protected Boolean doInBackground(Void... arg0) {
    long start = System.currentTimeMillis();
    Bitmap bitmap = activity.getCameraManager().buildLuminanceSource(data, width, height).renderCroppedGreyscaleBitmap();
    String textResult = null;
    // for use in findContours
    ArrayList<int[]> textBounds = null;
    int num_boxes = 0;
    int pageSegmentationMode;
    Bitmap resizedBitmap;

//    // First Try on the Entire image, if doesn't work then try findContours
//    String firstResult;
//    baseApi.setImage(ReadFile.readBitmap(bitmap));
//    int pageSegmentationMode = TessBaseAPI.PageSegMode.PSM_AUTO_OSD;
//    baseApi.setPageSegMode(pageSegmentationMode);
//    firstResult = baseApi.getUTF8Text();
//    if (textDetected(firstResult)){
//      if (stringValid(firstResult)){
//        textResult = firstResult;
//        System.out.println(textResult + "== overall auto picture");
//        Log.v("MyActivity", textResult + " from overall auto picture");
//      }
//    } else{ //change to single char then detect
//      pageSegmentationMode = TessBaseAPI.PageSegMode.PSM_SINGLE_CHAR;
//      baseApi.setPageSegMode(pageSegmentationMode);
//      String secondResult = baseApi.getUTF8Text();
//      if (stringValid(secondResult)) {
//        textResult = secondResult;
//        System.out.println(textResult + "== overall single char picture");
//        Log.v("MyActivity", textResult + " from over single char");
//      }
//    }
    if (!(textDetected(textResult) && stringValid(textResult))) { //find contours
      try {
        pageSegmentationMode = TessBaseAPI.PageSegMode.PSM_SINGLE_CHAR;
        baseApi.setPageSegMode(pageSegmentationMode);
        textBounds = new FindContour(bitmap).findText();
        num_boxes = textBounds.size();
        ArrayList<String> detectedStrings = new ArrayList<String>();

        int margin = 0;
        for(int i=0; i< num_boxes; i++){
          int[] dimensions = textBounds.get(i);
          int newX = dimensions[0];
          int newY = dimensions[1];
          int newWidth = dimensions[2];
          int newHeight = dimensions[3];
          Log.v("MyActivity", "Old Width" + String.valueOf(width));
          Log.v("MyActivity","Old Height" + String.valueOf(height));
          Log.v("MyActivity","New X" +  String.valueOf(dimensions[0]));
          Log.v("MyActivity","New Y" +  String.valueOf(dimensions[1]));
          Log.v("MyActivity","New Width" +  String.valueOf(dimensions[2]));
          Log.v("MyActivity","New Height" +  String.valueOf(dimensions[3]));
          resizedBitmap = Bitmap.createBitmap(bitmap, newX + margin , newY + margin , newWidth - 2*margin, newHeight - 2*margin);
          baseApi.setImage(ReadFile.readBitmap(resizedBitmap));
          String textResultPart = baseApi.getUTF8Text();
          if (stringValid(textResultPart) && textDetected(textResultPart)){
            detectedStrings.add(textResultPart);
          }

        }

        // Post processing of text detected
        //say the text that appears the most in the arraylist of detected text using findContours
        Set<String> unique = new HashSet<String>(detectedStrings);
        StringBuilder stringBuilder = new StringBuilder();
        int max_detection = 0;
        for (String key : unique) {
          int frequency_string = Collections.frequency(detectedStrings, key);
          System.out.println(key + ": " + frequency_string);
          if (frequency_string > max_detection){
            max_detection = frequency_string;
            stringBuilder.setLength(0); // reset stringBuilder
            stringBuilder.append(key);
            stringBuilder.append(" ");
          }
          else if (frequency_string == max_detection){
            stringBuilder.append(key);
            stringBuilder.append(" ");
          }
        }
        textResult = stringBuilder.toString();
        System.out.println(textResult + "== overall from findContours picture");
        Log.v("MyActivity", textResult + " from findContours");

      } catch (RuntimeException e) {
        Log.e("OcrRecognizeAsyncTaskC", "Caught RuntimeException in request to Tesseract. Setting state to CONTINUOUS_STOPPED.");
        Log.v("OcrRecognizeAsyncTaskC", String.valueOf(num_boxes));
        e.printStackTrace();
        try {
          baseApi.clear();
          activity.stopHandler();
        } catch (NullPointerException e1) {
          // Continue
        }
        return false;
      }
    } //end else using findContours

    // Set ocrResult with testResults
    try {
//      baseApi.setImage(ReadFile.readBitmap(bitmap));
//      textResult = baseApi.getUTF8Text();
      timeRequired = System.currentTimeMillis() - start;

      // Check for failure to recognize text
      if (!textDetected(textResult)) {
        textResult = "nothing";
      }
      ocrResult = new OcrResult();
      ocrResult.setWordConfidences(baseApi.wordConfidences());
      ocrResult.setMeanConfidence( baseApi.meanConfidence());
      ocrResult.setRegionBoundingBoxes(baseApi.getRegions().getBoxRects());
      ocrResult.setTextlineBoundingBoxes(baseApi.getTextlines().getBoxRects());
      ocrResult.setWordBoundingBoxes(baseApi.getWords().getBoxRects());
      ocrResult.setStripBoundingBoxes(baseApi.getStrips().getBoxRects());

      // Iterate through the results.
      final ResultIterator iterator = baseApi.getResultIterator();
      int[] lastBoundingBox;
      ArrayList<Rect> charBoxes = new ArrayList<Rect>();
      iterator.begin();
      do {
        lastBoundingBox = iterator.getBoundingBox(PageIteratorLevel.RIL_SYMBOL);
        Rect lastRectBox = new Rect(lastBoundingBox[0], lastBoundingBox[1],
                lastBoundingBox[2], lastBoundingBox[3]);
        charBoxes.add(lastRectBox);
      } while (iterator.next(PageIteratorLevel.RIL_SYMBOL));
      iterator.delete();
      ocrResult.setCharacterBoundingBoxes(charBoxes);

    } catch (RuntimeException e) {
      Log.e("OcrRecognizeAsyncTask", "Caught RuntimeException in request to Tesseract. Setting state to CONTINUOUS_STOPPED.");
      e.printStackTrace();
      try {
        baseApi.clear();
        activity.stopHandler();
      } catch (NullPointerException e1) {
        // Continue
      }
      return false;
    }
    timeRequired = System.currentTimeMillis() - start;
    ocrResult.setBitmap(bitmap);
    ocrResult.setText(textResult);
    ocrResult.setRecognitionTimeRequired(timeRequired);
    return true;
  }

  private boolean textDetected(String textResult){
    // Check for failure to recognize text
    if (textResult == null || textResult.equals("")) {
      return false;
    } else {
      return true;
    }
  }

  //determine if string is less than 15 characters and doesn't have 3 or more consequtive characters in a row
  private boolean stringValid(String textResult){
    int max_length = 15;
    if (textResult.length() > max_length){
      return false;
    } else{
      Pattern pattern_consequtive_3 = Pattern.compile("(.)\\1\\1", Pattern.CASE_INSENSITIVE);
      Matcher match_consequtive = pattern_consequtive_3.matcher(textResult);
      if (match_consequtive.find()) {
        return false;
      } else {
        return true;
      }
    }
  }

  @Override
  protected void onPostExecute(Boolean result) {
    super.onPostExecute(result);

    Handler handler = activity.getHandler();
    if (handler != null) {
      // Send results for single-shot mode recognition.
      if (result) {
        Message message = Message.obtain(handler, R.id.ocr_decode_succeeded, ocrResult);
        message.sendToTarget();
      } else {
        Message message = Message.obtain(handler, R.id.ocr_decode_failed, ocrResult);
        message.sendToTarget();
      }
      activity.getProgressDialog().dismiss();
    }
    if (baseApi != null) {
      baseApi.clear();
    }
  }
}
