package com.doumiao.documentscanner;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;
import androidx.exifinterface.media.ExifInterface;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQUEST_CAPTURE_IMAGE = 1001;
    private static final int REQUEST_CREATE_PDF = 1002;

    private static final int A4_WIDTH = 595;
    private static final int A4_HEIGHT = 842;
    private static final int PDF_MARGIN = 24;

    private static final String KEY_PAGES = "pages";
    private static final String KEY_PENDING_PATH = "pending_path";
    private static final String KEY_PENDING_URI = "pending_uri";
    private static final String KEY_LAST_PDF_URI = "last_pdf_uri";
    private static final String KEY_SMART_PROCESSING = "smart_processing";
    private static final String KEY_CONTINUOUS_CAPTURE = "continuous_capture";

    private final ArrayList<ScanPage> pages = new ArrayList<>();

    private LinearLayout pageList;
    private TextView statusView;
    private Button exportButton;
    private Button clearButton;
    private Button shareButton;
    private Button captureButton;
    private Button smartProcessingButton;
    private Button continuousCaptureButton;

    private File pendingImageFile;
    private Uri pendingImageUri;
    private Uri lastPdfUri;
    private boolean smartProcessingEnabled = true;
    private boolean continuousCaptureEnabled = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            restoreState(savedInstanceState);
        }

        buildUi();
        refreshPages();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        ArrayList<String> pagePaths = new ArrayList<>();
        for (ScanPage page : pages) {
            pagePaths.add(page.imageFile.getAbsolutePath());
        }
        outState.putStringArrayList(KEY_PAGES, pagePaths);

        if (pendingImageFile != null) {
            outState.putString(KEY_PENDING_PATH, pendingImageFile.getAbsolutePath());
        }
        if (pendingImageUri != null) {
            outState.putString(KEY_PENDING_URI, pendingImageUri.toString());
        }
        if (lastPdfUri != null) {
            outState.putString(KEY_LAST_PDF_URI, lastPdfUri.toString());
        }
        outState.putBoolean(KEY_SMART_PROCESSING, smartProcessingEnabled);
        outState.putBoolean(KEY_CONTINUOUS_CAPTURE, continuousCaptureEnabled);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CAPTURE_IMAGE) {
            handleCaptureResult(resultCode, data);
            return;
        }

        if (requestCode == REQUEST_CREATE_PDF) {
            handlePdfDocumentResult(resultCode, data);
        }
    }

    private void restoreState(Bundle state) {
        ArrayList<String> pagePaths = state.getStringArrayList(KEY_PAGES);
        if (pagePaths != null) {
            for (String path : pagePaths) {
                File imageFile = new File(path);
                if (imageFile.exists()) {
                    pages.add(new ScanPage(imageFile));
                }
            }
        }

        String pendingPath = state.getString(KEY_PENDING_PATH);
        if (pendingPath != null) {
            pendingImageFile = new File(pendingPath);
        }

        String pendingUriText = state.getString(KEY_PENDING_URI);
        if (pendingUriText != null) {
            pendingImageUri = Uri.parse(pendingUriText);
        }

        String pdfUriText = state.getString(KEY_LAST_PDF_URI);
        if (pdfUriText != null) {
            lastPdfUri = Uri.parse(pdfUriText);
        }

        smartProcessingEnabled = state.getBoolean(KEY_SMART_PROCESSING, true);
        continuousCaptureEnabled = state.getBoolean(KEY_CONTINUOUS_CAPTURE, false);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFF4F7F7);
        root.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        root.addView(createHeader());
        root.addView(createActionPanel());

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setClipToPadding(false);
        scrollView.setPadding(dp(14), dp(8), dp(14), dp(18));
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        pageList = new LinearLayout(this);
        pageList.setOrientation(LinearLayout.VERTICAL);
        pageList.setLayoutParams(new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        scrollView.addView(pageList);
        root.addView(scrollView);

        setContentView(root);
    }

    private View createHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setBackgroundColor(0xFF0F766E);
        header.setPadding(dp(20), dp(18), dp(20), dp(16));
        header.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView title = new TextView(this);
        title.setText("豆苗");
        title.setTextColor(Color.WHITE);
        title.setTextSize(24);
        title.setGravity(Gravity.START);
        title.setTypeface(null, android.graphics.Typeface.BOLD);

        TextView subtitle = new TextView(this);
        subtitle.setText("合同拍照转 PDF");
        subtitle.setTextColor(0xFFD8F5F2);
        subtitle.setTextSize(14);
        subtitle.setPadding(0, dp(4), 0, 0);

        header.addView(title);
        header.addView(subtitle);
        return header;
    }

    private View createActionPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14), dp(12), dp(14), dp(10));
        panel.setBackgroundColor(Color.WHITE);
        panel.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout primaryActions = new LinearLayout(this);
        primaryActions.setOrientation(LinearLayout.HORIZONTAL);
        primaryActions.setGravity(Gravity.CENTER);

        captureButton = createButton("拍照添加页", 0xFF0F766E, Color.WHITE);
        captureButton.setOnClickListener(view -> launchCamera());
        primaryActions.addView(captureButton, weightedButtonParams(true));

        exportButton = createButton("生成 PDF", 0xFF155E75, Color.WHITE);
        exportButton.setOnClickListener(view -> beginPdfCreation());
        primaryActions.addView(exportButton, weightedButtonParams(false));

        LinearLayout processingActions = new LinearLayout(this);
        processingActions.setOrientation(LinearLayout.HORIZONTAL);
        processingActions.setGravity(Gravity.CENTER);
        processingActions.setPadding(0, dp(8), 0, 0);

        smartProcessingButton = createButton("裁边增亮 开", 0xFFE7F4F1, 0xFF0B5D54);
        smartProcessingButton.setOnClickListener(view -> {
            smartProcessingEnabled = !smartProcessingEnabled;
            refreshPages();
            setStatus(smartProcessingEnabled ? "自动裁边和 AI 增亮已开启" : "智能处理已关闭");
        });
        processingActions.addView(smartProcessingButton, weightedButtonParams(true));

        continuousCaptureButton = createButton("连拍 关", 0xFFE7EEF0, 0xFF263B3D);
        continuousCaptureButton.setOnClickListener(view -> {
            continuousCaptureEnabled = !continuousCaptureEnabled;
            refreshPages();
            setStatus(continuousCaptureEnabled ? "连拍已开启，按返回结束" : "连拍已关闭");
        });
        processingActions.addView(continuousCaptureButton, weightedButtonParams(false));

        LinearLayout secondaryActions = new LinearLayout(this);
        secondaryActions.setOrientation(LinearLayout.HORIZONTAL);
        secondaryActions.setGravity(Gravity.CENTER);
        secondaryActions.setPadding(0, dp(8), 0, 0);

        clearButton = createButton("清空", 0xFFE7EEF0, 0xFF263B3D);
        clearButton.setOnClickListener(view -> confirmClearPages());
        secondaryActions.addView(clearButton, weightedButtonParams(true));

        shareButton = createButton("分享 PDF", 0xFFE7EEF0, 0xFF263B3D);
        shareButton.setOnClickListener(view -> shareLastPdf());
        secondaryActions.addView(shareButton, weightedButtonParams(false));

        statusView = new TextView(this);
        statusView.setTextColor(0xFF587173);
        statusView.setTextSize(13);
        statusView.setGravity(Gravity.CENTER_VERTICAL);
        statusView.setPadding(dp(2), dp(8), dp(2), 0);

        panel.addView(primaryActions);
        panel.addView(processingActions);
        panel.addView(secondaryActions);
        panel.addView(statusView);
        return panel;
    }

    private LinearLayout.LayoutParams weightedButtonParams(boolean addEndMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(46), 1f);
        if (addEndMargin) {
            params.setMargins(0, 0, dp(8), 0);
        }
        return params;
    }

    private Button createButton(String text, int backgroundColor, int textColor) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(15);
        button.setTextColor(textColor);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(44));
        button.setPadding(dp(8), 0, dp(8), 0);
        button.setBackground(roundedRect(backgroundColor, backgroundColor, dp(8), 0));
        return button;
    }

    private void refreshPages() {
        pageList.removeAllViews();

        if (pages.isEmpty()) {
            TextView emptyView = new TextView(this);
            emptyView.setText("暂无页面");
            emptyView.setTextSize(18);
            emptyView.setTextColor(0xFF607375);
            emptyView.setGravity(Gravity.CENTER);
            emptyView.setPadding(0, dp(96), 0, 0);
            pageList.addView(emptyView, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            setStatus("暂无页面");
        } else {
            for (int i = 0; i < pages.size(); i++) {
                pageList.addView(createPageView(i));
            }
            setStatus(pages.size() + " 页待生成");
        }

        exportButton.setEnabled(!pages.isEmpty());
        clearButton.setEnabled(!pages.isEmpty());
        shareButton.setVisibility(lastPdfUri == null ? View.GONE : View.VISIBLE);
        updateActionButtons();
    }

    private void updateActionButtons() {
        if (captureButton != null) {
            captureButton.setText(continuousCaptureEnabled ? "开始连拍" : "拍照添加页");
        }
        if (smartProcessingButton != null) {
            smartProcessingButton.setText(smartProcessingEnabled ? "裁边增亮 开" : "裁边增亮 关");
            smartProcessingButton.setTextColor(smartProcessingEnabled ? 0xFF0B5D54 : 0xFF263B3D);
            smartProcessingButton.setBackground(roundedRect(
                    smartProcessingEnabled ? 0xFFE7F4F1 : 0xFFE7EEF0,
                    smartProcessingEnabled ? 0xFFE7F4F1 : 0xFFE7EEF0,
                    dp(8),
                    0
            ));
        }
        if (continuousCaptureButton != null) {
            continuousCaptureButton.setText(continuousCaptureEnabled ? "连拍 开" : "连拍 关");
            continuousCaptureButton.setTextColor(continuousCaptureEnabled ? Color.WHITE : 0xFF263B3D);
            continuousCaptureButton.setBackground(roundedRect(
                    continuousCaptureEnabled ? 0xFF0F766E : 0xFFE7EEF0,
                    continuousCaptureEnabled ? 0xFF0F766E : 0xFFE7EEF0,
                    dp(8),
                    0
            ));
        }
    }

    private View createPageView(int index) {
        ScanPage page = pages.get(index);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(12), dp(12), dp(12), dp(12));
        container.setBackground(roundedRect(Color.WHITE, 0xFFD9E3E5, dp(8), dp(1)));

        LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        containerParams.setMargins(0, 0, 0, dp(12));
        container.setLayoutParams(containerParams);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("第 " + (index + 1) + " 页");
        title.setTextSize(16);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setTextColor(0xFF10201F);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button moveUp = createSmallButton("上移");
        moveUp.setEnabled(index > 0);
        moveUp.setOnClickListener(view -> {
            Collections.swap(pages, index, index - 1);
            refreshPages();
        });
        header.addView(moveUp);

        Button moveDown = createSmallButton("下移");
        moveDown.setEnabled(index < pages.size() - 1);
        moveDown.setOnClickListener(view -> {
            Collections.swap(pages, index, index + 1);
            refreshPages();
        });
        header.addView(moveDown);

        Button delete = createSmallButton("删除");
        delete.setTextColor(0xFFB42318);
        delete.setOnClickListener(view -> {
            ScanPage removed = pages.remove(index);
            deleteQuietly(removed.imageFile);
            refreshPages();
        });
        header.addView(delete);

        ImageView imageView = new ImageView(this);
        imageView.setBackgroundColor(0xFFF1F5F6);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imageView.setAdjustViewBounds(false);
        imageView.setPadding(dp(6), dp(6), dp(6), dp(6));

        Bitmap thumbnail = loadBitmap(page.imageFile, 1200);
        if (thumbnail != null) {
            imageView.setImageBitmap(thumbnail);
        }

        LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(300)
        );
        imageParams.setMargins(0, dp(10), 0, 0);

        container.addView(header);
        container.addView(imageView, imageParams);
        return container;
    }

    private Button createSmallButton(String text) {
        Button button = createButton(text, 0xFFF2F6F7, 0xFF263B3D);
        button.setTextSize(13);
        button.setMinWidth(dp(54));
        button.setMinHeight(dp(36));
        button.setPadding(dp(6), 0, dp(6), 0);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(36)
        );
        params.setMargins(dp(6), 0, 0, 0);
        button.setLayoutParams(params);
        return button;
    }

    private void launchCamera() {
        try {
            pendingImageFile = createImageFile();
            pendingImageUri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    pendingImageFile
            );

            Intent captureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            captureIntent.putExtra(MediaStore.EXTRA_OUTPUT, pendingImageUri);
            captureIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

            if (captureIntent.resolveActivity(getPackageManager()) == null) {
                Toast.makeText(this, "未找到相机应用", Toast.LENGTH_SHORT).show();
                deleteQuietly(pendingImageFile);
                pendingImageFile = null;
                pendingImageUri = null;
                return;
            }

            grantCameraUriPermissions(captureIntent, pendingImageUri);
            startActivityForResult(captureIntent, REQUEST_CAPTURE_IMAGE);
        } catch (IOException exception) {
            Toast.makeText(this, "无法创建照片文件", Toast.LENGTH_LONG).show();
        } catch (ActivityNotFoundException exception) {
            Toast.makeText(this, "未找到相机应用", Toast.LENGTH_SHORT).show();
        }
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

    private void grantCameraUriPermissions(Intent intent, Uri imageUri) {
        List<ResolveInfo> activities = getPackageManager().queryIntentActivities(
                intent,
                PackageManager.MATCH_DEFAULT_ONLY
        );
        for (ResolveInfo activity : activities) {
            grantUriPermission(
                    activity.activityInfo.packageName,
                    imageUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            );
        }
    }

    private void handleCaptureResult(int resultCode, Intent data) {
        File capturedFile = pendingImageFile;
        Uri capturedUri = pendingImageUri;
        pendingImageFile = null;
        pendingImageUri = null;

        if (capturedUri != null) {
            revokeUriPermission(
                    capturedUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            );
        }

        if (resultCode != RESULT_OK || capturedFile == null) {
            deleteQuietly(capturedFile);
            if (continuousCaptureEnabled) {
                continuousCaptureEnabled = false;
                Toast.makeText(this, "连拍已结束", Toast.LENGTH_SHORT).show();
            }
            refreshPages();
            return;
        }

        if (capturedFile.length() == 0 && data != null) {
            saveFallbackThumbnail(data, capturedFile);
        }

        if (!capturedFile.exists() || capturedFile.length() == 0) {
            Toast.makeText(this, "照片未保存", Toast.LENGTH_SHORT).show();
            deleteQuietly(capturedFile);
            refreshPages();
            return;
        }

        boolean continueAfterThisPage = continuousCaptureEnabled;
        if (smartProcessingEnabled) {
            updateActionButtons();
            setStatus("正在自动裁边和 AI 增亮...");
            processCapturedPageAsync(capturedFile, continueAfterThisPage);
            return;
        }

        addCapturedPage(capturedFile, false, continueAfterThisPage);
    }

    private void saveFallbackThumbnail(Intent data, File outputFile) {
        Object bitmapObject = data.getExtras() == null ? null : data.getExtras().get("data");
        if (!(bitmapObject instanceof Bitmap)) {
            return;
        }

        try (FileOutputStream outputStream = new FileOutputStream(outputFile)) {
            ((Bitmap) bitmapObject).compress(Bitmap.CompressFormat.JPEG, 92, outputStream);
        } catch (IOException ignored) {
            deleteQuietly(outputFile);
        }
    }

    private void processCapturedPageAsync(File capturedFile, boolean continueAfterThisPage) {
        Thread processingThread = new Thread(() -> {
            File pageFile = capturedFile;
            boolean processed = false;

            try {
                pageFile = processCapturedImage(capturedFile);
                processed = !pageFile.equals(capturedFile);
            } catch (IOException | RuntimeException ignored) {
                pageFile = capturedFile;
            }

            File finalPageFile = pageFile;
            boolean finalProcessed = processed;
            runOnUiThread(() -> addCapturedPage(finalPageFile, finalProcessed, continueAfterThisPage));
        }, "doumiao-page-processing");
        processingThread.start();
    }

    private void addCapturedPage(File pageFile, boolean processed, boolean continueAfterThisPage) {
        pages.add(new ScanPage(pageFile));
        refreshPages();
        setStatus(processed ? pages.size() + " 页已智能处理" : pages.size() + " 页待生成");

        if (continueAfterThisPage && continuousCaptureEnabled) {
            Toast.makeText(this, "已添加第 " + pages.size() + " 页，继续拍摄", Toast.LENGTH_SHORT).show();
            pageList.postDelayed(this::launchCamera, 450);
        }
    }

    private File processCapturedImage(File originalFile) throws IOException {
        Bitmap current = loadBitmap(originalFile, 2600);
        if (current == null) {
            throw new IOException("Unable to decode captured image");
        }

        try {
            Bitmap cropped = autoCropDocument(current);
            if (cropped != current) {
                current.recycle();
                current = cropped;
            }

            Bitmap enhanced = enhanceDocument(current);
            if (enhanced != current) {
                current.recycle();
                current = enhanced;
            }

            File outputFile = createProcessedImageFile();
            saveBitmap(current, outputFile);
            deleteQuietly(originalFile);
            return outputFile;
        } finally {
            if (current != null && !current.isRecycled()) {
                current.recycle();
            }
        }
    }

    private File createProcessedImageFile() throws IOException {
        File baseDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (baseDir == null) {
            baseDir = getFilesDir();
        }

        File scansDir = new File(baseDir, "processed");
        if (!scansDir.exists() && !scansDir.mkdirs()) {
            throw new IOException("Unable to create processed image directory");
        }

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.CHINA).format(new Date());
        return new File(scansDir, "page_" + timestamp + ".jpg");
    }

    private void saveBitmap(Bitmap bitmap, File outputFile) throws IOException {
        try (FileOutputStream outputStream = new FileOutputStream(outputFile)) {
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 94, outputStream)) {
                throw new IOException("Unable to save processed image");
            }
        }
    }

    private Bitmap autoCropDocument(Bitmap source) {
        Rect bounds = findDocumentBounds(source);
        if (bounds == null) {
            return source;
        }
        return Bitmap.createBitmap(source, bounds.left, bounds.top, bounds.width(), bounds.height());
    }

    private Rect findDocumentBounds(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int step = Math.max(2, Math.min(width, height) / 520);
        int stableSamples = Math.max(3, Math.min(width, height) / 700);

        int left = findVerticalEdge(bitmap, 0, width, step, stableSamples);
        int right = findVerticalEdge(bitmap, width - 1, -1, -step, stableSamples);
        int top = findHorizontalEdge(bitmap, 0, height, step, stableSamples);
        int bottom = findHorizontalEdge(bitmap, height - 1, -1, -step, stableSamples);

        if (left < 0 || right < 0 || top < 0 || bottom < 0 || right <= left || bottom <= top) {
            return null;
        }

        int padding = Math.max(8, Math.min(width, height) / 60);
        left = Math.max(0, left - padding);
        top = Math.max(0, top - padding);
        right = Math.min(width, right + padding);
        bottom = Math.min(height, bottom + padding);

        int cropWidth = right - left;
        int cropHeight = bottom - top;
        if (cropWidth < width * 0.45f || cropHeight < height * 0.45f) {
            return null;
        }

        int margin = Math.max(12, Math.min(width, height) / 80);
        boolean hasUsefulCrop = left > margin || top > margin || width - right > margin || height - bottom > margin;
        if (!hasUsefulCrop) {
            return null;
        }

        return new Rect(left, top, right, bottom);
    }

    private int findVerticalEdge(Bitmap bitmap, int start, int stop, int step, int stableSamples) {
        int stableCount = 0;
        int firstStable = -1;

        for (int x = start; step > 0 ? x < stop : x > stop; x += step) {
            float score = paperScoreForColumn(bitmap, x, Math.abs(step));
            if (score >= 0.18f) {
                if (stableCount == 0) {
                    firstStable = x;
                }
                stableCount++;
                if (stableCount >= stableSamples) {
                    return firstStable;
                }
            } else {
                stableCount = 0;
                firstStable = -1;
            }
        }
        return -1;
    }

    private int findHorizontalEdge(Bitmap bitmap, int start, int stop, int step, int stableSamples) {
        int stableCount = 0;
        int firstStable = -1;

        for (int y = start; step > 0 ? y < stop : y > stop; y += step) {
            float score = paperScoreForRow(bitmap, y, Math.abs(step));
            if (score >= 0.18f) {
                if (stableCount == 0) {
                    firstStable = y;
                }
                stableCount++;
                if (stableCount >= stableSamples) {
                    return firstStable;
                }
            } else {
                stableCount = 0;
                firstStable = -1;
            }
        }
        return -1;
    }

    private float paperScoreForColumn(Bitmap bitmap, int x, int step) {
        int matches = 0;
        int samples = 0;
        for (int y = 0; y < bitmap.getHeight(); y += step) {
            if (isLikelyPaperPixel(bitmap.getPixel(x, y))) {
                matches++;
            }
            samples++;
        }
        return samples == 0 ? 0f : (float) matches / samples;
    }

    private float paperScoreForRow(Bitmap bitmap, int y, int step) {
        int matches = 0;
        int samples = 0;
        for (int x = 0; x < bitmap.getWidth(); x += step) {
            if (isLikelyPaperPixel(bitmap.getPixel(x, y))) {
                matches++;
            }
            samples++;
        }
        return samples == 0 ? 0f : (float) matches / samples;
    }

    private boolean isLikelyPaperPixel(int color) {
        int red = Color.red(color);
        int green = Color.green(color);
        int blue = Color.blue(color);
        int max = Math.max(red, Math.max(green, blue));
        int min = Math.min(red, Math.min(green, blue));
        int luma = luminance(red, green, blue);
        int saturation = max - min;
        return (luma > 148 && saturation < 92) || luma > 188;
    }

    private Bitmap enhanceDocument(Bitmap source) {
        int width = source.getWidth();
        int height = source.getHeight();
        int[] pixels = new int[width * height];
        source.getPixels(pixels, 0, width, 0, 0, width, height);

        int[] histogram = new int[256];
        int sampleStep = Math.max(1, Math.min(width, height) / 720);
        int sampleCount = 0;
        for (int y = 0; y < height; y += sampleStep) {
            for (int x = 0; x < width; x += sampleStep) {
                int color = pixels[y * width + x];
                histogram[luminance(Color.red(color), Color.green(color), Color.blue(color))]++;
                sampleCount++;
            }
        }

        int low = percentile(histogram, sampleCount, 0.04f);
        int high = percentile(histogram, sampleCount, 0.96f);
        if (high - low < 46) {
            low = Math.max(0, low - 28);
            high = Math.min(255, high + 48);
        }
        float scale = 255f / Math.max(1, high - low);

        for (int i = 0; i < pixels.length; i++) {
            int color = pixels[i];
            int alpha = Color.alpha(color);
            int red = level(Color.red(color), low, scale);
            int green = level(Color.green(color), low, scale);
            int blue = level(Color.blue(color), low, scale);
            int luma = luminance(red, green, blue);
            int max = Math.max(red, Math.max(green, blue));
            int min = Math.min(red, Math.min(green, blue));
            int saturation = max - min;

            red = clamp(Math.round(red * 1.06f + 12));
            green = clamp(Math.round(green * 1.06f + 12));
            blue = clamp(Math.round(blue * 1.06f + 12));

            if (luma > 148 && saturation < 96) {
                red = blend(red, 255, 0.38f);
                green = blend(green, 255, 0.38f);
                blue = blend(blue, 255, 0.38f);
            } else if (luma < 96) {
                red = clamp(Math.round(red * 0.74f));
                green = clamp(Math.round(green * 0.74f));
                blue = clamp(Math.round(blue * 0.74f));
            }

            pixels[i] = Color.argb(alpha, red, green, blue);
        }

        Bitmap enhanced = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        enhanced.setPixels(pixels, 0, width, 0, 0, width, height);
        return enhanced;
    }

    private int percentile(int[] histogram, int total, float percentile) {
        int target = Math.max(1, Math.round(total * percentile));
        int running = 0;
        for (int i = 0; i < histogram.length; i++) {
            running += histogram[i];
            if (running >= target) {
                return i;
            }
        }
        return 255;
    }

    private int level(int value, int low, float scale) {
        return clamp(Math.round((value - low) * scale));
    }

    private int blend(int value, int target, float amount) {
        return clamp(Math.round(value + (target - value) * amount));
    }

    private int luminance(int red, int green, int blue) {
        return (red * 299 + green * 587 + blue * 114) / 1000;
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private void beginPdfCreation() {
        if (pages.isEmpty()) {
            Toast.makeText(this, "暂无页面", Toast.LENGTH_SHORT).show();
            return;
        }

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmm", Locale.CHINA).format(new Date());
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/pdf");
        intent.putExtra(Intent.EXTRA_TITLE, "豆苗扫描_" + timestamp + ".pdf");

        try {
            startActivityForResult(intent, REQUEST_CREATE_PDF);
        } catch (ActivityNotFoundException exception) {
            Toast.makeText(this, "无法打开文件保存器", Toast.LENGTH_LONG).show();
        }
    }

    private void handlePdfDocumentResult(int resultCode, Intent data) {
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }

        Uri outputUri = data.getData();
        try {
            keepDocumentPermission(data, outputUri);
            writePdf(outputUri);
            lastPdfUri = outputUri;
            refreshPages();
            setStatus("PDF 已生成");
            Toast.makeText(this, "PDF 已生成", Toast.LENGTH_SHORT).show();
        } catch (IOException exception) {
            Toast.makeText(this, "生成 PDF 失败", Toast.LENGTH_LONG).show();
        }
    }

    private void keepDocumentPermission(Intent data, Uri uri) {
        int flags = data.getFlags() & (
                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        );
        if (flags == 0) {
            return;
        }

        try {
            getContentResolver().takePersistableUriPermission(uri, flags);
        } catch (SecurityException ignored) {
            // Some file providers grant only temporary access for ACTION_CREATE_DOCUMENT results.
        }
    }

    private void writePdf(Uri outputUri) throws IOException {
        PdfDocument pdfDocument = new PdfDocument();
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);

        try {
            for (int i = 0; i < pages.size(); i++) {
                Bitmap bitmap = loadBitmap(pages.get(i).imageFile, 2600);
                if (bitmap == null) {
                    throw new IOException("Unable to decode page " + (i + 1));
                }

                int pageWidth = bitmap.getWidth() > bitmap.getHeight() ? A4_HEIGHT : A4_WIDTH;
                int pageHeight = bitmap.getWidth() > bitmap.getHeight() ? A4_WIDTH : A4_HEIGHT;

                PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(
                        pageWidth,
                        pageHeight,
                        i + 1
                ).create();

                PdfDocument.Page page = pdfDocument.startPage(pageInfo);
                Canvas canvas = page.getCanvas();
                canvas.drawColor(Color.WHITE);
                canvas.drawBitmap(bitmap, null, fitInside(bitmap, pageWidth, pageHeight), paint);
                pdfDocument.finishPage(page);
                bitmap.recycle();
            }

            try (OutputStream outputStream = getContentResolver().openOutputStream(outputUri, "w")) {
                if (outputStream == null) {
                    throw new IOException("Unable to open PDF destination");
                }
                pdfDocument.writeTo(outputStream);
            }
        } finally {
            pdfDocument.close();
        }
    }

    private RectF fitInside(Bitmap bitmap, int pageWidth, int pageHeight) {
        float availableWidth = pageWidth - PDF_MARGIN * 2f;
        float availableHeight = pageHeight - PDF_MARGIN * 2f;
        float scale = Math.min(
                availableWidth / bitmap.getWidth(),
                availableHeight / bitmap.getHeight()
        );

        float targetWidth = bitmap.getWidth() * scale;
        float targetHeight = bitmap.getHeight() * scale;
        float left = (pageWidth - targetWidth) / 2f;
        float top = (pageHeight - targetHeight) / 2f;
        return new RectF(left, top, left + targetWidth, top + targetHeight);
    }

    private Bitmap loadBitmap(File imageFile, int maxDimension) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(imageFile.getAbsolutePath(), bounds);

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null;
        }

        int sampleSize = 1;
        while (bounds.outWidth / sampleSize > maxDimension || bounds.outHeight / sampleSize > maxDimension) {
            sampleSize *= 2;
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;

        Bitmap bitmap = BitmapFactory.decodeFile(imageFile.getAbsolutePath(), options);
        if (bitmap == null) {
            return null;
        }

        return rotateIfNeeded(imageFile, bitmap);
    }

    private Bitmap rotateIfNeeded(File imageFile, Bitmap bitmap) {
        int degrees = readImageRotation(imageFile);
        if (degrees == 0) {
            return bitmap;
        }

        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);

        Bitmap rotated = Bitmap.createBitmap(
                bitmap,
                0,
                0,
                bitmap.getWidth(),
                bitmap.getHeight(),
                matrix,
                true
        );
        bitmap.recycle();
        return rotated;
    }

    private int readImageRotation(File imageFile) {
        try {
            ExifInterface exifInterface = new ExifInterface(imageFile.getAbsolutePath());
            int orientation = exifInterface.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
            );

            if (orientation == ExifInterface.ORIENTATION_ROTATE_90) {
                return 90;
            }
            if (orientation == ExifInterface.ORIENTATION_ROTATE_180) {
                return 180;
            }
            if (orientation == ExifInterface.ORIENTATION_ROTATE_270) {
                return 270;
            }
        } catch (IOException ignored) {
            return 0;
        }
        return 0;
    }

    private void confirmClearPages() {
        if (pages.isEmpty()) {
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("清空页面")
                .setMessage("删除本次拍摄的所有页面？")
                .setPositiveButton("清空", (dialog, which) -> {
                    for (ScanPage page : pages) {
                        deleteQuietly(page.imageFile);
                    }
                    pages.clear();
                    refreshPages();
                    setStatus("已清空");
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void shareLastPdf() {
        if (lastPdfUri == null) {
            Toast.makeText(this, "暂无 PDF", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent sendIntent = new Intent(Intent.ACTION_SEND);
        sendIntent.setType("application/pdf");
        sendIntent.putExtra(Intent.EXTRA_STREAM, lastPdfUri);
        sendIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(sendIntent, "分享 PDF"));
    }

    private void deleteQuietly(File file) {
        if (file != null && file.exists()) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    private void setStatus(String status) {
        if (statusView != null) {
            statusView.setText(status);
        }
    }

    private GradientDrawable roundedRect(int fillColor, int strokeColor, int radius, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fillColor);
        drawable.setCornerRadius(radius);
        if (strokeWidth > 0) {
            drawable.setStroke(strokeWidth, strokeColor);
        }
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class ScanPage {
        private final File imageFile;

        private ScanPage(File imageFile) {
            this.imageFile = imageFile;
        }
    }
}
