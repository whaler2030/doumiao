# 合同扫描 PDF

一个原生 Android 应用示例，用手机拍照把合同、收据、纸质资料等整理成多页 PDF。

## 功能

- 调用系统相机拍摄页面
- 支持连续添加多页
- 支持页面上移、下移和删除
- 使用 Android 原生 `PdfDocument` 生成 PDF
- 通过系统文件创建器选择 PDF 保存位置
- 生成后可调用系统分享面板分享 PDF

## 打开项目

1. 安装 Android Studio。
2. 用 Android Studio 打开当前文件夹。
3. 等待 Gradle 同步完成。
4. 连接手机或启动模拟器，点击 Run。

## 通过 GitHub 在线生成 APK

仓库已经包含 GitHub Actions 配置：`.github/workflows/build-apk.yml`。

使用方式：

1. 把本项目推送到 GitHub 的 `main` 分支。
2. 打开 GitHub 仓库页面，进入 Actions。
3. 选择 `Build Android APK`。
4. 点击 `Run workflow`，或直接等待推送触发构建。
5. 构建成功后，在页面底部 Artifacts 下载 `contract-scanner-debug-apk`。

当前仓库没有提交 Gradle Wrapper。如果要用命令行构建，请在装好 JDK、Android SDK 和 Gradle 后运行：

```bash
gradle assembleDebug
```

## 主要文件

- `app/src/main/java/com/doumiao/documentscanner/MainActivity.java`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/res/xml/file_paths.xml`
