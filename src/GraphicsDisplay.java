import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.font.FontRenderContext;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import javax.swing.*;

public class GraphicsDisplay extends JPanel {
    private Double[][] graphicsData;

    private boolean showAxis = true;
    private boolean showMarkers = true;

    // Границы диапазона пространства, подлежащего отображению
    private double minX;
    private double maxX;
    private double minY;
    private double maxY;
    // Используемый масштаб отображения
    private double scale;

    // Различные стили черчения линий
    private BasicStroke graphicsStroke;
    private BasicStroke axisStroke;
    private BasicStroke markerStroke;

    private final Cursor defaultCursor;
    private final Cursor pointCursor;

    // Различные шрифты отображения надписей
    private Font axisFont;
    private ArrayList<Range> ranges;

    private boolean selection = false;
    private Double[] chosenPoint = null;
    private boolean changingRange = false;
    private Point startRangePoint = null;
    private Point finishRangePoint = null;
    private BasicStroke rangeStroke;

    private final DecimalFormat formatter;
    private final Font coordinatesFont;

    private Range pointsToRange(Point p1, Point p2) {
        Double[] xy1 = pointToXY(p1);
        Double[] xy2 = pointToXY(p2);

        return new Range(
                Math.min(xy1[0], xy2[0]),
                Math.max(xy1[0], xy2[0]),
                Math.min(xy1[1], xy2[1]),
                Math.max(xy1[1], xy2[1]));
    }

    private static class Range {
        public Double minX;
        public Double maxX;
        public Double minY;
        public Double maxY;

        public Range(Double minX, Double maxX, Double minY, Double maxY) {
            this.minX = minX;
            this.maxX = maxX;
            this.minY = minY;
            this.maxY = maxY;
        }
    }

    public GraphicsDisplay() {
        addMouseMotionListener(new TMouseMotionListener());
        addMouseListener(new TMouseAdapter());
        setBackground(Color.WHITE);
        // Сконструировать необходимые объекты, используемые в рисовании
        // Перо для рисования графика
        formatter = (DecimalFormat) NumberFormat.getInstance();
        formatter.setMaximumFractionDigits(2);
        formatter.setGroupingUsed(false);
        float[] graphDash = new float[]{2, 3, 4, 3, 2, 3, 8, 3, 4, 3, 2};
        graphicsStroke = new BasicStroke(2.0f, BasicStroke.CAP_BUTT,
                BasicStroke.JOIN_ROUND, 10.0f, graphDash, 0.0f);
        // Перо для рисования осей координат
        rangeStroke = new BasicStroke(2.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10.0f,
                new float[]{2, 1, 3, 1}, 0.0f);
        axisStroke = new BasicStroke(2.0f, BasicStroke.CAP_BUTT,
                BasicStroke.JOIN_MITER, 10.0f, null, 0.0f);
        // Перо для рисования контуров маркеров
        markerStroke = new BasicStroke(1.0f, BasicStroke.CAP_BUTT,
                BasicStroke.JOIN_MITER, 10.0f, null, 0.0f);
        // Шрифт для подписей осей координат
        axisFont = new Font("Serif", Font.BOLD, 36);
        coordinatesFont = new Font("Serif", Font.BOLD, 15);
        defaultCursor = getCursor();
        pointCursor = new Cursor(Cursor.HAND_CURSOR);
    }

    // Данный метод вызывается из обработчика элемента меню "Открыть файл с графиком"
    // главного окна приложения в случае успешной загрузки данных
    public void showGraphics(Double[][] graphicsData) {
        this.graphicsData = graphicsData;
        ranges = new ArrayList<>();
        // Запросить перерисовку компонента, т.е. неявно вызвать paintComponent();
        Iterator<Double> xit = new Iterator<Double>() {
            private int i = 0;

            @Override
            public boolean hasNext() {
                return i < graphicsData.length;
            }

            @Override
            public Double next() {
                return graphicsData[i++][0];
            }
        };
        Iterator<Double> yit = new Iterator<Double>() {
            private int i = 0;

            @Override
            public boolean hasNext() {
                return i < graphicsData.length;
            }

            @Override
            public Double next() {
                return graphicsData[i++][1];
            }
        };
        double minX = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        while (xit.hasNext()) {
            double next = xit.next();
            if (Double.compare(next, minX) < 0) {
                minX = next;
            }
            if (Double.compare(next, maxX) > 0) {
                maxX = next;
            }
        }

        while (yit.hasNext()) {
            double next = yit.next();
            if (Double.compare(next, minY) < 0) {
                minY = next;
            }
            if (Double.compare(next, maxY) > 0) {
                maxY = next;
            }
        }
        ranges.add(new Range(minX, maxX, minY, maxY));
        repaint();
    }

    public void setShowAxis(boolean showAxis) {
        this.showAxis = showAxis;
        repaint();
    }

    public void setShowMarkers(boolean showMarkers) {
        this.showMarkers = showMarkers;
        repaint();
    }

    public void paintComponent(Graphics g) {
        /* Шаг 1 - Вызвать метод предка для заливки области цветом заднего фона
         * Эта функциональность - единственное, что осталось в наследство от
         * paintComponent класса JPanel
         */
        super.paintComponent(g);

        // Шаг 2 - Если данные графика не загружены (при показе компонента при запуске программы) - ничего не делать
        if (graphicsData == null || graphicsData.length == 0) {
            return;
        }
        // Шаг 3 - Определить минимальное и максимальное значения для координат X и Y
        Range r = ranges.get(ranges.size() - 1);
        maxX = r.maxX;
        minX = r.minX;
        maxY = r.maxY;
        minY = r.minY;
        /* Шаг 4 - Определить (исходя из размеров окна) масштабы по осям X
        и Y - сколько пикселов
        * приходится на единицу длины по X и по Y
        */
        double scaleX = getSize().getWidth() / (maxX - minX);
        double scaleY = getSize().getHeight() / (maxY - minY);
        // Шаг 5 - Чтобы изображение было неискажѐнным - масштаб должен быть одинаков
        // Выбираем за основу минимальный
        scale = Math.min(scaleX, scaleY);
        // Шаг 6 - корректировка границ отображаемой области согласно выбранному масштабу
        if (scale == scaleX) {
            /* Если за основу был взят масштаб по оси X, значит по оси Y
            делений меньше, * т.е. подлежащий визуализации диапазон по Y будет меньше
            высоты окна. * Значит необходимо добавить делений, сделаем это так:
            * 1) Вычислим, сколько делений влезет по Y при выбранном
            масштабе - getSize().getHeight()/scale * 2)
            Вычтем из этого сколько делений требовалось изначально
            * 3) Набросим по половине недостающего расстояния на maxY и minY
            */
            double yIncrement = (getSize().getHeight() / scale - (maxY - minY)) / 2;
            maxY += yIncrement;
            minY -= yIncrement;
        }
        if (scale == scaleY) {
            // Если за основу был взят масштаб по оси Y, действовать по аналогии
            double xIncrement = (getSize().getWidth() / scale - (maxX -
                    minX)) / 2;
            maxX += xIncrement;
            minX -= xIncrement;
        }
        // Шаг 7 - Сохранить текущие настройки холста
        Graphics2D canvas = (Graphics2D) g;
        Stroke oldStroke = canvas.getStroke();
        Color oldColor = canvas.getColor();
        Paint oldPaint = canvas.getPaint();
        Font oldFont = canvas.getFont();
        // Шаг 8 - В нужном порядке вызвать методы отображения элементов графика
        // Порядок вызова методов имеет значение, т.к. предыдущий рисунок будет затираться последующим
        // Первыми (если нужно) отрисовываются оси координат.
        if (showAxis) {
            paintAxis(canvas);
        }
        paintGraphics(canvas);

        if (showMarkers) {
            paintMarkers(canvas);
        }
        if (chosenPoint != null) {
            Point2D.Double point = xyToPoint(chosenPoint[0], chosenPoint[1]);
            float pointX = (float) point.x;
            float pointY = (float) point.y;
            canvas.setFont(coordinatesFont);
            canvas.setColor(Color.BLACK);
            String coordinatesString =
                    "X: " + formatter.format(chosenPoint[0]) + " Y: " + formatter.format(chosenPoint[1]);

            canvas.drawString(coordinatesString, pointX + 5, pointY - 8);
        }
        if (changingRange) {
            canvas.setColor(Color.BLACK);
            canvas.setStroke(rangeStroke);
            canvas.draw(new Rectangle2D.Double(
                    startRangePoint.x,
                    startRangePoint.y,
                    finishRangePoint.x - startRangePoint.x,
                    finishRangePoint.y - startRangePoint.y));
        }
        // Шаг 9 - Восстановить старые настройки холста
        canvas.setFont(oldFont);
        canvas.setPaint(oldPaint);
        canvas.setColor(oldColor);
        canvas.setStroke(oldStroke);
    }

    private void paintGraphics(Graphics2D canvas) {
        // Выбрать линию для рисования графика
        canvas.setStroke(graphicsStroke);
        // Выбрать цвет линии
        canvas.setColor(Color.RED);
        /* Будем рисовать линию графика как путь, состоящий из множества
        сегментов (GeneralPath)
        * Начало пути устанавливается в первую точку графика, после чего
        прямой соединяется со
        * следующими точками */
        GeneralPath graphics = new GeneralPath();
        for (int i = 0; i < graphicsData.length; i++) {
            // Преобразовать значения (x,y) в точку на экране point
            Point2D.Double point = xyToPoint(graphicsData[i][0], graphicsData[i][1]);
            if (i > 0) {
                // Не первая итерация цикла - вести линию в точку point
                graphics.lineTo(point.getX(), point.getY());
            } else {
                // Первая итерация цикла - установить начало пути в точку point
                graphics.moveTo(point.getX(), point.getY());
            }
        }

        // Отобразить график
        canvas.draw(graphics);
    }

    protected void paintMarkers(Graphics2D canvas) {
        // Шаг 1 - Установить специальное перо для черчения контуров маркеров
        Color highlightColor = Color.blue;
        Color chosenColor = Color.RED;
        canvas.setStroke(markerStroke);
        for (Double[] point : graphicsData) {
            int funcValue = (int) (double) point[0];
            if (funcValue % 2 == 0) {
                canvas.setColor(highlightColor);
                canvas.setPaint(highlightColor);
            } else {
                canvas.setColor(chosenColor);
                canvas.setPaint(chosenColor);
            }
            Point2D.Double center = xyToPoint(point[0], point[1]);

            List<Point2D.Double> points = Arrays.asList(shiftPoint(center, -1, 5),
                    shiftPoint(center, 1, 5), shiftPoint(center, 1, 2),
                    shiftPoint(center, 2, 2), shiftPoint(center, 2, 1),
                    shiftPoint(center, 5, 1), shiftPoint(center, 5, -1),
                    shiftPoint(center, 2, -1), shiftPoint(center, 2, -2),
                    shiftPoint(center, 1, -2), shiftPoint(center, 1, -5),
                    shiftPoint(center, -1, -5), shiftPoint(center, -1, -2),
                    shiftPoint(center, -2, -2), shiftPoint(center, -2, -1),
                    shiftPoint(center, -5, -1), shiftPoint(center, -5, 1),
                    shiftPoint(center, -2, 1), shiftPoint(center, -2, 2),
                    shiftPoint(center, -1, 2), shiftPoint(center, -1, 5),
                    shiftPoint(center, 1, 5));
            for (int i = 0; i < points.size() - 1; i++) {
                canvas.draw(new Line2D.Double(points.get(i), points.get(i + 1)));
            }
        }
    }

    // Метод, обеспечивающий отображение осей координат
    protected void paintAxis(Graphics2D canvas) {
        // Установить особое начертание для осей
        canvas.setStroke(axisStroke);
        // Оси рисуются чѐрным цветом
        canvas.setColor(Color.BLACK);
        // Стрелки заливаются чѐрным цветом
        canvas.setPaint(Color.BLACK);
        // Подписи к координатным осям делаются специальным шрифтом
        canvas.setFont(axisFont);
        // Создать объект контекста отображения текста - для получения характеристик устройства (экрана)
        FontRenderContext context = canvas.getFontRenderContext();
        // Определить, должна ли быть видна ось Y на графике
        if (minX <= 0.0 && maxX >= 0.0) {
            canvas.draw(new Line2D.Double(xyToPoint(0, maxY), xyToPoint(0, minY)));
            // Стрелка оси Y
            GeneralPath arrow = new GeneralPath();
            // Установить начальную точку ломаной точно на верхний конец оси Y
            Point2D.Double lineEnd = xyToPoint(0, maxY);
            arrow.moveTo(lineEnd.getX(), lineEnd.getY());
            // Вести левый "скат" стрелки в точку с относительными координатами (5,20)
            arrow.lineTo(arrow.getCurrentPoint().getX() + 5, arrow.getCurrentPoint().getY() + 20);
            // Вести нижнюю часть стрелки в точку с относительными координатами (-10, 0)
            arrow.lineTo(arrow.getCurrentPoint().getX() - 10, arrow.getCurrentPoint().getY());
            // Замкнуть треугольник стрелки
            arrow.closePath();
            canvas.draw(arrow); // Нарисовать стрелку
            canvas.fill(arrow); // Закрасить стрелку
            // Нарисовать подпись к оси Y
            // Определить, сколько места понадобится для надписи "y"
            Rectangle2D bounds = axisFont.getStringBounds("y", context);
            Point2D.Double labelPos = xyToPoint(0, maxY);
            // Вывести надпись в точке с вычисленными координатами
            canvas.drawString("y", (float) labelPos.getX() + 10,
                    (float) (labelPos.getY() - bounds.getY()));
        }
        if (minY <= 0.0 && maxY >= 0.0) {
            // Она должна быть видна, если верхняя граница показываемой области(maxX) >= 0.0,
            // а нижняя (minY) <= 0.0
            canvas.draw(new Line2D.Double(xyToPoint(minX, 0),
                    xyToPoint(maxX, 0)));
            // Стрелка оси X
            GeneralPath arrow = new GeneralPath();
            // Установить начальную точку ломаной точно на правый конец оси X
            Point2D.Double lineEnd = xyToPoint(maxX, 0);
            arrow.moveTo(lineEnd.getX(), lineEnd.getY());
            // Вести верхний "скат" стрелки в точку с относительными координатами (-20,-5)
            arrow.lineTo(arrow.getCurrentPoint().getX() - 20,
                    arrow.getCurrentPoint().getY() - 5);
            // Вести левую часть стрелки в точку с относительными координатами (0, 10)
            arrow.lineTo(arrow.getCurrentPoint().getX(),
                    arrow.getCurrentPoint().getY() + 10);
            // Замкнуть треугольник стрелки
            arrow.closePath();
            canvas.draw(arrow); // Нарисовать стрелку
            canvas.fill(arrow); // Закрасить стрелку
            // Нарисовать подпись к оси X
            // Определить, сколько места понадобится для надписи "x"
            Rectangle2D bounds = axisFont.getStringBounds("x", context);
            Point2D.Double labelPos = xyToPoint(maxX, 0);
            // Вывести надпись в точке с вычисленными координатами
            canvas.drawString("x", (float) (labelPos.getX() -
                    bounds.getWidth() - 10), (float) (labelPos.getY() + bounds.getY()));
        }
    }

    /* Метод-помощник, осуществляющий преобразование координат.
    * Оно необходимо, т.к. верхнему левому углу холста с координатами
    * (0.0, 0.0) соответствует точка графика с координатами (minX, maxY),
    где
    * minX - это самое "левое" значение X, а
    * maxY - самое "верхнее" значение Y.
    */
    protected Point2D.Double xyToPoint(double x, double y) {
        // Вычисляем смещение X от самой левой точки (minX)
        double deltaX = x - minX;
        // Вычисляем смещение Y от точки верхней точки (maxY)
        double deltaY = maxY - y;
        return new Point2D.Double(deltaX * scale, deltaY * scale);
    }

    /* Метод-помощник, возвращающий экземпляр класса Point2D.Double
     * смещѐнный по отношению к исходному на deltaX, deltaY
     * К сожалению, стандартного метода, выполняющего такую задачу, нет.
     */
    private Point2D.Double shiftPoint(Point2D.Double src, double deltaX, double deltaY) {
        // Инициализировать новый экземпляр точки
        Point2D.Double dest = new Point2D.Double();
        // Задать еѐ координаты как координаты существующей точки заданные смещения
        dest.setLocation(src.getX() + deltaX, src.getY() + deltaY);
        return dest;
    }

    public Double[][] getGraphicsData() {
        return graphicsData;
    }

    private Double[] pointToXY(Point p) {
        return new Double[]{p.x / scale + minX, maxY - p.y / scale};
    }

    private class TMouseAdapter extends MouseAdapter {
        @Override
        public void mousePressed(MouseEvent ev) {
            if (SwingUtilities.isLeftMouseButton(ev) && chosenPoint != null) {
                selection = true;
            } else if (ranges != null && SwingUtilities.isLeftMouseButton(ev)) {
                changingRange = true;
                startRangePoint = ev.getPoint();
                finishRangePoint = startRangePoint;
            }
            repaint();
        }

        @Override
        public void mouseReleased(MouseEvent ev) {
            if (SwingUtilities.isLeftMouseButton(ev)) {
                selection = false;
                changingRange = false;
                if (ranges != null) {
                    finishRangePoint = ev.getPoint();
                    ranges.add(pointsToRange(startRangePoint, finishRangePoint));
                }
            }
            repaint();
        }

        @Override
        public void mouseClicked(MouseEvent ev) {
            if (SwingUtilities.isRightMouseButton(ev)) {
                if (ranges != null && ranges.size() != 1) {
                    ranges.remove(ranges.size() - 1);
                }
            }
            repaint();
        }
    }

    private class TMouseMotionListener implements MouseMotionListener {
        @Override
        public void mouseDragged(MouseEvent e) {
            if (selection) {
                Double[] xy = pointToXY(e.getPoint());
                chosenPoint[0] = xy[0];
                chosenPoint[1] = xy[1];
            }
            if (changingRange) {
                finishRangePoint = e.getPoint();
            }
            repaint();
        }

        @Override
        public void mouseMoved(MouseEvent e) {
            setCursor(defaultCursor);
            chosenPoint = null;
            int mouseX = e.getX();
            int mouseY = e.getY();
            int pointX;
            int pointY;
            if (graphicsData != null) {
                for (Double[] xy : graphicsData) {
                    Point2D.Double point = xyToPoint(xy[0], xy[1]);
                    pointX = (int) point.x;
                    pointY = (int) point.y;
                    if (Math.abs(mouseX - pointX) <= 5 && Math.abs(mouseY - pointY) <= 5) {
                        chosenPoint = xy;
                        setCursor(pointCursor);
                        break;
                    }
                }
            }
            repaint();
        }
    }
}
