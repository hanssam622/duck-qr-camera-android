package kr.school.duckqr;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Bundle;
import android.os.Vibrator;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.GlobalHistogramBinarizer;

import java.io.IOException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends Activity implements SurfaceHolder.Callback, Camera.PreviewCallback {
    private static final int CAMERA_PERMISSION_REQUEST = 1001;

    private SurfaceView previewView;
    private TextView messageView;
    private Button actionButton;
    private Camera camera;
    private MultiFormatReader qrReader;
    private boolean previewReady;
    private boolean decoding;
    private String lastResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();

        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        qrReader = new MultiFormatReader();
        qrReader.setHints(hints);

        if (hasCameraPermission()) {
            previewView.getHolder().addCallback(this);
        } else {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST);
            showMessage("카메라 권한을 허용하면 QR 코드를 스캔할 수 있어요.", false);
        }
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        previewView = new SurfaceView(this);
        root.addView(previewView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        TextView frame = new TextView(this);
        frame.setBackgroundColor(Color.TRANSPARENT);
        frame.setText("QR 코드를 화면 가운데에 맞춰 주세요");
        frame.setTextColor(Color.WHITE);
        frame.setTextSize(18);
        frame.setGravity(Gravity.CENTER);
        frame.setShadowLayer(8, 0, 2, Color.BLACK);
        FrameLayout.LayoutParams frameParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(80),
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        frameParams.topMargin = dp(24);
        root.addView(frame, frameParams);

        messageView = new TextView(this);
        messageView.setTextColor(Color.WHITE);
        messageView.setTextSize(16);
        messageView.setGravity(Gravity.CENTER);
        messageView.setPadding(dp(16), dp(12), dp(16), dp(12));
        messageView.setBackgroundColor(0xCC111111);

        FrameLayout panel = new FrameLayout(this);
        panel.setPadding(dp(12), dp(12), dp(12), dp(12));
        panel.addView(messageView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        actionButton = new Button(this);
        actionButton.setText("다시 스캔");
        actionButton.setVisibility(View.GONE);
        actionButton.setOnClickListener(v -> resumeScanning());
        FrameLayout.LayoutParams buttonParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_HORIZONTAL);
        buttonParams.topMargin = dp(72);
        panel.addView(actionButton, buttonParams);

        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        root.addView(panel, panelParams);
        setContentView(root);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        previewReady = true;
        startCamera();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        restartPreview();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        previewReady = false;
        stopCamera();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (hasCameraPermission() && previewReady) {
            startCamera();
        }
    }

    @Override
    protected void onPause() {
        stopCamera();
        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            previewView.getHolder().addCallback(this);
            startCamera();
        } else {
            showMessage("카메라 권한이 필요합니다. 설정에서 권한을 허용해 주세요.", false);
        }
    }

    private void startCamera() {
        if (camera != null || !previewReady || !hasCameraPermission()) {
            return;
        }
        try {
            camera = Camera.open();
            Camera.Parameters params = camera.getParameters();
            Camera.Size size = choosePreviewSize(params.getSupportedPreviewSizes());
            params.setPreviewSize(size.width, size.height);
            params.setPreviewFormat(ImageFormat.NV21);
            if (params.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            } else if (params.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
            }
            camera.setParameters(params);
            camera.setDisplayOrientation(displayOrientation());
            camera.setPreviewDisplay(previewView.getHolder());
            camera.setPreviewCallback(this);
            camera.startPreview();
            decoding = false;
            showMessage("QR 코드를 비추면 자동으로 링크를 열어요.", false);
        } catch (IOException | RuntimeException e) {
            showMessage("카메라를 시작할 수 없습니다.", false);
            stopCamera();
        }
    }

    private void restartPreview() {
        if (camera == null) {
            startCamera();
            return;
        }
        try {
            camera.stopPreview();
            camera.setPreviewDisplay(previewView.getHolder());
            camera.startPreview();
        } catch (IOException | RuntimeException ignored) {
            stopCamera();
            startCamera();
        }
    }

    private void stopCamera() {
        if (camera == null) {
            return;
        }
        camera.setPreviewCallback(null);
        camera.stopPreview();
        camera.release();
        camera = null;
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera source) {
        if (decoding || data == null) {
            return;
        }
        decoding = true;
        Camera.Size size = source.getParameters().getPreviewSize();
        try {
            PlanarYUVLuminanceSource luminance = new PlanarYUVLuminanceSource(
                    data, size.width, size.height, 0, 0, size.width, size.height, false);
            BinaryBitmap bitmap = new BinaryBitmap(new GlobalHistogramBinarizer(luminance));
            Result result = qrReader.decodeWithState(bitmap);
            handleResult(result.getText());
        } catch (NotFoundException ignored) {
            decoding = false;
        } catch (RuntimeException ignored) {
            decoding = false;
        } finally {
            qrReader.reset();
        }
    }

    private void handleResult(String text) {
        if (TextUtils.isEmpty(text) || text.equals(lastResult)) {
            decoding = false;
            return;
        }
        lastResult = text;
        vibrate();

        if (isUrl(text)) {
            showMessage("링크를 열고 있어요:\n" + text, true);
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(normalizeUrl(text)));
            try {
                startActivity(intent);
            } catch (RuntimeException e) {
                Toast.makeText(this, "링크를 열 수 없습니다.", Toast.LENGTH_SHORT).show();
            }
        } else {
            showMessage("QR 내용:\n" + text, true);
        }
    }

    private void resumeScanning() {
        lastResult = null;
        decoding = false;
        actionButton.setVisibility(View.GONE);
        showMessage("QR 코드를 비추면 자동으로 링크를 열어요.", false);
    }

    private void showMessage(String message, boolean resultVisible) {
        messageView.setText(message);
        actionButton.setVisibility(resultVisible ? View.VISIBLE : View.GONE);
    }

    private boolean hasCameraPermission() {
        return checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private Camera.Size choosePreviewSize(List<Camera.Size> sizes) {
        Camera.Size best = sizes.get(0);
        for (Camera.Size size : sizes) {
            int pixels = size.width * size.height;
            int bestPixels = best.width * best.height;
            if (pixels > bestPixels && pixels <= 1280 * 720) {
                best = size;
            }
        }
        return best;
    }

    private int displayOrientation() {
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(0, info);
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;
        if (rotation == Surface.ROTATION_90) degrees = 90;
        if (rotation == Surface.ROTATION_180) degrees = 180;
        if (rotation == Surface.ROTATION_270) degrees = 270;
        return (info.orientation - degrees + 360) % 360;
    }

    private boolean isUrl(String value) {
        String lower = value.toLowerCase();
        return lower.startsWith("http://") || lower.startsWith("https://")
                || lower.startsWith("www.");
    }

    private String normalizeUrl(String value) {
        if (value.toLowerCase().startsWith("www.")) {
            return "https://" + value;
        }
        return value;
    }

    private void vibrate() {
        Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vibrator != null) {
            vibrator.vibrate(120);
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
