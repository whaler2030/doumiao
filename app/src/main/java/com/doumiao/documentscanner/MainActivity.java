package com.doumiao.documentscanner;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
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
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.exifinterface.media.ExifInterface;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQUEST_CAPTURE_IMAGE = 1001;
    private static final int REQUEST_CREATE_PDF = 1002;

    private static final int A4_WIDTH = 595;
    private static final int A4_HEIGHT = 842;
    private static final int PDF_MARGIN = 24;

    private static final String KEY_PAGES = "pages";
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
            handleCameraSessionResult(resultCode, data);
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
            setStatus(continuousCaptureEnabled ? "连拍已开启，点击完成返回" : "连拍已关闭");
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
        Intent intent = new Intent(this, CameraActivity.class);
        intent.putExtra(CameraActivity.EXTRA_CONTINUOUS_CAPTURE, continuousCaptureEnabled);
        startActivityForResult(intent, REQUEST_CAPTURE_IMAGE);
    }

    private void handleCameraSessionResult(int resultCode, Intent data) {
        if (resultCode != RESULT_OK || data == null) {
            return;
        }

        ArrayList<String> capturedPaths = data.getStringArrayListExtra(CameraActivity.EXTRA_CAPTURED_PATHS);
        if (capturedPaths == null || capturedPaths.isEmpty()) {
            return;
        }

        ArrayList<File> capturedFiles = new ArrayList<>();
        for (String path : capturedPaths) {
            File imageFile = new File(path);
            if (imageFile.exists() && imageFile.length() > 0) {
                capturedFiles.add(imageFile);
            }
        }

        if (capturedFiles.isEmpty()) {
            Toast.makeText(this, "照片未保存", Toast.LENGTH_SHORT).show();
            return;
        }

        setStatus(smartProcessingEnabled
                ? "正在裁切和增强 " + capturedFiles.size() + " 页..."
                : "正在添加 " + capturedFiles.size() + " 页...");
        processCapturedPagesAsync(capturedFiles);
    }

    private void processCapturedPagesAsync(ArrayList<File> capturedFiles) {
        Thread processingThread = new Thread(() -> {
            ArrayList<ProcessedPage> processedPages = new ArrayList<>();

            for (File capturedFile : capturedFiles) {
                File pageFile = capturedFile;
                boolean processed = false;

                if (smartProcessingEnabled) {
                    try {
                        pageFile = processCapturedImage(capturedFile);
                        processed = !pageFile.equals(capturedFile);
                    } catch (IOException | RuntimeException ignored) {
                        pageFile = capturedFile;
                    }
                }

                processedPages.add(new ProcessedPage(pageFile, processed));
            }

            runOnUiThread(() -> addCapturedPages(processedPages));
        }, "doumiao-page-processing");
        processingThread.start();
    }

    private void addCapturedPages(ArrayList<ProcessedPage> processedPages) {
        int processedCount = 0;
        for (ProcessedPage processedPage : processedPages) {
            pages.add(new ScanPage(processedPage.imageFile));
            if (processedPage.processed) {
                processedCount++;
            }
        }

        refreshPages();
        setStatus(processedCount > 0
                ? "已智能处理 " + processedCount + " 页，共 " + pages.size() + " 页"
                : "已添加 " + processedPages.size() + " 页，共 " + pages.size() + " 页");
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
        Rect paperBounds = findPaperBounds(bitmap);
        if (paperBounds != null) {
            return paperBounds;
        }
        return findInkBounds(bitmap);
    }

    private Rect findPaperBounds(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int step = Math.max(2, Math.min(width, height) / 700);
        int columns = Math.max(1, (width + step - 1) / step);
        int rows = Math.max(1, (height + step - 1) / step);
        int[] columnHits = new int[columns];
        int[] rowHits = new int[rows];
        EdgeStats border = sampleBorderStats(bitmap, step);

        for (int y = 0, row = 0; y < height; y += step, row++) {
            for (int x = 0, column = 0; x < width; x += step, column++) {
                if (isLikelyPaperPixel(bitmap.getPixel(x, y), border)) {
                    columnHits[column]++;
                    rowHits[row]++;
                }
            }
        }

        float[] columnScores = toScores(columnHits, rows);
        float[] rowScores = toScores(rowHits, columns);
        int stable = Math.max(2, Math.min(columns, rows) / 80);
        float threshold = border.averageLuma > 185 ? 0.08f : 0.11f;

        int leftIndex = firstStableIndex(columnScores, threshold, stable);
        int rightIndex = lastStableIndex(columnScores, threshold, stable);
        int topIndex = firstStableIndex(rowScores, threshold, stable);
        int bottomIndex = lastStableIndex(rowScores, threshold, stable);

        if (leftIndex < 0 || rightIndex <= leftIndex || topIndex < 0 || bottomIndex <= topIndex) {
            return null;
        }

        Rect bounds = new Rect(
                leftIndex * step,
                topIndex * step,
                Math.min(width, (rightIndex + 1) * step),
                Math.min(height, (bottomIndex + 1) * step)
        );
        return validateAndPadCrop(bounds, width, height, 0.46f, 0.055f);
    }

    private Rect findInkBounds(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int step = Math.max(2, Math.min(width, height) / 780);
        int marginX = Math.max(4, width / 40);
        int marginY = Math.max(4, height / 40);
        int left = width;
        int right = 0;
        int top = height;
        int bottom = 0;
        int hits = 0;

        for (int y = marginY; y < height - marginY; y += step) {
            for (int x = marginX; x < width - marginX; x += step) {
                int color = bitmap.getPixel(x, y);
                int red = Color.red(color);
                int green = Color.green(color);
                int blue = Color.blue(color);
                int luma = luminance(red, green, blue);
                int saturation = saturation(red, green, blue);
                if (luma < 124 && saturation < 120) {
                    left = Math.min(left, x);
                    right = Math.max(right, x);
                    top = Math.min(top, y);
                    bottom = Math.max(bottom, y);
                    hits++;
                }
            }
        }

        if (hits < 12 || right <= left || bottom <= top) {
            return null;
        }

        Rect bounds = new Rect(left, top, right + step, bottom + step);
        return validateAndPadCrop(bounds, width, height, 0.36f, 0.14f);
    }

    private EdgeStats sampleBorderStats(Bitmap bitmap, int step) {
        long lumaTotal = 0;
        long saturationTotal = 0;
        int samples = 0;
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int border = Math.max(step * 3, Math.min(width, height) / 18);

        for (int y = 0; y < height; y += step) {
            for (int x = 0; x < width; x += step) {
                if (x > border && x < width - border && y > border && y < height - border) {
                    continue;
                }
                int color = bitmap.getPixel(x, y);
                int red = Color.red(color);
                int green = Color.green(color);
                int blue = Color.blue(color);
                lumaTotal += luminance(red, green, blue);
                saturationTotal += saturation(red, green, blue);
                samples++;
            }
        }

        if (samples == 0) {
            return new EdgeStats(170, 40);
        }
        return new EdgeStats((int) (lumaTotal / samples), (int) (saturationTotal / samples));
    }

    private boolean isLikelyPaperPixel(int color, EdgeStats border) {
        int red = Color.red(color);
        int green = Color.green(color);
        int blue = Color.blue(color);
        int luma = luminance(red, green, blue);
        int saturation = saturation(red, green, blue);

        if (luma > 218 && saturation < 130) {
            return true;
        }
        if (luma > 158 && saturation < 78) {
            return border.averageLuma < 150
                    || luma - border.averageLuma > 16
                    || border.averageSaturation - saturation > 22;
        }
        return luma - border.averageLuma > 36 && saturation < 112;
    }

    private float[] toScores(int[] hits, int divisor) {
        float[] scores = new float[hits.length];
        for (int i = 0; i < hits.length; i++) {
            scores[i] = divisor == 0 ? 0f : (float) hits[i] / divisor;
        }
        return smoothScores(scores);
    }

    private float[] smoothScores(float[] scores) {
        float[] smoothed = new float[scores.length];
        for (int i = 0; i < scores.length; i++) {
            float total = scores[i];
            int count = 1;
            if (i > 0) {
                total += scores[i - 1];
                count++;
            }
            if (i < scores.length - 1) {
                total += scores[i + 1];
                count++;
            }
            smoothed[i] = total / count;
        }
        return smoothed;
    }

    private int firstStableIndex(float[] scores, float threshold, int stableSamples) {
        int stableCount = 0;
        for (int i = 0; i < scores.length; i++) {
            if (scores[i] >= threshold) {
                stableCount++;
                if (stableCount >= stableSamples) {
                    return i - stableSamples + 1;
                }
            } else {
                stableCount = 0;
            }
        }
        return -1;
    }

    private int lastStableIndex(float[] scores, float threshold, int stableSamples) {
        int stableCount = 0;
        for (int i = scores.length - 1; i >= 0; i--) {
            if (scores[i] >= threshold) {
                stableCount++;
                if (stableCount >= stableSamples) {
                    return i + stableSamples - 1;
                }
            } else {
                stableCount = 0;
            }
        }
        return -1;
    }

    private Rect validateAndPadCrop(Rect bounds, int width, int height, float minCoverage, float paddingRatio) {
        int padding = Math.max(10, Math.round(Math.min(width, height) * paddingRatio));
        int left = Math.max(0, bounds.left - padding);
        int top = Math.max(0, bounds.top - padding);
        int right = Math.min(width, bounds.right + padding);
        int bottom = Math.min(height, bounds.bottom + padding);

        int cropWidth = right - left;
        int cropHeight = bottom - top;
        if (cropWidth < width * minCoverage || cropHeight < height * minCoverage) {
            return null;
        }

        int usefulMargin = Math.max(14, Math.min(width, height) / 95);
        boolean hasUsefulCrop = left > usefulMargin
                || top > usefulMargin
                || width - right > usefulMargin
                || height - bottom > usefulMargin;
        if (!hasUsefulCrop) {
            return null;
        }

        return new Rect(left, top, right, bottom);
    }

    private Bitmap enhanceDocument(Bitmap source) {
        int width = source.getWidth();
        int height = source.getHeight();
        int[] pixels = new int[width * height];
        source.getPixels(pixels, 0, width, 0, 0, width, height);

        ChannelBalance balance = measureChannelBalance(pixels, width, height);
        int[] lumaHistogram = new int[256];
        int sampleStep = Math.max(1, Math.min(width, height) / 720);
        int sampleCount = 0;
        for (int y = 0; y < height; y += sampleStep) {
            for (int x = 0; x < width; x += sampleStep) {
                int color = pixels[y * width + x];
                int red = clamp(Math.round(Color.red(color) * balance.redScale));
                int green = clamp(Math.round(Color.green(color) * balance.greenScale));
                int blue = clamp(Math.round(Color.blue(color) * balance.blueScale));
                lumaHistogram[luminance(red, green, blue)]++;
                sampleCount++;
            }
        }

        int low = percentile(lumaHistogram, sampleCount, 0.025f);
        int high = percentile(lumaHistogram, sampleCount, 0.92f);
        if (high - low < 72) {
            low = Math.max(0, low - 20);
            high = Math.min(255, low + 132);
        }
        float scale = 255f / Math.max(1, high - low);

        for (int i = 0; i < pixels.length; i++) {
            int color = pixels[i];
            int alpha = Color.alpha(color);
            int balancedRed = clamp(Math.round(Color.red(color) * balance.redScale));
            int balancedGreen = clamp(Math.round(Color.green(color) * balance.greenScale));
            int balancedBlue = clamp(Math.round(Color.blue(color) * balance.blueScale));
            int luma = luminance(balancedRed, balancedGreen, balancedBlue);
            int saturation = saturation(balancedRed, balancedGreen, balancedBlue);
            int mapped = level(luma, low, scale);
            int neutral = mapDocumentTone(mapped);

            int red = neutral;
            int green = neutral;
            int blue = neutral;
            if (saturation > 78 && luma > 62) {
                red = blend(neutral, level(balancedRed, low, scale), 0.58f);
                green = blend(neutral, level(balancedGreen, low, scale), 0.58f);
                blue = blend(neutral, level(balancedBlue, low, scale), 0.58f);
            }

            pixels[i] = Color.argb(alpha, red, green, blue);
        }

        Bitmap enhanced = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        enhanced.setPixels(pixels, 0, width, 0, 0, width, height);
        return enhanced;
    }

    private ChannelBalance measureChannelBalance(int[] pixels, int width, int height) {
        int[] redHistogram = new int[256];
        int[] greenHistogram = new int[256];
        int[] blueHistogram = new int[256];
        int sampleStep = Math.max(1, Math.min(width, height) / 680);
        int sampleCount = 0;

        for (int y = 0; y < height; y += sampleStep) {
            for (int x = 0; x < width; x += sampleStep) {
                int color = pixels[y * width + x];
                redHistogram[Color.red(color)]++;
                greenHistogram[Color.green(color)]++;
                blueHistogram[Color.blue(color)]++;
                sampleCount++;
            }
        }

        int redWhite = Math.max(1, percentile(redHistogram, sampleCount, 0.96f));
        int greenWhite = Math.max(1, percentile(greenHistogram, sampleCount, 0.96f));
        int blueWhite = Math.max(1, percentile(blueHistogram, sampleCount, 0.96f));
        float target = Math.min(242f, Math.max(redWhite, Math.max(greenWhite, blueWhite)));
        return new ChannelBalance(
                clampScale(target / redWhite),
                clampScale(target / greenWhite),
                clampScale(target / blueWhite)
        );
    }

    private int mapDocumentTone(int value) {
        float normalized = value / 255f;
        if (normalized > 0.62f) {
            return blend(value, 255, 0.78f);
        }
        if (normalized > 0.36f) {
            return clamp(value + 26);
        }
        return clamp(Math.round(value * 0.72f));
    }

    private float clampScale(float value) {
        return Math.max(0.72f, Math.min(1.48f, value));
    }

    private int saturation(int red, int green, int blue) {
        int max = Math.max(red, Math.max(green, blue));
        int min = Math.min(red, Math.min(green, blue));
        return max - min;
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

    private static final class EdgeStats {
        private final int averageLuma;
        private final int averageSaturation;

        private EdgeStats(int averageLuma, int averageSaturation) {
            this.averageLuma = averageLuma;
            this.averageSaturation = averageSaturation;
        }
    }

    private static final class ChannelBalance {
        private final float redScale;
        private final float greenScale;
        private final float blueScale;

        private ChannelBalance(float redScale, float greenScale, float blueScale) {
            this.redScale = redScale;
            this.greenScale = greenScale;
            this.blueScale = blueScale;
        }
    }

    private static final class ProcessedPage {
        private final File imageFile;
        private final boolean processed;

        private ProcessedPage(File imageFile, boolean processed) {
            this.imageFile = imageFile;
            this.processed = processed;
        }
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
