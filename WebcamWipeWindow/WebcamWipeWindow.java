import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import javax.swing.*;

import com.github.sarxos.webcam.Webcam;

public class WebcamWipeWindow extends JFrame {

    // 形状の種類
    enum ShapeType {
        CIRCLE,
        FULL_RECT
    }

    private Webcam webcam;
    private WipePanel videoPanel;
    private ShapeType shapeType = ShapeType.FULL_RECT; // デフォルトは矩形

    public WebcamWipeWindow() {
        super("WebcamWipeWindow");

        // 枠なし＆透過ウィンドウ
        setUndecorated(true);
        setBackground(new Color(0, 0, 0, 0));
        ((JComponent) getContentPane()).setOpaque(false);

        // Webカメラ初期化
        webcam = Webcam.getDefault();
        if (webcam == null) {
            JOptionPane.showMessageDialog(null, "Webカメラが見つかりません。");
            System.exit(1);
        }
        webcam.open();

        // パネル作成
        videoPanel = new WipePanel(this, webcam);
        videoPanel.setPreferredSize(new Dimension(480, 360));
        videoPanel.setOpaque(false);

        setLayout(new BorderLayout());
        add(videoPanel, BorderLayout.CENTER);

        pack();
        setLocation(100, 100);

        applyShape();

        // リサイズしたら形も更新
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                applyShape();
            }
        });

        // 約30fpsで再描画
        Timer timer = new Timer(33, e -> videoPanel.repaint());
        timer.start();
    }

    ShapeType getShapeType() {
        return shapeType;
    }

    void setShapeType(ShapeType type) {
        this.shapeType = type;

        // 円のときはウィンドウもだいたい正方形に寄せる
        if (type == ShapeType.CIRCLE) {
            int size = Math.min(getWidth(), getHeight());
            if (size <= 0) size = 360;
            videoPanel.setPreferredSize(new Dimension(size, size));
            pack();
        }

        applyShape();
        videoPanel.repaint();
    }

    // ウィンドウ自体の形（丸 or 矩形）を更新
    void applyShape() {
        int w = getWidth();
        int h = getHeight();
        Shape s;

        switch (shapeType) {
            case CIRCLE: {
                int size = Math.min(w, h);      // 短辺に合わせた真円
                int x = (w - size) / 2;
                int y = (h - size) / 2;
                s = new Ellipse2D.Double(x, y, size, size);
                break;
            }
            case FULL_RECT:
            default:
                s = new Rectangle2D.Double(0, 0, w, h);
                break;
        }

        setShape(s);
    }

    // Webカメラ映像＋ドラッグ＆リサイズ＆メニュー
    private static class WipePanel extends JPanel {

        private final WebcamWipeWindow frame;
        private final Webcam webcam;

        private int pressX, pressY;

        private enum DragMode { MOVE, RESIZE_SE }
        private DragMode dragMode = DragMode.MOVE;
        private static final int RESIZE_MARGIN = 16;
        private static final int MIN_WIDTH = 200;
        private static final int MIN_HEIGHT = 150;

        WipePanel(WebcamWipeWindow frame, Webcam webcam) {
            this.frame = frame;
            this.webcam = webcam;

            setOpaque(false);

            // ドラッグで移動／右下でリサイズ
            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    pressX = e.getX();
                    pressY = e.getY();

                    if (frame.getShapeType() == ShapeType.FULL_RECT) {
                        int w = getWidth();
                        int h = getHeight();
                        boolean nearRight = (w - pressX) <= RESIZE_MARGIN;
                        boolean nearBottom = (h - pressY) <= RESIZE_MARGIN;
                        if (nearRight && nearBottom) {
                            dragMode = DragMode.RESIZE_SE;
                        } else {
                            dragMode = DragMode.MOVE;
                        }
                    } else {
                        dragMode = DragMode.MOVE;
                    }
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    Point p = e.getLocationOnScreen();
                    if (dragMode == DragMode.RESIZE_SE &&
                            frame.getShapeType() == ShapeType.FULL_RECT) {

                        int newW = Math.max(MIN_WIDTH, p.x - frame.getX());
                        int newH = Math.max(MIN_HEIGHT, p.y - frame.getY());
                        frame.setSize(newW, newH);
                        frame.validate();
                        frame.applyShape();
                    } else {
                        frame.setLocation(p.x - pressX, p.y - pressY);
                    }
                }

                @Override
                public void mouseMoved(MouseEvent e) {
                    if (frame.getShapeType() == ShapeType.FULL_RECT) {
                        int w = getWidth();
                        int h = getHeight();
                        int x = e.getX();
                        int y = e.getY();
                        boolean nearRight = (w - x) <= RESIZE_MARGIN;
                        boolean nearBottom = (h - y) <= RESIZE_MARGIN;
                        if (nearRight && nearBottom) {
                            setCursor(Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR));
                        } else {
                            setCursor(Cursor.getDefaultCursor());
                        }
                    } else {
                        setCursor(Cursor.getDefaultCursor());
                    }
                }
            };

            addMouseListener(mouse);
            addMouseMotionListener(mouse);

            // 右クリックメニュー
            JPopupMenu menu = new JPopupMenu();

            // ★ タイトルパネル（青文字・太字・背景薄グレー・ver.1.0）
            JPanel header = new JPanel(new BorderLayout());
            header.setBackground(new Color(230, 230, 230));
            header.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));

            JLabel headerLabel = new JLabel(
                    "<html><b><span style='color:blue;'>WebcamWipeWindow ver.1.0</span><br>"
                            + "Copyright fumiaki masakiyo</b></html>");
            headerLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
            header.add(headerLabel, BorderLayout.CENTER);

            menu.add(header);
            menu.addSeparator();

            // サイズプリセット
            JMenuItem small = new JMenuItem("Small (240x180)");
            small.addActionListener(e -> frame.setSize(240, 180));
            menu.add(small);

            JMenuItem medium = new JMenuItem("Medium (360x270)");
            medium.addActionListener(e -> frame.setSize(360, 270));
            menu.add(medium);

            JMenuItem large = new JMenuItem("Large (480x360)");
            large.addActionListener(e -> frame.setSize(480, 360));
            menu.add(large);

            menu.addSeparator();

            // 形状切り替え
            JMenuItem fullRectItem = new JMenuItem("Shape: Full Rect");
            fullRectItem.addActionListener(e -> frame.setShapeType(ShapeType.FULL_RECT));
            menu.add(fullRectItem);

            JMenuItem circleItem = new JMenuItem("Shape: Circle");
            circleItem.addActionListener(e -> frame.setShapeType(ShapeType.CIRCLE));
            menu.add(circleItem);

            menu.addSeparator();

            // 最前面 ON/OFF
            JMenuItem topOn = new JMenuItem("Always on top: ON");
            topOn.addActionListener(e -> frame.setAlwaysOnTop(true));
            menu.add(topOn);

            JMenuItem topOff = new JMenuItem("Always on top: OFF");
            topOff.addActionListener(e -> frame.setAlwaysOnTop(false));
            menu.add(topOff);

            menu.addSeparator();

            JMenuItem exitItem = new JMenuItem("Exit");
            exitItem.addActionListener(e -> {
                if (webcam != null && webcam.isOpen()) {
                    webcam.close();
                }
                System.exit(0);
            });
            menu.add(exitItem);

            // ポップアップ表示
            addMouseListener(new MouseAdapter() {
                private void tryPopup(MouseEvent e) {
                    if (e.isPopupTrigger()) {
                        menu.show(e.getComponent(), e.getX(), e.getY());
                    }
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    tryPopup(e);
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    tryPopup(e);
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            if (webcam == null || !webcam.isOpen()) return;
            Image img = webcam.getImage();
            if (img == null) return;

            int w = getWidth();
            int h = getHeight();

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);

            // クリップ形状（円は真円）
            Shape clip;
            if (frame.getShapeType() == ShapeType.CIRCLE) {
                int size = Math.min(w, h);
                int x = (w - size) / 2;
                int y = (h - size) / 2;
                clip = new Ellipse2D.Double(x, y, size, size);
            } else {
                clip = new Rectangle2D.Double(0, 0, w, h);
            }
            g2.setClip(clip);

            int imgW = img.getWidth(null);
            int imgH = img.getHeight(null);
            if (imgW > 0 && imgH > 0) {
                // アスペクト比を保ったまま、パネル全体を覆うよう拡大
                double scale = Math.max((double) w / imgW, (double) h / imgH);
                int drawW = (int) (imgW * scale);
                int drawH = (int) (imgH * scale);
                int dx = (w - drawW) / 2;
                int dy = (h - drawH) / 2;
                g2.drawImage(img, dx, dy, drawW, drawH, null);
            }

            g2.dispose();
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            WebcamWipeWindow w = new WebcamWipeWindow();
            w.setVisible(true);
        });
    }
}

