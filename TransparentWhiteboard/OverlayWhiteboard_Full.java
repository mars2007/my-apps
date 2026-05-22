import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import java.awt.RadialGradientPaint;

public class OverlayWhiteboard_Full {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new OverlayWhiteboard_Full().start());
    }

    private enum Tool { PEN, ERASER, LASER, TEXT, SHAPE }
    private enum BackgroundMode { WHITE, TRANSPARENT }
    private enum ShapeType { LINE, RECT, ROUND_RECT, ELLIPSE }

    private Tool currentTool = Tool.PEN;
    private BackgroundMode bgMode = BackgroundMode.WHITE;
    private ShapeType currentShape = ShapeType.LINE;

    // デフォルト色を赤に
    private Color currentColor = Color.RED;

    // 各種設定
    private float penWidth = 4f;
    private int   penAlpha = 255;
    private float eraserWidth = 30f;
    private int   laserSize = 20;

    private int   textSize = 28;
    private String textFontName = "Yu Gothic";

    // 角丸四角の角の丸さ（半径）
    private int roundRectRadius = 20;

    private JFrame frame;
    private DrawPanel drawPanel;
    private JToolBar bar1;
    private JToolBar bar2;
    private JToolBar headerBar;
    private JPanel colorPreview;
    private JComboBox<String> fontCombo;   // フォント選択用

    // ウィンドウ移動
    private Point dragStart = null;

    // 最大化管理
    private boolean maximized = false;
    private Rectangle normalBounds = null;

    // リサイズ中フラグ
    private boolean resizingActive = false;

    // ボード一時非表示用
    private boolean boardHidden = false;
    private Rectangle boardShownBounds = null;

    // 自動保存
    private javax.swing.Timer autoSaveTimer = null;
    private File autoSaveFile = null;
    // ★ 自動保存間隔を 3 分に変更
    private int autoSaveIntervalMs = 180_000; // 3分ごと

    // レイヤ表示フラグ
    private boolean showStrokes = true;
    private boolean showTexts   = true;
    private boolean showShapes  = true;

    // ==============================
    // 起動
    // ==============================
    private void start() {
        frame = new JFrame("Overlay Transparent Whiteboard");
        frame.setUndecorated(true);
        frame.setAlwaysOnTop(true);

        // ほぼ透明な背景
        frame.setBackground(new Color(0, 0, 0, 1));
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        JPanel root = (JPanel) frame.getContentPane();
        root.setLayout(new BorderLayout());
        root.setOpaque(false);

        drawPanel = new DrawPanel();
        drawPanel.setOpaque(false);

        JComponent bars = createTopPanel();
        root.add(bars, BorderLayout.NORTH);
        root.add(drawPanel, BorderLayout.CENTER);

        // 全方向リサイズ
        WindowResizer wr = new WindowResizer(frame);
        frame.addMouseListener(wr);
        frame.addMouseMotionListener(wr);
        addResizeSupport(frame.getRootPane(), wr);  // 中身にも全部付与

        // タイトルバー（ヘッダ＋ツールバー）で移動
        addMoveSupport(bars);

        // Ctrl+Z / Ctrl+Y ショートカット
        JRootPane rp = frame.getRootPane();
        InputMap im = rp.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = rp.getActionMap();
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK), "undo");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK), "redo");

        am.put("undo", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                drawPanel.undoLast();
            }
        });
        am.put("redo", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                drawPanel.redoLast();
            }
        });

        frame.setSize(900, 600);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        // 表示後にスライダー幅を再計算
        SwingUtilities.invokeLater(this::updateSliders);
    }

    // コンポーネントツリー全体にリサイズ用リスナーを付与
    private void addResizeSupport(Component c, WindowResizer wr) {
        c.addMouseListener(wr);
        c.addMouseMotionListener(wr);
        if (c instanceof Container) {
            for (Component child : ((Container) c).getComponents()) {
                addResizeSupport(child, wr);
            }
        }
    }

    // ==============================
    // ウィンドウ移動（bars 全体＋その子孫すべて）
    // ==============================
    private void addMoveSupport(Component comp) {
        MouseAdapter mover = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                dragStart = e.getLocationOnScreen();
            }
            @Override public void mouseDragged(MouseEvent e) {
                if (dragStart == null) return;
                Point cur = e.getLocationOnScreen();
                Point loc = frame.getLocation();
                frame.setLocation(
                        loc.x + (cur.x - dragStart.x),
                        loc.y + (cur.y - dragStart.y)
                );
                dragStart = cur;
            }
        };
        addMoveSupportRecursive(comp, mover);
    }

    private void addMoveSupportRecursive(Component c, MouseAdapter mover) {
        c.addMouseListener(mover);
        c.addMouseMotionListener(mover);
        if (c instanceof Container) {
            for (Component child : ((Container) c).getComponents()) {
                addMoveSupportRecursive(child, mover);
            }
        }
    }

    // ==============================
    // 一番上＋ツールバー2段（計3段）
    // ==============================
    private JComponent createTopPanel() {
        // 1: header, 2: bar1, 3: bar2
        JPanel panel = new JPanel(new GridLayout(3, 1));

        // --- 1段目: タイトルヘッダ + Undo/Redo/Save + ウィンドウボタン ---
        headerBar = new JToolBar();
        headerBar.setFloatable(false);

        // アイコン用スペース
        JLabel iconSpace = new JLabel();
        iconSpace.setPreferredSize(new Dimension(24, 24));
        headerBar.add(iconSpace);
        headerBar.add(Box.createHorizontalStrut(8));

        // メインタイトル（青・太字）
        JLabel titleMain = new JLabel("Transparent Board ver.1");
        titleMain.setForeground(Color.BLUE);
        Font base = titleMain.getFont().deriveFont(Font.BOLD);
        titleMain.setFont(base);
        headerBar.add(titleMain);

        // 少しスペースを空ける
        headerBar.add(Box.createHorizontalStrut(30));

        // 著作権表示（黒・細字）
        JLabel titleCopy = new JLabel("Copyright Fumiaki Masakiyo");
        titleCopy.setForeground(Color.BLACK);
        titleCopy.setFont(titleCopy.getFont().deriveFont(Font.PLAIN));
        headerBar.add(titleCopy);

        headerBar.add(Box.createHorizontalStrut(20));

        // ---- Undo / Redo / Clear / Save(背景) / Save(透過) ----
        JButton undo = new JButton("Undo");
        JButton redo = new JButton("Redo");
        JButton clear = new JButton("Clear");
        JButton saveBG = new JButton("保存(背景)");
        JButton saveTR = new JButton("保存(透過)");

        undo.addActionListener(e -> drawPanel.undoLast());
        redo.addActionListener(e -> drawPanel.redoLast());
        clear.addActionListener(e -> drawPanel.clearAll());
        saveBG.addActionListener(e -> doSaveWithBackground());
        saveTR.addActionListener(e -> doSaveTransparent());

        headerBar.add(undo);
        headerBar.add(redo);
        headerBar.add(clear);
        headerBar.add(saveBG);
        headerBar.add(saveTR);

        // ---- 自動保存 ON/OFF（保存系の3つをまとめる） ----
        JToggleButton autoSaveBtn = new JToggleButton("自動保存OFF");
        autoSaveBtn.addActionListener(e -> toggleAutoSave(autoSaveBtn));
        headerBar.add(autoSaveBtn);

        headerBar.add(Box.createHorizontalStrut(10));

        // ---- ボード一時非表示ボタン ----
        JButton toggleBoardBtn = new JButton("ボード一時非表示");
        toggleBoardBtn.addActionListener(e -> toggleBoardVisible(toggleBoardBtn));
        headerBar.add(toggleBoardBtn);

        headerBar.add(Box.createHorizontalGlue());

        // 右上の -, □, ×
        JButton min = new JButton("—");
        JButton max = new JButton("□");
        JButton cls = new JButton("×");
        min.addActionListener(e -> frame.setState(Frame.ICONIFIED));
        max.addActionListener(e -> toggleMaximize());
        cls.addActionListener(e -> frame.dispose());
        headerBar.add(min);
        headerBar.add(max);
        headerBar.add(cls);

        panel.add(headerBar);

        // --- 2〜3段目: ツールバー本体を準備 ---
        createToolbars();   // bar1, bar2 を初期化

        panel.add(bar1);    // 2段目
        panel.add(bar2);    // 3段目（スライダ用＋レイヤチェック）

        return panel;
    }

    // ボード表示/非表示トグル
    private void toggleBoardVisible(JButton btn) {
        if (!boardHidden) {
            // 現在のウィンドウサイズを保存
            boardShownBounds = frame.getBounds();

            // 3段分（headerBar, bar1, bar2）の高さを計算
            int h = headerBar.getHeight() + bar1.getHeight() + bar2.getHeight();
            if (h <= 0) {
                h = headerBar.getPreferredSize().height
                        + bar1.getPreferredSize().height
                        + bar2.getPreferredSize().height;
            }

            Rectangle b = boardShownBounds;
            frame.setBounds(b.x, b.y, b.width, h);
            boardHidden = true;
            btn.setText("ボード再表示");
        } else {
            if (boardShownBounds != null) {
                frame.setBounds(boardShownBounds);
            }
            boardHidden = false;
            btn.setText("ボード一時非表示");
        }
    }

    // 自動保存ON/OFF
    private void toggleAutoSave(JToggleButton btn) {
        if (btn.isSelected()) {
            // 自動保存ON
            if (autoSaveFile == null) {
                JFileChooser chooser = new JFileChooser();
                chooser.setDialogTitle("自動保存するPNGファイルを指定してください");
                chooser.setFileFilter(new FileNameExtensionFilter("PNG 画像 (*.png)", "png"));
                int result = chooser.showSaveDialog(frame);
                if (result != JFileChooser.APPROVE_OPTION) {
                    // キャンセルされたのでOFFに戻す
                    btn.setSelected(false);
                    return;
                }
                File f = chooser.getSelectedFile();
                if (!f.getName().toLowerCase().endsWith(".png")) {
                    f = new File(f.getParentFile(), f.getName() + ".png");
                }
                autoSaveFile = f;
            }
            if (autoSaveTimer == null) {
                autoSaveTimer = new javax.swing.Timer(autoSaveIntervalMs, e -> doAutoSave());
            }
            autoSaveTimer.start();
            btn.setText("自動保存ON");
        } else {
            // 自動保存OFF
            if (autoSaveTimer != null) {
                autoSaveTimer.stop();
            }
            btn.setText("自動保存OFF");
        }
    }

    // 実際の自動保存処理（ダイアログ無し）
    private void doAutoSave() {
        if (autoSaveFile == null) return;
        try {
            if (bgMode == BackgroundMode.WHITE) {
                drawPanel.saveWhiteBgPng(autoSaveFile);
            } else {
                // 透過モード時はスクショで背景ごと保存
                saveScreenShotOfDrawArea(autoSaveFile);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            // 自動保存なのでエラーはログだけにしてダイアログは出さない
        }
    }

    // ==============================
    // ツールバー（操作パネル：2段目 bar1、3段目 bar2）
    // ==============================
    private void createToolbars() {
        bar1 = new JToolBar();
        bar2 = new JToolBar();
        bar1.setFloatable(false);
        bar2.setFloatable(false);

        // bar2 はスライダ + レイヤチェックなので左寄せレイアウト
        bar2.setLayout(new FlowLayout(FlowLayout.LEFT, 5, 0));

        // ------------------------------
        // 色（黒・赤・青 + 緑・黄・白 + フルカラー）
        // ------------------------------
        bar1.add(new JLabel("色: "));

        JButton b1 = new JButton("黒");
        JButton b2 = new JButton("赤");
        JButton b3 = new JButton("青");
        JButton b4 = new JButton("緑");
        JButton b5 = new JButton("黄");
        JButton b6 = new JButton("白");

        b1.addActionListener(e -> setColor(Color.BLACK));
        b2.addActionListener(e -> setColor(Color.RED));
        b3.addActionListener(e -> setColor(Color.BLUE));
        b4.addActionListener(e -> setColor(Color.GREEN));
        b5.addActionListener(e -> setColor(Color.YELLOW));
        b6.addActionListener(e -> setColor(Color.WHITE));

        bar1.add(b1);
        bar1.add(b2);
        bar1.add(b3);
        bar1.add(b4);
        bar1.add(b5);
        bar1.add(b6);

        // 現在色プレビュー（クリックでカラーピッカー）
        colorPreview = new JPanel();
        colorPreview.setPreferredSize(new Dimension(25, 25));
        colorPreview.setBackground(currentColor);  // デフォルトは赤
        colorPreview.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY, 1));
        colorPreview.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                Color newColor = JColorChooser.showDialog(
                        frame, "色を選択", currentColor
                );
                if (newColor != null) setColor(newColor);
            }
        });
        bar1.add(colorPreview);

        bar1.add(Box.createHorizontalStrut(10));

        // ------------------------------
        // 背景モード
        // ------------------------------
        bar1.add(new JLabel("背景: "));
        JToggleButton bgW = new JToggleButton("白");
        JToggleButton bgT = new JToggleButton("透過");
        ButtonGroup bgGroup = new ButtonGroup();
        bgGroup.add(bgW);
        bgGroup.add(bgT);
        bgW.setSelected(true);
        bgW.addActionListener(e -> { bgMode = BackgroundMode.WHITE; drawPanel.repaint(); });
        bgT.addActionListener(e -> { bgMode = BackgroundMode.TRANSPARENT; drawPanel.repaint(); });
        bar1.add(bgW);
        bar1.add(bgT);

        bar1.add(Box.createHorizontalStrut(10));

        // ------------------------------
        // ツール選択
        // ------------------------------
        bar1.add(new JLabel("ツール: "));
        JToggleButton pen   = new JToggleButton("ペン");
        JToggleButton era   = new JToggleButton("消しゴム");
        JToggleButton las   = new JToggleButton("レーザー");
        JToggleButton txt   = new JToggleButton("文字");
        JToggleButton shape = new JToggleButton("図形");

        ButtonGroup toolGroup = new ButtonGroup();
        toolGroup.add(pen);
        toolGroup.add(era);
        toolGroup.add(las);
        toolGroup.add(txt);
        toolGroup.add(shape);
        pen.setSelected(true);

        pen.addActionListener(e -> { currentTool = Tool.PEN;    updateSliders(); });
        era.addActionListener(e -> { currentTool = Tool.ERASER; updateSliders(); });
        las.addActionListener(e -> { currentTool = Tool.LASER;  updateSliders(); });
        txt.addActionListener(e -> { currentTool = Tool.TEXT;   updateSliders(); });
        shape.addActionListener(e -> { currentTool = Tool.SHAPE; updateSliders(); });

        bar1.add(pen);
        bar1.add(era);
        bar1.add(las);
        bar1.add(txt);
        bar1.add(shape);

        bar1.add(Box.createHorizontalStrut(10));

        // ------------------------------
        // 図形ボタン（直線・四角・角丸四角・楕円）
        // ------------------------------
        JPanel shapePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        JToggleButton btnLine    = new JToggleButton("直線");
        JToggleButton btnRect    = new JToggleButton("四角");
        JToggleButton btnRound   = new JToggleButton("角丸四角");
        JToggleButton btnEllipse = new JToggleButton("楕円");

        ButtonGroup shapeGroup = new ButtonGroup();
        shapeGroup.add(btnLine);
        shapeGroup.add(btnRect);
        shapeGroup.add(btnRound);
        shapeGroup.add(btnEllipse);

        // 現在の選択に合わせて ON
        switch (currentShape) {
            case LINE:        btnLine.setSelected(true);   break;
            case RECT:        btnRect.setSelected(true);   break;
            case ROUND_RECT:  btnRound.setSelected(true);  break;
            case ELLIPSE:     btnEllipse.setSelected(true);break;
        }

        btnLine.addActionListener(e -> currentShape = ShapeType.LINE);
        btnRect.addActionListener(e -> currentShape = ShapeType.RECT);
        btnRound.addActionListener(e -> currentShape = ShapeType.ROUND_RECT);
        btnEllipse.addActionListener(e -> currentShape = ShapeType.ELLIPSE);

        shapePanel.add(new JLabel("図形: "));
        shapePanel.add(btnLine);
        shapePanel.add(btnRect);
        shapePanel.add(btnRound);
        shapePanel.add(btnEllipse);

        bar1.add(shapePanel);

        bar1.add(Box.createHorizontalGlue());

        // 初期スライダー表示
        updateSliders();
    }

    // ==============================
    // スライダー更新（3段目 bar2 専用）
    // ==============================
    private void updateSliders() {
        if (bar2 == null || frame == null) return;
        bar2.removeAll();

        // bar2 は「レイヤチェック + スライダーバー」専用
        bar2.setLayout(new FlowLayout(FlowLayout.LEFT, 5, 0));

        // ---- レイヤ表示チェックボックス ----
        bar2.add(new JLabel("表示: "));

        JCheckBox chkStroke = new JCheckBox("線", showStrokes);
        JCheckBox chkText   = new JCheckBox("文字", showTexts);
        JCheckBox chkShape  = new JCheckBox("図形", showShapes);

        chkStroke.addActionListener(e -> {
            showStrokes = chkStroke.isSelected();
            drawPanel.requestFullRedraw();  // レイヤON/OFFのたびに再描画
        });
        chkText.addActionListener(e -> {
            showTexts = chkText.isSelected();
            drawPanel.requestFullRedraw();
        });
        chkShape.addActionListener(e -> {
            showShapes = chkShape.isSelected();
            drawPanel.requestFullRedraw();
        });

        bar2.add(chkStroke);
        bar2.add(chkText);
        bar2.add(chkShape);

        // チェックボックスとスライダーの間隔
        bar2.add(Box.createHorizontalStrut(20));

        int baseW = Math.max(frame.getWidth(), 400);
        int w;

        if (currentTool == Tool.SHAPE) {
            // 図形モードだけ短め（ウィンドウ幅の 1/8 くらい）
            w = Math.max(baseW / 8, 80);
        } else {
            // それ以外は 1/4
            w = Math.max(baseW / 4, 150);
        }

        if (currentTool == Tool.PEN) {
            // ペン
            JSlider slW = new JSlider(1, 40, (int) penWidth);
            fixSliderSize(slW, w);
            JLabel lblW = new JLabel((int)penWidth + " px");

            slW.addChangeListener(e -> {
                penWidth = slW.getValue();
                lblW.setText((int)penWidth + " px");
            });

            JSlider slA = new JSlider(10, 255, penAlpha);
            fixSliderSize(slA, w);
            JLabel lblA = new JLabel(penAlpha + "");

            slA.addChangeListener(e -> {
                penAlpha = slA.getValue();
                lblA.setText(penAlpha + "");
            });

            bar2.add(new JLabel("ペン太さ "));
            bar2.add(slW);
            bar2.add(lblW);
            bar2.add(Box.createHorizontalStrut(10));
            bar2.add(new JLabel("濃度 "));
            bar2.add(slA);
            bar2.add(lblA);

        } else if (currentTool == Tool.SHAPE) {
            // 図形用スライダ（太さ／濃度／角丸半径）
            JSlider slW = new JSlider(1, 40, (int) penWidth);
            fixSliderSize(slW, w);
            JLabel lblW = new JLabel((int)penWidth + " px");
            slW.addChangeListener(e -> {
                penWidth = slW.getValue();
                lblW.setText((int)penWidth + " px");
            });

            JSlider slA = new JSlider(10, 255, penAlpha);
            fixSliderSize(slA, w);
            JLabel lblA = new JLabel(penAlpha + "");
            slA.addChangeListener(e -> {
                penAlpha = slA.getValue();
                lblA.setText(penAlpha + "");
            });

            JSlider slR = new JSlider(0, 80, roundRectRadius);
            fixSliderSize(slR, w);
            JLabel lblR = new JLabel(roundRectRadius + " px");
            slR.addChangeListener(e -> {
                roundRectRadius = slR.getValue();
                lblR.setText(roundRectRadius + " px");
            });

            bar2.add(new JLabel("太さ "));
            bar2.add(slW);
            bar2.add(lblW);
            bar2.add(Box.createHorizontalStrut(10));
            bar2.add(new JLabel("濃度 "));
            bar2.add(slA);
            bar2.add(lblA);
            bar2.add(Box.createHorizontalStrut(10));
            bar2.add(new JLabel("角丸半径 "));
            bar2.add(slR);
            bar2.add(lblR);

        } else if (currentTool == Tool.ERASER) {
            JSlider sl = new JSlider(10, 80, (int) eraserWidth);
            fixSliderSize(sl, w);
            JLabel lbl = new JLabel((int)eraserWidth + " px");
            sl.addChangeListener(e -> {
                eraserWidth = sl.getValue();
                lbl.setText((int)eraserWidth + " px");
            });
            bar2.add(new JLabel("消しゴム太さ "));
            bar2.add(sl);
            bar2.add(lbl);

        } else if (currentTool == Tool.LASER) {
            JSlider sl = new JSlider(5, 60, laserSize);
            fixSliderSize(sl, w);
            JLabel lbl = new JLabel(laserSize + " px");
            sl.addChangeListener(e -> {
                laserSize = sl.getValue();
                lbl.setText(laserSize + " px");
            });
            bar2.add(new JLabel("レーザー "));
            bar2.add(sl);
            bar2.add(lbl);

        } else if (currentTool == Tool.TEXT) {
            // 文字サイズスピナー
            SpinnerNumberModel model = new SpinnerNumberModel(textSize, 8, 80, 1);
            JSpinner sp = new JSpinner(model);
            sp.addChangeListener(e -> textSize = (Integer) sp.getValue());
            Dimension d = sp.getPreferredSize();
            d.width = 60; // 半角4桁くらい
            sp.setPreferredSize(d);
            sp.setMaximumSize(d);
            sp.setMinimumSize(d);

            bar2.add(new JLabel("文字サイズ "));
            bar2.add(sp);
            bar2.add(Box.createHorizontalStrut(10));

            // フォント選択コンボボックス（初回だけ作成）
            if (fontCombo == null) {
                String[] fontNames = GraphicsEnvironment
                        .getLocalGraphicsEnvironment()
                        .getAvailableFontFamilyNames();
                fontCombo = new JComboBox<>(fontNames);
                fontCombo.setSelectedItem(textFontName);
                fontCombo.addActionListener(e -> {
                    String name = (String) fontCombo.getSelectedItem();
                    if (name != null) {
                        textFontName = name;
                    }
                });
                Dimension fd = fontCombo.getPreferredSize();
                fd.width = 160; // 横幅を少し抑える
                fontCombo.setPreferredSize(fd);
                fontCombo.setMaximumSize(fd);
                fontCombo.setMinimumSize(fd);
            }

            bar2.add(new JLabel("フォント "));
            bar2.add(fontCombo);
        }

        bar2.revalidate();
        bar2.repaint();
    }

    private void fixSliderSize(JSlider s, int w) {
        Dimension d = new Dimension(w, 25);
        s.setPreferredSize(d);
        s.setMaximumSize(d);
        s.setMinimumSize(d);
    }

    private void toggleMaximize() {
        if (!maximized) {
            normalBounds = frame.getBounds();
            GraphicsDevice gd = GraphicsEnvironment
                    .getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice();
            Rectangle r = gd.getDefaultConfiguration().getBounds();
            frame.setBounds(r);
            maximized = true;
        } else {
            if (normalBounds != null) frame.setBounds(normalBounds);
            maximized = false;
        }
    }

    private void setColor(Color c) {
        currentColor = c;
        if (colorPreview != null) {
            colorPreview.setBackground(c);
        }
    }

    // ==============================
    // 保存(背景込み)
    // ==============================
    private void doSaveWithBackground() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("PNG で保存（背景込み）");
        chooser.setFileFilter(new FileNameExtensionFilter("PNG 画像 (*.png)", "png"));

        int result = chooser.showSaveDialog(frame);
        if (result != JFileChooser.APPROVE_OPTION) return;

        File f = chooser.getSelectedFile();
        if (!f.getName().toLowerCase().endsWith(".png")) {
            f = new File(f.getParentFile(), f.getName() + ".png");
        }

        if (bgMode == BackgroundMode.WHITE) {
            try {
                drawPanel.saveWhiteBgPng(f);
                JOptionPane.showMessageDialog(frame, "保存しました:\n" + f.getAbsolutePath());
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(frame, "保存に失敗しました:\n" + ex.getMessage(),
                        "エラー", JOptionPane.ERROR_MESSAGE);
            }
        } else {
            File target = f;
            new javax.swing.Timer(200, e -> {
                ((javax.swing.Timer) e.getSource()).stop();
                try {
                    saveScreenShotOfDrawArea(target);
                    JOptionPane.showMessageDialog(frame, "保存しました:\n" + target.getAbsolutePath());
                } catch (Exception ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(frame, "保存に失敗しました:\n" + ex.getMessage(),
                            "エラー", JOptionPane.ERROR_MESSAGE);
                }
            }).start();
        }
    }

    // ==============================
    // 保存(透過: 線＋文字のみ)
    // ==============================
    private void doSaveTransparent() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("PNG で保存（透過・線+文字のみ）");
        chooser.setFileFilter(new FileNameExtensionFilter("PNG 画像 (*.png)", "png"));

        int result = chooser.showSaveDialog(frame);
        if (result != JFileChooser.APPROVE_OPTION) return;

        File f = chooser.getSelectedFile();
        if (!f.getName().toLowerCase().endsWith(".png")) {
            f = new File(f.getParentFile(), f.getName() + ".png");
        }

        try {
            drawPanel.saveTransparentPng(f);
            JOptionPane.showMessageDialog(frame, "保存しました:\n" + f.getAbsolutePath());
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(frame, "保存に失敗しました:\n" + ex.getMessage(),
                    "エラー", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ==============================
    // 描画領域のスクショ
    // ==============================
    private void saveScreenShotOfDrawArea(File file) throws Exception {
        Point p = drawPanel.getLocationOnScreen();
        Dimension d = drawPanel.getSize();
        Rectangle rect = new Rectangle(p.x, p.y, d.width, d.height);
        Robot robot = new Robot();
        BufferedImage img = robot.createScreenCapture(rect);
        ImageIO.write(img, "png", file);
    }

    // ==============================
    // 描画パネル（内部キャンバス方式）
    // ==============================
    private class DrawPanel extends JComponent {

        private List<StrokeData> strokes = new ArrayList<>();
        private List<TextItem>   texts   = new ArrayList<>();
        private List<ShapeItem>  shapes  = new ArrayList<>();
        private StrokeData currentStroke = null;

        private Point laserPointScreen = null;

        // 文字ドラッグ用
        private TextItem draggingText = null;
        private Point dragOffset = null;

        // 図形プレビュー用（新規作成）
        private boolean drawingShape = false;
        private Point shapeStartScreen = null;
        private Point shapeEndScreen   = null;

        // 図形ドラッグ用
        private ShapeItem draggingShape = null;
        private Point shapeDragStartScreen = null;
        private int shapeOrigX, shapeOrigY, shapeOrigW, shapeOrigH;

        // 内部キャンバス
        private BufferedImage canvas = null;
        private boolean needsRedraw = true;

        // スナップショット履歴
        private List<CanvasState> history = new ArrayList<>();
        private int historyIndex = -1;

        public DrawPanel() {
            setFocusable(true);

            // 初期状態（空のキャンバス）を履歴に積む
            pushStateForUndo();

            MouseAdapter h = new MouseAdapter() {

                @Override
                public void mousePressed(MouseEvent e) {
                    if (resizingActive) return; // リサイズ中は無視

                    requestFocusInWindow();
                    Point screenPt = e.getLocationOnScreen();

                    if (currentTool == Tool.LASER) {
                        // レーザーは状態を変えないので undo 履歴には積まない
                        laserPointScreen = screenPt;
                        repaint();
                        return;
                    }

                    if (currentTool == Tool.TEXT) {
                        TextItem hit = findTextAt(screenPt);
                        if (hit != null) {
                            // 既存テキスト
                            if (e.getClickCount() >= 2) {
                                // 編集 → 状態が変わるので事前にスナップショット
                                pushStateForUndo();
                                String s = JOptionPane.showInputDialog(
                                        frame,
                                        "文字を編集：",
                                        hit.text
                                );
                                if (s != null && !s.isEmpty()) {
                                    hit.text = s;
                                    needsRedraw = true;
                                    repaint();
                                }
                            } else {
                                // 移動 → ドラッグ開始前にスナップショット
                                pushStateForUndo();
                                draggingText = hit;
                                dragOffset = new Point(
                                        screenPt.x - hit.screenPos.x,
                                        screenPt.y - hit.screenPos.y
                                );
                            }
                        } else {
                            // 新規テキスト → 追加前にスナップショット
                            String s = JOptionPane.showInputDialog(
                                    frame,
                                    "文字を入力してください：",
                                    "テキスト入力",
                                    JOptionPane.PLAIN_MESSAGE
                            );
                            if (s != null && !s.isEmpty()) {
                                pushStateForUndo();
                                TextItem ti = new TextItem();
                                ti.text = s;
                                ti.screenPos = screenPt;
                                ti.color = currentColor;
                                ti.font = new Font(textFontName, Font.PLAIN, textSize);
                                texts.add(ti);
                                needsRedraw = true;
                                repaint();
                            }
                        }
                        return;
                    }

                    if (currentTool == Tool.SHAPE) {
                        // まず既存図形を掴んで移動するかどうか判定
                        ShapeItem hitShape = findShapeAt(screenPt);
                        if (hitShape != null) {
                            // 図形ドラッグ開始 → 状態変化前にスナップショット
                            pushStateForUndo();
                            draggingShape = hitShape;
                            shapeDragStartScreen = screenPt;
                            shapeOrigX = hitShape.x;
                            shapeOrigY = hitShape.y;
                            shapeOrigW = hitShape.w;
                            shapeOrigH = hitShape.h;
                            drawingShape = false;  // 新規描画はしない
                            return;
                        }

                        // 既存図形が無ければ新規図形描画
                        pushStateForUndo();
                        shapeStartScreen = screenPt;
                        shapeEndScreen   = screenPt;
                        drawingShape     = true;
                        draggingShape    = null;
                        repaint();
                        return;
                    }

                    // ペン／消しゴム → 描画前にスナップショット
                    if (currentTool == Tool.PEN || currentTool == Tool.ERASER) {
                        pushStateForUndo();

                        currentStroke = new StrokeData();
                        currentStroke.baseColor = currentColor;
                        currentStroke.eraser    = (currentTool == Tool.ERASER);
                        currentStroke.width     = (currentTool == Tool.PEN) ? penWidth : eraserWidth;
                        currentStroke.alpha     = currentStroke.eraser ? 255 : penAlpha;

                        currentStroke.addScreenPoint(screenPt);
                        strokes.add(currentStroke);
                        needsRedraw = true;
                        repaint();
                    }
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if (resizingActive) return; // リサイズ中は無視

                    Point screenPt = e.getLocationOnScreen();

                    if (currentTool == Tool.LASER) {
                        laserPointScreen = screenPt;
                        repaint();
                        return;
                    }

                    if (currentTool == Tool.TEXT) {
                        if (draggingText != null && dragOffset != null) {
                            draggingText.screenPos = new Point(
                                    screenPt.x - dragOffset.x,
                                    screenPt.y - dragOffset.y
                            );
                            needsRedraw = true;
                            repaint();
                        }
                        return;
                    }

                    if (currentTool == Tool.SHAPE) {
                        // 図形ドラッグ中
                        if (draggingShape != null && shapeDragStartScreen != null) {
                            int dx = screenPt.x - shapeDragStartScreen.x;
                            int dy = screenPt.y - shapeDragStartScreen.y;

                            if (draggingShape.type == ShapeType.LINE) {
                                draggingShape.x = shapeOrigX + dx;
                                draggingShape.y = shapeOrigY + dy;
                                draggingShape.w = shapeOrigW + dx;
                                draggingShape.h = shapeOrigH + dy;
                            } else {
                                draggingShape.x = shapeOrigX + dx;
                                draggingShape.y = shapeOrigY + dy;
                                // w,h はそのまま
                            }
                            needsRedraw = true;
                            repaint();
                            return;
                        }

                        // 新規図形描画中
                        if (drawingShape) {
                            shapeEndScreen = screenPt;
                            repaint();
                        }
                        return;
                    }

                    if (currentStroke != null) {
                        currentStroke.addScreenPoint(screenPt);
                        if (currentTool == Tool.ERASER) {
                            eraseTextAt(screenPt);   // 文字を消す
                            eraseShapeAt(screenPt);  // 図形を消す
                        }
                        needsRedraw = true;
                        repaint();
                    }
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (resizingActive) return; // 念のため

                    if (currentTool == Tool.LASER) {
                        laserPointScreen = null;
                        repaint();
                    }
                    if (currentTool == Tool.TEXT) {
                        draggingText = null;
                        dragOffset = null;
                    }
                    if (currentTool == Tool.PEN || currentTool == Tool.ERASER) {
                        currentStroke = null;
                    }

                    if (currentTool == Tool.SHAPE) {
                        // 図形ドラッグ終了
                        if (draggingShape != null) {
                            draggingShape = null;
                            shapeDragStartScreen = null;
                            // 位置変更後はすでにスナップショット側でカバーされている
                            return;
                        }

                        // 新規図形描画の終了
                        if (drawingShape) {
                            if (shapeStartScreen != null && shapeEndScreen != null) {
                                double dx = shapeEndScreen.x - shapeStartScreen.x;
                                double dy = shapeEndScreen.y - shapeStartScreen.y;
                                double dist2 = dx * dx + dy * dy;
                                if (dist2 > 4.0) { // 2px 以上の移動で有効
                                    ShapeItem item = new ShapeItem();
                                    item.type = currentShape;
                                    int x1 = Math.min(shapeStartScreen.x, shapeEndScreen.x);
                                    int x2 = Math.max(shapeStartScreen.x, shapeEndScreen.x);
                                    int y1 = Math.min(shapeStartScreen.y, shapeEndScreen.y);
                                    int y2 = Math.max(shapeStartScreen.y, shapeEndScreen.y);
                                    item.x = x1;
                                    item.y = y1;
                                    item.w = Math.max(1, x2 - x1);
                                    item.h = Math.max(1, y2 - y1);
                                    item.color = currentColor;
                                    item.alpha = penAlpha;
                                    item.width = penWidth;
                                    item.cornerRadius = roundRectRadius;

                                    // LINE だけは特別に始点終点をそのまま持つ
                                    if (currentShape == ShapeType.LINE) {
                                        item.x = shapeStartScreen.x;
                                        item.y = shapeStartScreen.y;
                                        item.w = shapeEndScreen.x;
                                        item.h = shapeEndScreen.y;
                                    }

                                    shapes.add(item);
                                    needsRedraw = true;
                                }
                            }
                            drawingShape = false;
                            shapeStartScreen = null;
                            shapeEndScreen   = null;
                            repaint();
                        }
                    }
                }
            };

            addMouseListener(h);
            addMouseMotionListener(h);
        }

        // レイヤ設定が変わったときなどにフル再描画させる
        public void requestFullRedraw() {
            needsRedraw = true;
            repaint();
        }

        // ---- スナップショット関連 ----

        private void pushStateForUndo() {
            // redo 分を切り捨て
            if (historyIndex >= 0 && historyIndex < history.size() - 1) {
                history = new ArrayList<>(history.subList(0, historyIndex + 1));
            }
            CanvasState st = new CanvasState();
            st.strokes = new ArrayList<>();
            for (StrokeData s : strokes) {
                st.strokes.add(new StrokeData(s));
            }
            st.texts = new ArrayList<>();
            for (TextItem t : texts) {
                st.texts.add(new TextItem(t));
            }
            st.shapes = new ArrayList<>();
            for (ShapeItem si : shapes) {
                st.shapes.add(new ShapeItem(si));
            }
            history.add(st);
            historyIndex = history.size() - 1;
        }

        private void restoreFromState(CanvasState st) {
            strokes.clear();
            texts.clear();
            shapes.clear();
            currentStroke = null;
            drawingShape = false;
            shapeStartScreen = null;
            shapeEndScreen   = null;
            draggingShape = null;
            shapeDragStartScreen = null;

            for (StrokeData s : st.strokes) {
                strokes.add(new StrokeData(s));
            }
            for (TextItem t : st.texts) {
                texts.add(new TextItem(t));
            }
            for (ShapeItem si : st.shapes) {
                shapes.add(new ShapeItem(si));
            }
            needsRedraw = true;
            repaint();
        }

        public void undoLast() {
            if (historyIndex <= 0) return;
            historyIndex--;
            CanvasState st = history.get(historyIndex);
            restoreFromState(st);
        }

        public void redoLast() {
            if (historyIndex < 0 || historyIndex >= history.size() - 1) return;
            historyIndex++;
            CanvasState st = history.get(historyIndex);
            restoreFromState(st);
        }

        public void clearAll() {
            if (strokes.isEmpty() && texts.isEmpty() && shapes.isEmpty()) return;
            // Clear 前の状態をスナップショット
            pushStateForUndo();
            strokes.clear();
            texts.clear();
            shapes.clear();
            needsRedraw = true;
            repaint();
        }

        // クリック位置付近の文字を探す（矩形ヒット判定）
        private TextItem findTextAt(Point screenPt) {
            Point panelScreen;
            try {
                panelScreen = getLocationOnScreen();
            } catch (IllegalComponentStateException ex) {
                panelScreen = new Point(0, 0);
            }
            int px = screenPt.x - panelScreen.x;
            int py = screenPt.y - panelScreen.y;

            for (TextItem t : texts) {
                Point rel = t.getRelativeTo(panelScreen);
                FontMetrics fm = getFontMetrics(t.font);
                int w = fm.stringWidth(t.text);
                int h = fm.getHeight();
                int ascent = fm.getAscent();
                int x = rel.x;
                int y = rel.y - ascent; // ベースラインから上に ascent 分

                Rectangle rect = new Rectangle(x, y, w, h);
                if (rect.contains(px, py)) {
                    return t;
                }
            }
            return null;
        }

        // クリック位置付近の図形を探す（図形ドラッグ用）
        private ShapeItem findShapeAt(Point screenPt) {
            double pickRadius = 6.0;
            double r2 = pickRadius * pickRadius;

            // 上にあるものを優先したいので、後ろから走査
            for (int i = shapes.size() - 1; i >= 0; i--) {
                ShapeItem s = shapes.get(i);
                boolean hit = false;

                if (s.type == ShapeType.LINE) {
                    double d2 = distancePointToSegmentSquared(
                            screenPt.x, screenPt.y,
                            s.x, s.y,
                            s.w, s.h
                    );
                    if (d2 <= r2) hit = true;
                } else {
                    int x1 = s.x;
                    int y1 = s.y;
                    int x2 = s.x + s.w;
                    int y2 = s.y + s.h;

                    int ex1 = (int)Math.floor(x1 - pickRadius);
                    int ey1 = (int)Math.floor(y1 - pickRadius);
                    int ex2 = (int)Math.ceil (x2 + pickRadius);
                    int ey2 = (int)Math.ceil (y2 + pickRadius);

                    if (screenPt.x >= ex1 && screenPt.x <= ex2 &&
                        screenPt.y >= ey1 && screenPt.y <= ey2) {
                        hit = true;
                    }
                }

                if (hit) {
                    return s;
                }
            }
            return null;
        }

        // 消しゴムで文字を消す（円判定）
        private void eraseTextAt(Point screenPt) {
            double r = eraserWidth / 2.0;
            double r2 = r * r;
            List<TextItem> toRemove = new ArrayList<>();
            for (TextItem t : texts) {
                double dx = t.screenPos.x - screenPt.x;
                double dy = t.screenPos.y - screenPt.y;
                if (dx * dx + dy * dy <= r2) {
                    toRemove.add(t);
                }
            }
            if (!toRemove.isEmpty()) {
                texts.removeAll(toRemove);
                needsRedraw = true;
            }
        }

        // 消しゴムで図形を消す（図形オブジェクト丸ごと）
        private void eraseShapeAt(Point screenPt) {
            double r = eraserWidth / 2.0;
            double r2 = r * r;
            List<ShapeItem> toRemove = new ArrayList<>();

            for (ShapeItem s : shapes) {
                boolean hit = false;

                if (s.type == ShapeType.LINE) {
                    // 線分と点の距離判定
                    double d2 = distancePointToSegmentSquared(
                            screenPt.x, screenPt.y,
                            s.x, s.y,   // start
                            s.w, s.h    // end
                    );
                    if (d2 <= r2) {
                        hit = true;
                    }
                } else {
                    // 四角・角丸四角・楕円は外接矩形 + r で判定
                    int x1 = s.x;
                    int y1 = s.y;
                    int x2 = s.x + s.w;
                    int y2 = s.y + s.h;

                    int ex1 = (int) Math.floor(x1 - r);
                    int ey1 = (int) Math.floor(y1 - r);
                    int ex2 = (int) Math.ceil (x2 + r);
                    int ey2 = (int) Math.ceil (y2 + r);

                    if (screenPt.x >= ex1 && screenPt.x <= ex2 &&
                        screenPt.y >= ey1 && screenPt.y <= ey2) {
                        hit = true;
                    }
                }

                if (hit) {
                    toRemove.add(s);
                }
            }

            if (!toRemove.isEmpty()) {
                shapes.removeAll(toRemove);
                needsRedraw = true;
            }
        }

        // 点と線分の距離^2（ユークリッド距離の2乗）を計算
        private double distancePointToSegmentSquared(
                double px, double py,
                double x1, double y1,
                double x2, double y2
        ) {
            double vx = x2 - x1;
            double vy = y2 - y1;
            double wx = px - x1;
            double wy = py - y1;

            double c1 = vx * wx + vy * wy;
            if (c1 <= 0) {
                // 端点1側が最近点
                double dx = px - x1;
                double dy = py - y1;
                return dx * dx + dy * dy;
            }

            double c2 = vx * vx + vy * vy;
            if (c2 <= c1) {
                // 端点2側が最近点
                double dx = px - x2;
                double dy = py - y2;
                return dx * dx + dy * dy;
            }

            // 線分内部の最近点
            double b = c1 / c2;
            double bx = x1 + b * vx;
            double by = y1 + b * vy;
            double dx = px - bx;
            double dy = py - by;
            return dx * dx + dy * dy;
        }

        private void ensureCanvas() {
            int w = Math.max(getWidth(), 1);
            int h = Math.max(getHeight(), 1);
            if (canvas == null || canvas.getWidth() != w || canvas.getHeight() != h) {
                canvas = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                needsRedraw = true;
            }
        }

        private void redrawCanvas() {
            if (canvas == null) return;

            Graphics2D cg = canvas.createGraphics();
            cg.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);

            // 全部透明に
            cg.setComposite(AlphaComposite.Clear);
            cg.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
            cg.setComposite(AlphaComposite.SrcOver);

            Point panelScreen;
            try {
                panelScreen = getLocationOnScreen();
            } catch (IllegalComponentStateException ex) {
                panelScreen = new Point(0, 0);
            }

            // 線（ペン・消しゴム）
            if (showStrokes) {
                for (StrokeData s : strokes) {
                    s.drawOnCanvas(cg, panelScreen);
                }
            }

            // 図形
            if (showShapes) {
                for (ShapeItem si : shapes) {
                    si.drawOnCanvas(cg, panelScreen);
                }
            }

            // 文字
            if (showTexts) {
                for (TextItem t : texts) {
                    Point rel = t.getRelativeTo(panelScreen);
                    cg.setFont(t.font);
                    cg.setColor(t.color);
                    cg.drawString(t.text, rel.x, rel.y);
                }
            }

            cg.dispose();
        }

        // ---- 白背景込み保存 ----
        public void saveWhiteBgPng(File file) throws IOException {
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;

            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2 = img.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);

            g2.setColor(Color.WHITE);
            g2.fillRect(0, 0, w, h);

            ensureCanvas();
            redrawCanvas();
            g2.drawImage(canvas, 0, 0, null);

            g2.dispose();
            ImageIO.write(img, "png", file);
        }

        // ---- 透過保存（線＋文字＋図形のみ）----
        public void saveTransparentPng(File file) throws IOException {
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;

            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = img.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);

            ensureCanvas();
            redrawCanvas();
            g2.drawImage(canvas, 0, 0, null);

            g2.dispose();
            ImageIO.write(img, "png", file);
        }

        @Override
        protected void paintComponent(Graphics g) {
            ensureCanvas();
            if (needsRedraw) {
                redrawCanvas();
                needsRedraw = false;
            }

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);

            if (bgMode == BackgroundMode.WHITE) {
                g2.setColor(Color.WHITE);
                g2.fillRect(0, 0, getWidth(), getHeight());
            }

            g2.drawImage(canvas, 0, 0, null);

            // 図形プレビュー（図形ツール中・新規描画時のみ）
            if (drawingShape && shapeStartScreen != null && shapeEndScreen != null &&
                    currentTool == Tool.SHAPE) {
                Point panelScreen;
                try {
                    panelScreen = getLocationOnScreen();
                } catch (IllegalComponentStateException ex) {
                    panelScreen = new Point(0, 0);
                }
                int sx = shapeStartScreen.x - panelScreen.x;
                int sy = shapeStartScreen.y - panelScreen.y;
                int exx = shapeEndScreen.x - panelScreen.x;
                int eyy = shapeEndScreen.y - panelScreen.y;

                g2.setStroke(new BasicStroke(
                        penWidth,
                        BasicStroke.CAP_ROUND,
                        BasicStroke.JOIN_ROUND
                ));
                int a = Math.max(10, Math.min(255, penAlpha));
                g2.setColor(new Color(
                        currentColor.getRed(),
                        currentColor.getGreen(),
                        currentColor.getBlue(),
                        a
                ));

                int x = Math.min(sx, exx);
                int y = Math.min(sy, eyy);
                int w = Math.abs(exx - sx);
                int h = Math.abs(eyy - sy);

                switch (currentShape) {
                    case LINE:
                        g2.drawLine(sx, sy, exx, eyy);
                        break;
                    case RECT:
                        g2.drawRect(x, y, w, h);
                        break;
                    case ROUND_RECT:
                        g2.draw(new RoundRectangle2D.Float(
                                x, y, w, h,
                                roundRectRadius * 2f,
                                roundRectRadius * 2f
                        ));
                        break;
                    case ELLIPSE:
                        g2.draw(new Ellipse2D.Float(x, y, w, h));
                        break;
                }
            }

            // レーザーポインタ（currentColor ベース）
            if (laserPointScreen != null) {
                Point panelScreen;
                try {
                    panelScreen = getLocationOnScreen();
                } catch (IllegalComponentStateException ex) {
                    panelScreen = new Point(0, 0);
                }
                Point rel = new Point(
                        laserPointScreen.x - panelScreen.x,
                        laserPointScreen.y - panelScreen.y
                );
                int r = laserSize;

                Color base = currentColor;
                Color center = new Color(base.getRed(), base.getGreen(), base.getBlue(), 220);
                Color edge   = new Color(base.getRed(), base.getGreen(), base.getBlue(), 0);

                RadialGradientPaint paint =
                        new RadialGradientPaint(
                                new Point(rel.x, rel.y),
                                r,
                                new float[]{0f, 1f},
                                new Color[]{center, edge}
                        );
                g2.setPaint(paint);
                g2.fillOval(rel.x - r, rel.y - r, 2 * r, 2 * r);
            }

            g2.dispose();
        }
    }

    // ==============================
    // 線データ（画面絶対座標）→キャンバスに描く
    // ==============================
    private static class StrokeData {
        Color baseColor;
        int   alpha;
        float width;
        boolean eraser;
        List<Point> ptsScreen = new ArrayList<>();

        StrokeData() {}

        StrokeData(StrokeData other) {
            this.baseColor = other.baseColor;
            this.alpha = other.alpha;
            this.width = other.width;
            this.eraser = other.eraser;
            for (Point p : other.ptsScreen) {
                this.ptsScreen.add(new Point(p));
            }
        }

        void addScreenPoint(Point screenPoint) {
            ptsScreen.add(new Point(screenPoint));
        }

        void drawOnCanvas(Graphics2D g, Point panelScreenOrigin) {
            if (ptsScreen.size() < 2) return;

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setStroke(new BasicStroke(
                    width,
                    BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND
            ));

            if (eraser) {
                g2.setComposite(AlphaComposite.Clear);
            } else {
                g2.setComposite(AlphaComposite.SrcOver);
                int a = Math.max(0, Math.min(255, alpha));
                g2.setColor(new Color(
                        baseColor.getRed(),
                        baseColor.getGreen(),
                        baseColor.getBlue(),
                        a
                ));
            }

            Point prevScreen = ptsScreen.get(0);
            for (int i = 1; i < ptsScreen.size(); i++) {
                Point curScreen = ptsScreen.get(i);
                int x1 = prevScreen.x - panelScreenOrigin.x;
                int y1 = prevScreen.y - panelScreenOrigin.y;
                int x2 = curScreen.x - panelScreenOrigin.x;
                int y2 = curScreen.y - panelScreenOrigin.y;
                g2.drawLine(x1, y1, x2, y2);
                prevScreen = curScreen;
            }

            g2.dispose();
        }
    }

    // ==============================
    // 図形データ（画面絶対座標）
    // ==============================
    private static class ShapeItem {
        ShapeType type;
        int x, y, w, h; // LINE のとき: (x,y) = start, (w,h) = end
        Color color;
        int alpha;
        float width;
        int cornerRadius; // 角丸四角用

        ShapeItem() {}
        ShapeItem(ShapeItem other) {
            this.type  = other.type;
            this.x     = other.x;
            this.y     = other.y;
            this.w     = other.w;
            this.h     = other.h;
            this.color = other.color;
            this.alpha = other.alpha;
            this.width = other.width;
            this.cornerRadius = other.cornerRadius;
        }

        void drawOnCanvas(Graphics2D g, Point panelScreenOrigin) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);

            int a = Math.max(0, Math.min(255, alpha));
            g2.setColor(new Color(
                    color.getRed(),
                    color.getGreen(),
                    color.getBlue(),
                    a
            ));
            g2.setStroke(new BasicStroke(
                    width,
                    BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND
            ));

            if (type == ShapeType.LINE) {
                int sx = x - panelScreenOrigin.x;
                int sy = y - panelScreenOrigin.y;
                int ex = w - panelScreenOrigin.x;
                int ey = h - panelScreenOrigin.y;
                g2.drawLine(sx, sy, ex, ey);
            } else {
                int rx = x - panelScreenOrigin.x;
                int ry = y - panelScreenOrigin.y;
                int rw = w;
                int rh = h;
                switch (type) {
                    case RECT:
                        g2.drawRect(rx, ry, rw, rh);
                        break;
                    case ROUND_RECT:
                        g2.draw(new RoundRectangle2D.Float(
                                rx, ry, rw, rh,
                                cornerRadius * 2f,
                                cornerRadius * 2f
                        ));
                        break;
                    case ELLIPSE:
                        g2.draw(new Ellipse2D.Float(rx, ry, rw, rh));
                        break;
                    default:
                        break;
                }
            }
            g2.dispose();
        }
    }

    // ==============================
    // 文字データ（画面絶対座標）
    // ==============================
    private static class TextItem {
        String text;
        Point  screenPos;
        Color  color;
        Font   font;

        TextItem() {}

        TextItem(TextItem other) {
            this.text = other.text;
            this.screenPos = new Point(other.screenPos);
            this.color = other.color;
            this.font = other.font; // Font は不変なので共有でOK
        }

        Point getRelativeTo(Point panelScreenOrigin) {
            return new Point(
                    screenPos.x - panelScreenOrigin.x,
                    screenPos.y - panelScreenOrigin.y
            );
        }
    }

    // ==============================
    // キャンバス状態（Undo/Redo 用）
    // ==============================
    private static class CanvasState {
        List<StrokeData> strokes;
        List<TextItem>   texts;
        List<ShapeItem>  shapes;
    }

    // ==============================
    // 全方向リサイズ用クラス
    // ==============================
    private class WindowResizer extends MouseAdapter {
        private final Window window;
        private static final int BORDER = 6;
        private Point startMouse;
        private Rectangle startBounds;
        private int dragDirection = 0;

        private static final int EAST  = 1;
        private static final int WEST  = 2;
        private static final int NORTH = 4;
        private static final int SOUTH = 8;

        WindowResizer(Window window) {
            this.window = window;
        }

        @Override
        public void mouseMoved(MouseEvent e) {
            Point winPt = SwingUtilities.convertPoint(
                    e.getComponent(), e.getPoint(), window);
            int dir = getDirection(winPt);
            Cursor c = getCursorForDirection(dir);
            window.setCursor(c);
        }

        @Override
        public void mousePressed(MouseEvent e) {
            Point winPt = SwingUtilities.convertPoint(
                    e.getComponent(), e.getPoint(), window);
            dragDirection = getDirection(winPt);
            startMouse = e.getLocationOnScreen();
            startBounds = window.getBounds();
            // リサイズ開始ならフラグ ON
            resizingActive = (dragDirection != 0);
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            if (dragDirection == 0) return;

            Point p = e.getLocationOnScreen();
            int dx = p.x - startMouse.x;
            int dy = p.y - startMouse.y;

            Rectangle nb = new Rectangle(startBounds);

            if ((dragDirection & EAST)  != 0) nb.width  += dx;
            if ((dragDirection & SOUTH) != 0) nb.height += dy;
            if ((dragDirection & WEST)  != 0) {
                nb.x     += dx;
                nb.width -= dx;
            }
            if ((dragDirection & NORTH) != 0) {
                nb.y      += dy;
                nb.height -= dy;
            }

            nb.width  = Math.max(nb.width,  200);
            nb.height = Math.max(nb.height, 150);

            window.setBounds(nb);
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            dragDirection = 0;
            resizingActive = false; // リサイズ終了
        }

        private int getDirection(Point pWin) {
            int x = pWin.x, y = pWin.y;
            int w = window.getWidth(), h = window.getHeight();
            int dir = 0;
            if (x < BORDER)          dir |= WEST;
            else if (x > w - BORDER) dir |= EAST;
            if (y < BORDER)          dir |= NORTH;
            else if (y > h - BORDER) dir |= SOUTH;
            return dir;
        }

        private Cursor getCursorForDirection(int dir) {
            switch (dir) {
                case EAST:           return Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR);
                case WEST:           return Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR);
                case NORTH:          return Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR);
                case SOUTH:          return Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR);
                case NORTH | EAST:   return Cursor.getPredefinedCursor(Cursor.NE_RESIZE_CURSOR);
                case NORTH | WEST:   return Cursor.getPredefinedCursor(Cursor.NW_RESIZE_CURSOR);
                case SOUTH | EAST:   return Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR);
                case SOUTH | WEST:   return Cursor.getPredefinedCursor(Cursor.SW_RESIZE_CURSOR);
                default:             return Cursor.getDefaultCursor();
            }
        }
    }
}
