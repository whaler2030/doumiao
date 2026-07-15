package com.doumiao.documentscanner;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class CameraActivity extends ComponentActivity {
    public static final String EXTRA_CONTINUOUS_CAPTURE = "continuous_capture";
    public static final String EXTRA_CAPTURED_PATHS = "captured_paths";

    private static final int REQUEST_CAMERA_PERMISSION = 2001;

    private final ArrayList<String> capturedPaths = new ArrayList<>();

    private PreviewView previewView;
    private TextView statusView;
    private Button shutterButton;
    private Button doneButton;
    private ImageCapture imageCapture;
    private boolean continuousCapture;
    private boolean takingPicture;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        continuousCapture = getIntent().getBooleanExtra(EXTRA_CONTINUOUS_CAPTURE, false);
        buildUi();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.CAMERA},
                    REQUEST_CAMERA_PERMISSION
            );
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            Toast.makeText(this, "需要相机权限才能拍照", Toast.LENGTH_LONG).show();
            setResult(Activity.RESULT_CANCELED);
            finish();
        }
    }

    @Override
    public void onBackPressed() {
        finishWithCapturedPages();
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        previewView = new PreviewView(this);
        previewView.setScaleType(PreviewView.ScaleType.FILL_CENTER);
        root.addView(previewView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        View shade = new View(this);
        shade.setBackgroundColor(0x33000000);
        FrameLayout.LayoutParams shadeParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(96),
                Gravity.TOP
        );
        root.addView(shade, shadeParams);

        Button cancelButton = createOverlayButton("取消");
        cancelButton.setOnClickListener(view -> {
            if (capturedPaths.isEmpty()) {
                setResult(Activity.RESULT_CANCELED);
                finish();
            } else {
                finishWithCapturedPages();
            }
        });
        FrameLayout.LayoutParams cancelParams = new FrameLayout.LayoutParams(
                dp(72),
                dp(44),
                Gravity.TOP | Gravity.START
        );
        cancelParams.setMargins(dp(14), dp(22), 0, 0);
        root.addView(cancelButton, cancelParams);

        doneButton = createOverlayButton("完成");
        doneButton.setOnClickListener(view -> finishWithCapturedPages());
        FrameLayout.LayoutParams doneParams = new FrameLayout.LayoutParams(
                dp(72),
                dp(44),
                Gravity.TOP | Gravity.END
        );
        doneParams.setMargins(0, dp(22), dp(14), 0);
        root.addView(doneButton, doneParams);

        statusView = new TextView(this);
        statusView.setTextColor(Color.WHITE);
        statusView.setTextSize(15);
        statusView.setGravity(Gravity.CENTER);
        statusView.setText(continuousCapture ? "连拍 0 页" : "拍摄 1 页");
        FrameLayout.LayoutParams statusParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(44),
                Gravity.TOP | Gravity.CENTER_HORIZONTAL
        );
        statusParams.setMargins(0, dp(22), 0, 0);
        root.addView(statusView, statusParams);

        shutterButton = createShutterButton();
        shutterButton.setOnClickListener(view -> takePhoto());
        FrameLayout.LayoutParams shutterParams = new FrameLayout.LayoutParams(
                dp(86),
                dp(86),
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL
        );
        shutterParams.setMargins(0, 0, 0, dp(34));
        root.addView(shutterButton, shutterParams);

        setContentView(root);
        updateControls();
    }

    private Button createOverlayButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(15);
        button.setTextColor(Color.WHITE);
        button.setBackgroundColor(0x33000000);
        button.setPadding(0, 0, 0, 0);
        return button;
    }

    private Button createShutterButton() {
        Button button = new Button(this);
        button.setText("拍");
        button.setAllCaps(false);
        button.setTextSize(24);
        button.setTextColor(0xFF0B7A5B);
        button.setBackgroundColor(Color.WHITE);
        return button;
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> providerFuture = ProcessCameraProvider.getInstance(this);
        providerFuture.addListener(() -> {
            try {
                ProcessCameraProvider provider = providerFuture.get();
                bindCamera(provider);
            } catch (Exception exception) {
                Toast.makeText(this, "无法启动相机", Toast.LENGTH_LONG).show();
                setResult(Activity.RESULT_CANCELED);
                finish();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCamera(ProcessCameraProvider provider) {
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        imageCapture = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build();

        provider.unbindAll();
        provider.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture
        );
    }

    private void takePhoto() {
        if (imageCapture == null || takingPicture) {
            return;
        }

        File imageFile;
        try {
            imageFile = createImageFile();
        } catch (IOException exception) {
            Toast.makeText(this, "无法创建照片文件", Toast.LENGTH_SHORT).show();
            return;
        }

        View displayView = previewView;
        if (displayView.getDisplay() != null) {
            imageCapture.setTargetRotation(displayView.getDisplay().getRotation());
        }

        takingPicture = true;
        updateControls();

        ImageCapture.OutputFileOptions options = new ImageCapture.OutputFileOptions.Builder(imageFile).build();
        imageCapture.takePicture(
                options,
                ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(ImageCapture.OutputFileResults outputFileResults) {
                        capturedPaths.add(imageFile.getAbsolutePath());
                        takingPicture = false;
                        updateControls();

                        if (!continuousCapture) {
                            finishWithCapturedPages();
                        }
                    }

                    @Override
                    public void onError(ImageCaptureException exception) {
                        //noinspection ResultOfMethodCallIgnored
                        imageFile.delete();
                        takingPicture = false;
                        updateControls();
                        Toast.makeText(CameraActivity.this, "拍照失败", Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private File createImageFile() throws IOException {
        File baseDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (baseDir == null) {
            baseDir = getFilesDir();
        }

        File scansDir = new File(baseDir, "captures");
        if (!scansDir.exists() && !scansDir.mkdirs()) {
            throw new IOException("Unable to create capture directory");
        }

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.CHINA).format(new Date());
        return new File(scansDir, "scan_" + timestamp + ".jpg");
    }

    private void updateControls() {
        if (statusView != null) {
            if (takingPicture) {
                statusView.setText("正在保存...");
            } else if (continuousCapture) {
                statusView.setText("连拍 " + capturedPaths.size() + " 页");
            } else {
                statusView.setText("拍摄 1 页");
            }
        }

        if (shutterButton != null) {
            shutterButton.setEnabled(!takingPicture);
            shutterButton.setAlpha(takingPicture ? 0.55f : 1f);
        }

        if (doneButton != null) {
            doneButton.setVisibility(continuousCapture || !capturedPaths.isEmpty() ? View.VISIBLE : View.GONE);
            doneButton.setEnabled(!takingPicture);
        }
    }

    private void finishWithCapturedPages() {
        Intent result = new Intent();
        result.putStringArrayListExtra(EXTRA_CAPTURED_PATHS, capturedPaths);
        setResult(capturedPaths.isEmpty() ? Activity.RESULT_CANCELED : Activity.RESULT_OK, result);
        finish();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
