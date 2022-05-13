package chirp.me.in.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Environment;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * for doing fft and making spectrogram, code modified from
 *  https://stackoverflow.com/questions/39295589/creating-spectrogram-from-wav-using-fft-in-java
 */
public class SoundProcessor {
    private byte[] entireFileData;

    private final Context context;

    private final boolean DEBUG = false;
    /**
     * fft window size
     */
    private final int windowSize = 1024;
    /**
     * fft overlap factor
     */
    private final int overlap = 8;
    /**
     * fft window step
     */
    private final int windowStep = windowSize / overlap;
    /**
     * scaling factor for cropping (SIGMA * latencyMS from start and end of recording)
     */
    private final double SIGMA = 1.4;

    /**
     * construct sound processor from file path
     * @param wavFilePath
     */
    public SoundProcessor(final String wavFilePath, final Context context)  {
        this.context = context;
        File file = new File(wavFilePath);
        FileInputStream fileInputStream;
        entireFileData = new byte[(int) file.length()];
        try
        {
            //Read bytes with InputStream
            fileInputStream = new FileInputStream(file);
            fileInputStream.read(entireFileData);
            fileInputStream.close();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        if (DEBUG){
            //extract format
            String format = new String(Arrays.copyOfRange(entireFileData, 8, 12), StandardCharsets.UTF_8);

            //extract number of channels
            int noOfChannels = entireFileData[22];
            String noOfChannels_str;
            if (noOfChannels == 2)
                noOfChannels_str = "2 (stereo)";
            else if (noOfChannels == 1)
                noOfChannels_str = "1 (mono)";
            else
                noOfChannels_str = noOfChannels + "(more than 2 channels)";

            //extract sampling rate (SR)
            int SR = (int) this.getSR();

            //extract Bit Per Second (BPS/Bit depth)
            int BPS = entireFileData[34];

            System.out.println("---------------------------------------------------");
            System.out.println("File path:          " + wavFilePath);
            System.out.println("File format:        " + format);
            System.out.println("Number of channels: " + noOfChannels_str);
            System.out.println("Sampling rate:      " + SR);
            System.out.println("Bit depth:          " + BPS);
            System.out.println("---------------------------------------------------");

        }
    }

    /**
     * Perform linear regression on data given latency and return object (which can be used to
     * retrieve slope and r^2, along with other data. See LinearRegression class for more details)
     * @param latencyMS the latency of the transmission to use in cropping
     * @return slope (Hz/s)
     */
    public LinearRegression getLinearRegression(final long latencyMS) {
        //double slope = 0.0;
        double[] rawData = getByteArray();
        double samplingRate = getSR();
        double time_resolution = windowSize / samplingRate;
        double frequency_resolution = samplingRate / windowSize;
        double highest_detectable_frequency = samplingRate / 2.0;
        double lowest_detectable_frequency = 5.0 * samplingRate / windowSize;

        Log.d("MY_SOUND_PROCESSING", "time_resolution:              " + time_resolution * 1000 + " ms");
        Log.d("MY_SOUND_PROCESSING", "frequency_resolution:         " + frequency_resolution + " Hz");
        Log.d("MY_SOUND_PROCESSING", "highest_detectable_frequency: " + highest_detectable_frequency + " Hz");
        Log.d("MY_SOUND_PROCESSING", "lowest_detectable_frequency:  " + lowest_detectable_frequency + " Hz");

        //initialize plotData array
        int length = rawData.length;
        int nX = (length - windowSize) / windowStep;
        int nY = windowSize / 2 + 1;
        double[][] plotData = new double[nX][nY];

        //apply FFT and find MAX and MIN amplitudes
        double maxAmp = Double.MIN_VALUE;
        double minAmp = Double.MAX_VALUE;
        double amp_square;
        double[] inputImag = new double[length];    // imaginary vector is all 0's
        for (int i = 0; i < nX; i++) {
            Arrays.fill(inputImag, 0.0);
            // compute FFT on time slice
            double[] WS_array = FFT.fft(Arrays.copyOfRange(rawData, i * windowStep, i * windowStep + windowSize), inputImag, true);
            // perform thresholding to obtain data ready for regression
            for (int j = 0; j < nY; j++){
                assert WS_array != null;
                amp_square = (WS_array[2*j]*WS_array[2*j]) + (WS_array[2*j+1]*WS_array[2*j+1]);
                double threshold = 1.0;
                plotData[i][nY-j-1] = Math.max(amp_square,threshold);

                //find MAX and MIN amplitude
                if (plotData[i][j] > maxAmp)
                    maxAmp = plotData[i][j];
                else if (plotData[i][j] < minAmp)
                    minAmp = plotData[i][j];

            }
        }

        // Normalize data by max/min amplitudes
        double diff = maxAmp - minAmp;
        for (int i = 0; i < nX; i++){
            for (int j = 0; j < nY; j++){
                plotData[i][j] = (plotData[i][j] - minAmp) / diff;
            }
        }

        // extract indices of max frequency from each time slice
        int[] maxFreqIndices = new int[plotData.length];
        double tempMax;
        for(int r = 0; r < plotData.length; r++) {
            tempMax = Double.MIN_VALUE;
            for(int c = 0; c < plotData[0].length; c++) {
                if(plotData[r][c] > tempMax) {
                    tempMax = plotData[r][c];
                    maxFreqIndices[r] = c;
                }
            }
        }

        // calculate max frequencies from max frequency indices
        double[] maxFreqs = new double[maxFreqIndices.length];
        for(int i = 0; i < maxFreqs.length; i++) {
            // have to subtract from 1 since we indexed in reverse when populating plotData
            maxFreqs[i] =  highest_detectable_frequency * (1.0 - ((double) maxFreqIndices[i] / (double) plotData.length));
        }

        // calculate time stamps associated with those frequencies
        double totalTimeS = (double) length / samplingRate;
        Log.d("MY_TIME", "total length is: " + totalTimeS);
        double[] timeStamps = new double[plotData.length] ;
        for(int i = 0; i < maxFreqs.length; i++) {
            timeStamps[i] = totalTimeS * ((double) i / maxFreqs.length);
        }

        // crop indices for frequencies and timestamps based on latency and SIGMA (scaling factor)
        double crop = latencyMS / (totalTimeS * 1000) * SIGMA;
        int startIndex = (int) (maxFreqIndices.length * crop);
        int endIndex = (int) ((maxFreqIndices.length - 1) * (1 - crop));
        double[] maxFreqsCropped = Arrays.copyOfRange(maxFreqs, startIndex, endIndex);
        double[] timeStampsCropped = Arrays.copyOfRange(timeStamps, startIndex, endIndex);

        // perform linear regression on cropped data to retrieve slope
        LinearRegression regression = new LinearRegression(timeStampsCropped, maxFreqsCropped);
//        slope = regression.slope();
//
//        // log r^2
//        double r2 = regression.R2();
//        Log.d("MY_REGRESSION", "Slope: " + slope + ", r^2 value: " + r2);

        // write image to file (not necessary for analysis)
        saveBitmapAsPNG(convertToBitmap(plotData), "spectrogram.png");

        return regression;
    }



    /**
     * save bitmap as png file with filename
     * @param bitmap to save
     * @param fileName filename including ".png" extension
     */
    public void saveBitmapAsPNG(final Bitmap bitmap, String fileName) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
        final byte[] bitmapData = stream.toByteArray();
        File spectrogramPNG = new File(Environment.getExternalStorageDirectory(), fileName);
        if (spectrogramPNG.exists()) {
            Log.d("MY_PHOTO", "Deleting spectrogram");
            spectrogramPNG.delete();
        }
        try {
            FileOutputStream fos = new FileOutputStream(spectrogramPNG);
            fos.write(bitmapData);
            fos.flush();
            fos.close();
            Log.d("MY_PHOTO", "Wrote file");
        }
        catch (java.io.IOException e) {
            Log.d("PictureDemo", "Exception in photoCallback", e);
        }
    }

    /**
     * convert 2d array of doubles to bitmap with ARGB color encoding (from blue to red)
     * @param imageData 2d array of doubles between 0 and 1
     */
    public Bitmap convertToBitmap(double[][] imageData) {
        Paint paint = new Paint();
        Bitmap bitmap = Bitmap.createBitmap(imageData.length, imageData[0].length, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        RGB rgb;
        for(int r = 0; r < imageData.length; r++) {
            for(int c = 0; c < imageData[0].length; c++) {
                rgb = RGB.getSpectrumRedBlue((float) imageData[r][c]);
                paint.setARGB(255, rgb.r, rgb.g, rgb.b);
                canvas.drawPoint(r, c, paint);
            }
        }
        return bitmap;
    }

    /**
     * get sampling rate of file
     * @return sampling rate as int
     */
    public double getSR(){
        ByteBuffer wrapped = ByteBuffer.wrap(Arrays.copyOfRange(entireFileData, 24, 28)); // big-endian by default
        double SR = wrapped.order(java.nio.ByteOrder.LITTLE_ENDIAN).getInt();
        return SR;
    }

    /**
     * get raw data byte array from wav file
     * @return byte array with raw data
     */
    public double[] getByteArray (){
        byte[] data_raw = Arrays.copyOfRange(entireFileData, 44, entireFileData.length);
        int totalLength = data_raw.length;

        //declare double array for mono
        int new_length = totalLength/4;
        double[] data_mono = new double[new_length];

        double left, right;
        for (int i = 0; 4*i+3 < totalLength; i++){
            left = (short)((data_raw[4*i+1] & 0xff) << 8) | (data_raw[4*i] & 0xff);
            right = (short)((data_raw[4*i+3] & 0xff) << 8) | (data_raw[4*i+2] & 0xff);
            data_mono[i] = (left+right)/2.0;
        }
        return data_mono;
    }

    /**
     * for doing RGB calculations for spectrogram gradient
     */
    private static class RGB {
        int r;
        int g;
        int b;

        public RGB(int r, int g, int b) {
            this.r = r;
            this.g = g;
            this.b = b;
        }

        /**
         * Get gradient color between red and blue (higher value means more red, lower value
         * means more blue)
         * @param val - value in [0,1] to determine how much of 1 and how much of 2 to mix
         * @return RGB mix of the two
         */
        private static RGB getSpectrumRedBlue(final float val) {
            return new RGB(
                    (int) (255 * val),
                    0,
                    (int) (255 * (1.0 - val))
            );
        }
    }
}
