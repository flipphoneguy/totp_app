package com.flipphoneguy.totp;

import android.graphics.Bitmap;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.GlobalHistogramBinarizer;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

public final class QrDecoder {
    private QrDecoder() {}

    public static String decode(Bitmap bmp) {
        if (bmp == null) return null;
        int w = bmp.getWidth();
        int h = bmp.getHeight();
        int[] pixels = new int[w * h];
        bmp.getPixels(pixels, 0, w, 0, 0, w, h);

        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, EnumSet.of(BarcodeFormat.QR_CODE));
        hints.put(DecodeHintType.CHARACTER_SET, "UTF-8");

        LuminanceSource base = new RGBLuminanceSource(w, h, pixels);
        LuminanceSource[] sources = { base, base.invert() };

        for (LuminanceSource src : sources) {
            String r = tryDecode(new BinaryBitmap(new HybridBinarizer(src)), hints);
            if (r != null) return r;
            r = tryDecode(new BinaryBitmap(new GlobalHistogramBinarizer(src)), hints);
            if (r != null) return r;
        }
        return null;
    }

    private static String tryDecode(BinaryBitmap bb, Map<DecodeHintType, Object> hints) {
        try {
            Result r = new QRCodeReader().decode(bb, hints);
            return r != null ? r.getText() : null;
        } catch (Exception ignored) {
            return null;
        }
    }
}
