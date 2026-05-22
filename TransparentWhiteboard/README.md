# TransparentWhiteboard

`TransparentWhiteboard` は、デスクトップ上に半透明で重ねて表示できる Java Swing 製のホワイトボードです。  
`OverlayWhiteboard_Full.java` を中心に、ペン、消しゴム、レーザー、文字入力、図形描画、PNG 保存、自動保存などの機能を備えています。

## 動作環境

- Windows 10 / 11
- JDK 21 以上
- 64bit 環境推奨

`jpackage` を使って `.exe` を作る場合も、JDK 21 以上が必要です。

## 主な機能

- 常に手前に表示されるオーバーレイホワイトボード
- ペン / 消しゴム / レーザー / 文字 / 図形
- 図形: 直線 / 四角 / 角丸四角 / 楕円
- 背景の切り替え: 白 / 透過
- `Undo` / `Redo` / `Clear`
- PNG 保存
  - `保存(背景)` : 白背景付き PNG
  - `保存(透過)` : 透過 PNG
- 3 分間隔の自動保存
- ボードの一時非表示

## ファイル構成

- `OverlayWhiteboard_Full.java`
  - メインのソースコード
- `manifest.txt`
  - JAR 作成時の `Main-Class` 定義
- `icon.ico`
  - Windows アプリ用アイコン
- `app-image.bat`
  - `jpackage --type app-image` 実行用バッチ
- `package-input/TransparentBoard.jar`
  - `jpackage` 入力用 JAR

## ソースコードから実行する方法

`TransparentWhiteboard` フォルダで PowerShell を開いて実行します。

```powershell
cd "c:\（フォルダのパス）\TransparentWhiteboard"
javac --release 21 OverlayWhiteboard_Full.java
java OverlayWhiteboard_Full
```

`.class` ファイルが生成されたあと、`java OverlayWhiteboard_Full` で起動できます。

## JAR ファイルの作り方

配布や `jpackage` の前に、実行可能 JAR を作成します。

```powershell
cd "c:\U（フォルダのパス）\TransparentWhiteboard"
javac --release 21 OverlayWhiteboard_Full.java
jar cfm TransparentBoard.jar manifest.txt OverlayWhiteboard_Full*.class
```

`manifest.txt` には以下が定義されています。

```text
Main-Class: OverlayWhiteboard_Full
```

作成した JAR の動作確認:

```powershell
java -jar TransparentBoard.jar
```

`jpackage` 用に配置する場合は、`package-input` フォルダへコピーします。

```powershell
if (!(Test-Path package-input)) { New-Item -ItemType Directory package-input | Out-Null }
Copy-Item .\TransparentBoard.jar .\package-input\TransparentBoard.jar -Force
```

## app-image の作り方

フォルダ配布用の Windows アプリ一式を作る手順です。  
このプロジェクトには `app-image.bat` が用意されています。

### 方法 1: バッチファイルを使う

```powershell
cd "c:\（フォルダのパス）\TransparentWhiteboard"
.\app-image.bat
```

### 方法 2: コマンドを直接実行する

```powershell
cd "c:\（フォルダのパス）\TransparentWhiteboard"
jpackage `
  --name TransparentBoard `
  --input "package-input" `
  --main-jar TransparentBoard.jar `
  --type app-image `
  --dest "image-out" `
  --icon icon.ico `
  --win-console
```

生成先:

- `image-out\TransparentBoard\`

この中に `TransparentBoard.exe` と実行用ランタイム一式が作成されます。  
インストール不要で、そのままフォルダごと配布できます。

## Windows の `.exe` インストーラを作る方法

インストーラ形式の `.exe` を作るには、`jpackage --type exe` を使います。

### 1. 事前準備

以下が揃っていることを確認します。

- `TransparentBoard.jar`
- `package-input\TransparentBoard.jar`
- `icon.ico`
- JDK 21 以上

### 2. `.exe` インストーラを作成

```powershell
cd "c:\（フォルダのパス）TransparentWhiteboard"
jpackage `
  --name TransparentBoard `
  --input "package-input" `
  --main-jar TransparentBoard.jar `
  --type exe `
  --dest "installer-out" `
  --icon icon.ico `
  --win-console `
  --win-dir-chooser `
  --win-shortcut `
  --win-menu
```

生成先:

- `installer-out\TransparentBoard-<version>.exe` または `installer-out\TransparentBoard.exe`

`jpackage` のバージョンや指定オプションにより出力名が多少変わる場合があります。

### 3. インストーラの意味

`--type exe` で作られるものは、アプリ本体そのものではなく Windows 用インストーラです。  
この `.exe` を実行すると、スタートメニューやショートカット付きでアプリをインストールできます。

## すぐ使えるビルド手順まとめ

最初から順に実行する場合は次の流れです。

```powershell
cd "c:\（フォルダのパス）\TransparentWhiteboard"

javac --release 21 OverlayWhiteboard_Full.java
jar cfm TransparentBoard.jar manifest.txt OverlayWhiteboard_Full*.class

if (!(Test-Path package-input)) { New-Item -ItemType Directory package-input | Out-Null }
Copy-Item .\TransparentBoard.jar .\package-input\TransparentBoard.jar -Force

jpackage `
  --name TransparentBoard `
  --input "package-input" `
  --main-jar TransparentBoard.jar `
  --type exe `
  --dest "installer-out" `
  --icon icon.ico `
  --win-console `
  --win-dir-chooser `
  --win-shortcut `
  --win-menu
```

## 補足

- 実行時に保存される画像は PNG 形式です。
- ソース修正後は、再度 `javac` と `jar` を実行してから `jpackage` を行ってください。
- `.exe` を作らず、配布フォルダだけ欲しい場合は `--type app-image` を使ってください。
