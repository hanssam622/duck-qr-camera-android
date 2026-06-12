package kr.school.duckqr;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;
import com.journeyapps.barcodescanner.DefaultDecoderFactory;

import java.util.Collections;

public class MainActivity extends Activity {
    private static final int CAMERA_PERMISSION_REQUEST = 1001;

    private DecoratedBarcodeView scannerView;
    private TextView messageView;
    private Button openButton;
    private Button scanButton;
    private boolean awaitingChoice;
    private boolean launchedExternal;
    private String lastResult;

    private final BarcodeCallback callback = new BarcodeCallback() {
        @Override
        public void barcodeResult(BarcodeResult result) {
            if (awaitingChoice || result == null || TextUtils.isEmpty(result.getText())) {
                return;
            }
            handleResult(result.getText());
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        configureScanner();

        if (hasCameraPermission()) {
            scannerView.decodeContinuous(callback);
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST);
            }
            showIdleMessage("카메라 권한을 허용하면 QR 코드를 스캔할 수 있어요.");
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);

        scannerView = new DecoratedBarcodeView(this);
        scannerView.setStatusText("");
        root.addView(scannerView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setPadding(dp(12), dp(10), dp(12), dp(12));
        panel.setBackgroundColor(0xEE111111);

        messageView = new TextView(this);
        messageView.setTextColor(Color.WHITE);
        messageView.setTextSize(16);
        messageView.setGravity(Gravity.CENTER);
        panel.addView(messageView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout buttonRow = new LinearLayout(this);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setGravity(Gravity.CENTER);

        openButton = new Button(this);
        openButton.setText("링크 열기");
        openButton.setVisibility(View.GONE);
        openButton.setOnClickListener(v -> openLastResult());
        LinearLayout.LayoutParams openParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        openParams.rightMargin = dp(8);
        buttonRow.addView(openButton, openParams);

        scanButton = new Button(this);
        scanButton.setText("다시 스캔");
        scanButton.setVisibility(View.GONE);
        scanButton.setOnClickListener(v -> resumeScanning());
        LinearLayout.LayoutParams scanParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        scanParams.leftMargin = dp(8);
        buttonRow.addView(scanButton, scanParams);

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = dp(8);
        panel.addView(buttonRow, rowParams);
        root.addView(panel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);
        showIdleMessage("QR 코드를 비추면 내용을 확인한 뒤 링크를 열 수 있어요.");
    }

    private void configureScanner() {
        scannerView.getBarcodeView().setDecoderFactory(
                new DefaultDecoderFactory(Collections.singletonList(BarcodeFormat.QR_CODE)));
        scannerView.getBarcodeView().setCameraSettings(scannerView.getBarcodeView().getCameraSettings());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (launchedExternal) {
            launchedExternal = false;
            resumeScanning();
            return;
        }
        if (hasCameraPermission() && !awaitingChoice) {
            scannerView.resume();
        }
    }

    @Override
    protected void onPause() {
        scannerView.pause();
        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            scannerView.decodeContinuous(callback);
            scannerView.resume();
            showIdleMessage("QR 코드를 비추면 내용을 확인한 뒤 링크를 열 수 있어요.");
        } else {
            showIdleMessage("카메라 권한이 필요합니다. 설정에서 권한을 허용해 주세요.");
        }
    }

    private void handleResult(String text) {
        awaitingChoice = true;
        lastResult = text;
        scannerView.pause();
        vibrate();

        scanButton.setVisibility(View.VISIBLE);
        if (isUrl(text)) {
            openButton.setVisibility(View.VISIBLE);
            messageView.setText("링크를 열까요?\n" + text);
        } else {
            openButton.setVisibility(View.GONE);
            messageView.setText("QR 내용:\n" + text);
        }
    }

    private void resumeScanning() {
        awaitingChoice = false;
        lastResult = null;
        openButton.setVisibility(View.GONE);
        scanButton.setVisibility(View.GONE);
        showIdleMessage("QR 코드를 비추면 내용을 확인한 뒤 링크를 열 수 있어요.");
        if (hasCameraPermission()) {
            scannerView.resume();
        }
    }

    private void openLastResult() {
        if (TextUtils.isEmpty(lastResult) || !isUrl(lastResult)) {
            return;
        }
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(normalizeUrl(lastResult)));
        try {
            launchedExternal = true;
            startActivity(intent);
        } catch (RuntimeException e) {
            launchedExternal = false;
            Toast.makeText(this, "링크를 열 수 없습니다.", Toast.LENGTH_SHORT).show();
        }
    }

    private void showIdleMessage(String message) {
        messageView.setText(message);
        openButton.setVisibility(View.GONE);
        scanButton.setVisibility(View.GONE);
    }

    private boolean hasCameraPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
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
