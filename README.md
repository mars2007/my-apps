# my-apps

個人で開発・管理しているアプリケーションやツール、実験用プロトタイプをまとめているモノリポ（統合）リポジトリです。
GitHub Pagesを利用して、Webアプリケーション、ブラウザ拡張機能、AI連携ツールなどのソースコードを公開・管理しています。

## 🚀 収録プロジェクト・アプリ一覧

現在、このリポジトリには以下の5つのプロジェクトが含まれています。各フォルダにそれぞれのソースコードが配置されています。

| ディレクトリ名 | プロジェクト名 / 概要 | 主な技術・環境 |
| :--- | :--- | :--- |
| 📁 [EasyEnglishChat](./EasyEnglishChat/) | **やさしい英会話練習システム(Web版)**<br>英語でのチャットや会話を手軽に行える学習支援ツール。速度調整可能。API不要。無料。 https://mars2007.github.io/my-apps//EasyEnglishChat/index.html| HTML / CSS / JS |
| 📁 [SpeechToText](./SpeechToText/) | **バリアフリー会話システム(Web版) **<br>音声をリアルタイムでキャッチし、テキストへ変換するツール。 | Web Speech API |
| 📁 [TextToSpeech](./TextToSpeech/) | **ホームページ読み上げシステム(Web版) **<br>ふりがな（ルビ）の読み分けや常駐パネルを備えたChrome拡張機能。 | Manifest V3 / JS |
| 📁 [TransparentWhiteboard](./TransparentWhiteboard/) | **Transparent Board(Windowsアプリ版)**<br>画面上に透明なレイヤーを重ねて、直接メモや描画ができるツール。 | Windows版 |
| 📁 [WebcamWipeWindow](./WebcamWipeWindow/) | **WebCamWipeWindow(Windowsアプリ版)**<br>ウェブカメラの映像を活用した、ユニークなウィンドウ操作・表示ツール。 | Windows版 |

*※新しいツールを開発・更新次第、随時この一覧を追加・修正していきます。*

---

## 🛠️ 基本的な利用方法

### 1. 公開ページでの利用
本リポジトリに格納されているWebアプリケーションは、上記の各プロジェクトフォルダ配下から入手できます。EasyEnglishChat以外は、設定、コンパイル等が必要です。

### 2. ローカル環境へのダウンロード（クローン）
手元のパソコンでコードを編集したり実行したりする場合は、以下のコマンドを使用します。

```bash
git clone [https://github.com/mars2007/my-apps.git](https://github.com/mars2007/my-apps.git)
cd my-apps

### 3．各アプリの詳細設定
Chrome拡張機能（TextToSpeech）の導入手順や、ローカルAI（Ollamaなど）と連携するツールの詳細なセットアップ手順については、各プロジェクトフォルダ配下に用意されている個別の README.md を参照してください。

💻 共通の開発・動作環境
主に以下の言語や環境を中心にツールを作成しています。

Frontend: HTML5, CSS3, JavaScript (Vanilla JS)

API / 外部連携: Web Speech API (音声認識・音声合成), OpenAI互換API

環境: Google Chrome / Microsoft Edge (デベロッパーモード対応)

📝 ライセンス / 免責事項
本リポジトリ内のコードは、個人利用および実験・研究を目的として作成されたものです。
本ツールの利用に伴う不具合やトラブル、損害について、作成者は一切の責任を負いません。すべて自己責任でのご利用をお願いいたします。
外部APIやブラウザの仕様変更等により、予告なく一部機能が動作しなくなる場合があります。あらかじめご了承ください。
