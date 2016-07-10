package eu.hgross.blaubotcam.video;

import java.io.ByteArrayOutputStream;

import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.util.Log;

/**
 * http://stackoverflow.com/questions/5272388/extract-black-and-white-image-from-android-cameras-nv21-format
 * 
 * @author Henning Gross {@literal (mail.to@henning-gross.de)}
 *
 */
public class ImageUtils {
	private static final String LOG_TAG = "ImageUtils";

	public static byte[] convertToGrayScaleJpeg(byte[] yuv_nv21_data, int previewSizeWidth, int previewSizeHeight, int quality) {
		ByteArrayOutputStream jpgOut = new ByteArrayOutputStream();
		int[] imagePixelsRGB = new int[yuv_nv21_data.length];
		applyGrayScale(imagePixelsRGB, yuv_nv21_data, previewSizeWidth, previewSizeHeight);
		Bitmap bmap = Bitmap.createBitmap(imagePixelsRGB, previewSizeWidth, previewSizeHeight, Bitmap.Config.ARGB_8888);
		if (bmap.compress(CompressFormat.JPEG, quality, jpgOut)) {
			return jpgOut.toByteArray();
		} else {
			Log.e(LOG_TAG, "Failed to compress preview image to grayscale jpeg.");
			return null;
		}
	}
	
	public static byte[] convertToJpeg(byte[] yuv_nv21_data, int previewSizeWidth, int previewSizeHeight, int quality) {
		ByteArrayOutputStream jpgOut = new ByteArrayOutputStream();
		int[] imagePixelsRGB = convertYUV420_NV21toRGB8888(yuv_nv21_data, previewSizeWidth, previewSizeHeight);
		Bitmap bmap = Bitmap.createBitmap(imagePixelsRGB, previewSizeWidth, previewSizeHeight, Bitmap.Config.ARGB_8888);
		imagePixelsRGB = null;
		if (bmap.compress(CompressFormat.JPEG, quality, jpgOut)) {
			return jpgOut.toByteArray();
		} else {
			Log.e(LOG_TAG, "Failed to compress preview image to jpeg.");
			return null;
		}
	}
	
	
	public static Bitmap bitmapFromYUV(int[] pixels, int width, int height) {
		Bitmap bm = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
		return bm;
	}
	
	public static Bitmap toGrayscale(Bitmap bmpOriginal)
    {        
        int width, height;
        height = bmpOriginal.getHeight();
        width = bmpOriginal.getWidth();    

        Bitmap bmpGrayscale = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
        Canvas c = new Canvas(bmpGrayscale);
        Paint paint = new Paint();
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0);
        ColorMatrixColorFilter f = new ColorMatrixColorFilter(cm);
        paint.setColorFilter(f);
        c.drawBitmap(bmpOriginal, 0, 0, paint);
        return bmpGrayscale;
    }
	
	/**
	 * Converts YUV420 NV21 to Y888 (RGB8888). The grayscale image still holds 3 bytes on the pixel.
	 * 
	 * @param pixels output array with the converted array o grayscale pixels
	 * @param data byte array on YUV420 NV21 format.
	 * @param width pixels width
	 * @param height pixels height
	 */
	public static void applyGrayScale(int [] pixels, byte [] data, int width, int height) {
	    int p;
	    int size = width*height;
	    for(int i = 0; i < size; i++) {
	        p = data[i] & 0xFF;
	        pixels[i] = 0xff000000 | p<<16 | p<<8 | p;
	    }
	}
	
	/**
	 * Converts YUV420 NV21 to RGB8888
	 * 
	 * @param data byte array on YUV420 NV21 format.
	 * @param width pixels width
	 * @param height pixels height
	 * @return a RGB8888 pixels int array. Where each int is a pixels ARGB. 
	 */
	public static int[] convertYUV420_NV21toRGB8888(byte [] data, int width, int height) {
	    int size = width*height;
	    int offset = size;
	    int[] pixels = new int[size];
	    int u, v, y1, y2, y3, y4;

	    // i percorre os Y and the final pixels
	    // k percorre os pixles U e V
	    for(int i=0, k=0; i < size; i+=2, k+=2) {
	        y1 = data[i  ]&0xff;
	        y2 = data[i+1]&0xff;
	        y3 = data[width+i  ]&0xff;
	        y4 = data[width+i+1]&0xff;

	        u = data[offset+k  ]&0xff;
	        v = data[offset+k+1]&0xff;
	        u = u-128;
	        v = v-128;

	        pixels[i  ] = convertYUVtoRGB(y1, u, v);
	        pixels[i+1] = convertYUVtoRGB(y2, u, v);
	        pixels[width+i  ] = convertYUVtoRGB(y3, u, v);
	        pixels[width+i+1] = convertYUVtoRGB(y4, u, v);

	        if (i!=0 && (i+2)%width==0)
	            i+=width;
	    }

	    return pixels;
	}

	private static int convertYUVtoRGB(int y, int u, int v) {
	    int r,g,b;

	    r = y + (int)1.402f*v;
	    g = y - (int)(0.344f*u +0.714f*v);
	    b = y + (int)1.772f*u;
	    r = r>255? 255 : r<0 ? 0 : r;
	    g = g>255? 255 : g<0 ? 0 : g;
	    b = b>255? 255 : b<0 ? 0 : b;
	    return 0xff000000 | (b<<16) | (g<<8) | r;
	}
}
