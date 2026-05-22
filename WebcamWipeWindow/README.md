# WebcamWipeWindow - ビルド＆実行ガイド

WebcamWipeWindow は、Webカメラ映像を円形または矩形で切り抜いて表示するJavaアプリケーションです。
このドキュメントでは、ソースコードからコンパイル、実行ファイル化までの手順を説明します。

---

## 📋 必要な環境

### システム要件
- **OS**: Windows 10 / 11（64bit）
- **Webカメラ**: 内蔵またはUSB接続

### 開発環境
- **Java Development Kit (JDK)**: Java 21 以上
- **ビルドツール**: Maven または Gradle（オプション）
- **IDE**: VS Code、IntelliJ IDEA など

---

## 📁 プロジェクト構成

```
WebcamWipeWindow/
├── WebcamWipeWindow.java         # メインソースコード
├── README.txt                     # アプリケーション説明書
├── README.md                      # このファイル
├── bin/                           # コンパイル済みクラスファイル（自動生成）
├── lib/                           # 依存ライブラリ
│   ├── webcam-capture-0.3.12.jar
│   ├── bridj-0.7.0.jar
│   ├── commons-lang3-3.14.0.jar
│   ├── slf4j-api-2.1.0-alpha1.jar
│   ├── slf4j-simple-2.1.0-alpha1.jar
│   └── opencv_java4110.dll        # opencvは重いため入れておりません。自分で探して入れてください！
├── manifest.txt                   # 実行ファイル用マニフェスト
└── app-image.bat                  # EXE生成用バッチファイル
```

---

## 🔧 セットアップ手順

### 1. JDKのインストール確認

PowerShellで以下を実行し、Javaがインストールされているか確認します：

```powershell
java -version
javac -version
```

**出力例:**
```
java version "21.0.x" 2023-xx-xx
```

インストールされていない場合は、[Oracle JDK](https://www.oracle.com/java/technologies/downloads/) または [Eclipse Adoptium](https://adoptium.net/) からダウンロードしてインストールしてください。

### 2. ワークディレクトリへの移動

PowerShellで以下を実行します：

```powershell
cd "c:\（フォルダのパス）\WebcamWipeWindow"
```

---

## 🔨 コンパイル方法

### **方法1: PowerShellスクリプト（推奨）**

以下のコマンドを実行します：

```powershell
# binディレクトリの作成（既に存在していればスキップ）
if (!(Test-Path bin)) { New-Item -ItemType Directory bin | Out-Null }

# コンパイル（Java 21用、lib配下すべてをクラスパスに含める）
javac --release 21 -d bin -cp "lib\*" WebcamWipeWindow.java

# コンパイル成功確認
if ($?) { Write-Host "✓ コンパイル成功" } else { Write-Host "✗ コンパイル失敗" }

# クラスファイルの確認
Get-ChildItem bin -Filter "*.class"
```

### **方法2: コマンドプロンプト**

```batch
REM binディレクトリ作成
if not exist bin mkdir bin

REM コンパイル
javac --release 21 -d bin -cp "lib\*" WebcamWipeWindow.java

REM 実行
java -cp "bin;lib\*" WebcamWipeWindow
```

### **コンパイルエラーの場合**

コンパイルに失敗した場合、エラーメッセージを確認してください：

- **`--release` オプションが認識されない**: JDKのバージョンを確認。古いJDKの場合は削除可能
- **クラスパスのエラー**: `lib` フォルダが同じディレクトリにあることを確認
- **文字列エラー**: ファイル名に特殊文字が含まれていないか確認

---

## ▶️ 実行方法

### **方法1: 直接実行（Java実行）**

```powershell
java -cp "bin;lib\*" WebcamWipeWindow
```

**実行前の確認:**
- Webカメラが正しく接続されている
- Zoom、Teams、OBS などでWebカメラが使用されていない

### **方法2: JARファイルとして実行**

JARファイルを作成してから実行することもできます：

```powershell
# JARファイルの生成
jar cvfe WebcamWipeWindow.jar WebcamWipeWindow -C bin .

# JARの実行
java -cp "WebcamWipeWindow.jar;lib\*" WebcamWipeWindow
```

---

## 📦 実行ファイル（EXE）の作成方法

### **方法1: jpackage（Java 16以上推奨）**

```powershell
# JARを作成
jar cvfe WebcamWipeWindow.jar WebcamWipeWindow -C bin .

# MSIインストーラ生成
jpackage `
  --input . `
  --name WebcamWipeWindow `
  --main-jar WebcamWipeWindow.jar `
  --main-class WebcamWipeWindow `
  --type msi `
  --app-version 1.0 `
  --win-menu `
  --win-menu-group "WebcamWipeWindow" `
  --win-shortcut
```

### **方法2: Launch4j + Maven（より高度）**

`pom.xml` を使用してMavenでビルドすることで、自動的にEXEが生成されます。

### **方法3: app-image.bat スクリプト（既存）**

プロジェクトの `app-image.bat` ファイルを実行します：

```powershell
.\app-image.bat
```

このスクリプトは自動的に：
1. コンパイル
2. JARファイル生成
3. モジュール化されたアプリケーションイメージ生成
4. EXEまたはMSIインストーラの作成

---

## 🚀 配布用パッケージの準備

実行ファイルを他のユーザーに配布する場合：

### 必要なファイル
```
WebcamWipeWindow/
├── WebcamWipeWindow.exe          # 実行ファイル
├── README.txt                     # アプリ説明書（重要）
└── [実行に必要なDLL等]
```

### パッケージ化の手順

1. **EXEが生成されたフォルダ全体をZIP化**
   ```powershell
   Compress-Archive -Path WebcamWipeWindow -DestinationPath WebcamWipeWindow.zip
   ```

2. **README.txt を必ず同梱**

3. **配布時の注意**
   - README.txt を除去しない
   - 必要なDLLが含まれているか確認
   - ウイルススキャンを実施

---

## ❓ トラブルシューティング

### コンパイルエラー

**エラー: `javac: command not found`**
- 原因: Javaがインストールされていないか、PATHが設定されていない
- 解決: JDKをインストールし、PATHに `JAVA_HOME\bin` を追加

**エラー: `package com.github.sarxos.webcam does not exist`**
- 原因: ライブラリパスが正しくない
- 解決: `-cp "lib\*"` が正しく指定されているか確認

### 実行時エラー

**エラー: `Exception in thread "main" java.lang.UnsatisfiedLinkError`**
- 原因: ネイティブライブラリ（DLL）が見つからない
- 解決: `lib` フォルダ内に `opencv_java4110.dll` があることを確認

**エラー: `Webカメラが見つかりません`**
- 原因: Webカメラが接続されていない、または別アプリで占有されている
- 解決: 
  - Webカメラを接続
  - Zoom、Teams などを終了してから実行

---

## 📝 クイックコマンド集

### Windows PowerShell での実行コマンド一覧

```powershell
# ディレクトリ移動
cd "c:\（フォルダのパス）\WebcamWipeWindow"

# コンパイル
if (!(Test-Path bin)) { New-Item -ItemType Directory bin | Out-Null }; javac --release 21 -d bin -cp "lib\*" WebcamWipeWindow.java

# 実行
java -cp "bin;lib\*" WebcamWipeWindow

# JAR作成
jar cvfe WebcamWipeWindow.jar WebcamWipeWindow -C bin .

# JAR実行
java -cp "WebcamWipeWindow.jar;lib\*" WebcamWipeWindow

# EXE生成（jpackage）
jpackage --input . --name WebcamWipeWindow --main-jar WebcamWipeWindow.jar --main-class WebcamWipeWindow --type msi --app-version 1.0
```

---

## 📚 依存ライブラリ

プロジェクトは以下のライブラリに依存しています：

| ライブラリ | バージョン | 用途 |
|-----------|----------|------|
| webcam-capture | 0.3.12 | Webカメラ映像取得 |
| commons-lang3 | 3.14.0 | ユーティリティ機能 |
| bridj | 0.7.0 | ネイティブ関数呼び出し |
| slf4j-api | 2.1.0-alpha1 | ログ出力API |
| slf4j-simple | 2.1.0-alpha1 | ログ出力実装 |
| OpenCV Java | 4.1.1 | 画像処理（DLL） |

---

## 💡 その他の情報

### プロジェクト情報
- **開発言語**: Java 21
- **ライセンス**: フリーソフトウェア
- **著作権**: © 2025 Fumiaki Masakiyo

### 関連ファイル
- [README.txt](./README.txt) - アプリケーション使用方法
- [manifest.txt](./manifest.txt) - JARマニフェスト設定
- [app-image.bat](./app-image.bat) - EXE生成バッチスクリプト

---

## 🔗 参考リンク

- [Oracle JDK ダウンロード](https://www.oracle.com/java/technologies/downloads/)
- [Eclipse Adoptium](https://adoptium.net/)
- [WebcamCapture ライブラリ](https://github.com/sarxos/webcam-capture)
- [OpenCV Java](https://opencv.org/releases/)

---

**最終更新**: 2025年11月28日
