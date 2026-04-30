package com.flipphoneguy.totp;

import android.graphics.Bitmap;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

public final class QrDecoder {
    private QrDecoder() {}

    public static String decode(Bitmap bmp) {
        if (bmp == null) return null;
        int w = bmp.getWidth();
        int h = bmp.getHeight();
        int[] pixels = new int[w * h];
        bmp.getPixels(pixels, 0, w, 0, 0, w, h);
        try {
            RGBLuminanceSource src = new RGBLuminanceSource(w, h, pixels);
            BinaryBitmap bb = new BinaryBitmap(new HybridBinarizer(src));
            Result r = new QRCodeReader().decode(bb);
            return r.getText();
        } catch (Exception e) {
            // Try inverted (white-on-dark)
            try {
                RGBLuminanceSource src = new RGBLuminanceSource(w, h, pixels);
                BinaryBitmap bb = new BinaryBitmap(new HybridBinarizer(src.invert()));
                Result r = new QRCodeReader().decode(bb);
                return r.getText();
            } catch (Exception e2) {
                return null;
            }
        }
    }
}
