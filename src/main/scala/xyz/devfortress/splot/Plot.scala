package xyz.devfortress.splot

import java.awt._

import scala.math.{max, min}

sealed trait PlotElement

final case class CompositePlotElement(plotElements: Seq[Either[Plot, Label[_]]]) extends PlotElement

trait Plot extends PlotElement with CommonSPlotLibTrait {
  /**
    * Domain, i.e. inclusive range along the x-axis.
    */
  def domain: (Double, Double)

  /**
    * Range, i.e. inclusive range along the y-axis.
    */
  def range: (Double, Double)

  /**
   * Domain predicate. Function that will use used to determine if particular point is within x-y domain.
   */
  def inDomain: (Double, Double) => Boolean

  def draw(ctx: DrawingContext, plotIndex: Int): Unit
}

object Plot {
  def makeStroke(lineWidth: Int, lineType: LineType): Stroke = {
    if (lineType == LineType.SOLID)
      new BasicStroke(lineWidth.toFloat)
    else
      new BasicStroke(
        lineWidth.toFloat, // width
        BasicStroke.CAP_ROUND, // cap
        BasicStroke.JOIN_ROUND, // join
        1, // miterLimit
        lineType.dashPattern.map(_ * lineWidth).toArray, // dash[]
        0 // dash_phase
      )
  }
}

trait LazyZMapPlot extends Plot {
  /**
    * Function that will be used to display color point for each pixel of the plot that falls into function domain.
    */
  def zFunction: (Double, Double) => Double

  /**
    * Range of Z value as returned by the zFunction. If there z-values that fall outside of this range their color will
    * be clipped.
    */
  def zRange: (Double, Double)

  /**
    * Color map to be used when displaying this heat-map like plot.
    * @return
    */
  def colorMap: Double => Color = colormaps.viridis
}

trait SimplePlot extends Plot {
  /**
   * @return Primary color for this plot
   */
  def color: Color

  /**
   * Sequence of points that defines this plot
   */
  def data: Seq[(Double, Double)]
}

abstract class PlotBase extends SimplePlot {
  override val domain: (Double, Double) =
    data.foldLeft((data.head._1, data.head._1))(
      (acc, v) => (min(acc._1, v._1), max(acc._2, v._1))
    )

  override val range: (Double, Double) =
    data.foldLeft((data.head._2, data.head._2))(
      (acc, v) => (min(acc._1, v._2), max(acc._2, v._2))
    )

  override def inDomain: (Double, Double) => Boolean =
    (x, y) => domain._1 <= x && x <= domain._2 && range._1 <= y && y <= range._2
}

/**
 * Plot shown as set of x-y points (i.e. a scatter plot).
 *
 * @param data sequence of data points.
 * @param pointSize point size. Must be greater than 0.
 * @param color color of the points.
 * @param pointType type of data points, i.e. how we display them as dots, crosses or something else.
 */
final case class PointPlot(
  data: Seq[(Double, Double)],
  pointSize: Int = 3,
  color: Color = Color.BLUE,
  pointType: PointType = PointType.Dot,
  fillColor: Option[Color] = None
) extends PlotBase {
  assert(pointSize > 0)

  override def draw(ctx: DrawingContext, plotIndex: Int): Unit = {
    import ctx._
    g2.setColor(color)
    g2.setStroke(new BasicStroke(1))
    val halfSize = pointSize / 2
    data.foreach(point => {
      pointType.drawAt((x2i(point._1), y2i(point._2)), pointSize, halfSize, fillColor, g2)
    })
  }
}

/**
 * ZPoint plot is very similar to PointPlot but also contains sequence of zValues that should be in range from 0 to 1
 * inclusive and color of each point is determined by by calling provided colorMap function which takes value from
 * zValues for a given point and returns color to be used for plotting this point.
 *
 * @param data sequence of data points.
 * @param zValues sequence of z-values for each point in "data" sequence (must be the same size as size of "data").
 * @param pointSize point size. Must be greater than 0.
 * @param colorMap color map function that transforms each z-value into color.
 * @param pointType type of data points, i.e. how we display them as dots, crosses or something else.
 */
final case class ZPointPlot(
  data: Seq[(Double, Double)],
  zValues: Seq[Double],
  pointSize: Int = 5,
  colorMap: Double => Color = colormaps.viridis,
  pointType: PointType = PointType.Dot
) extends PlotBase {
  assert(pointSize > 0)
  assert(data.size == zValues.size)

  /**
   * Primary color for this plot is ignored
   */
  override def color: Color = Color.BLACK

  override def draw(ctx: DrawingContext, plotIndex: Int): Unit = {
    import ctx._
    g2.setStroke(new BasicStroke(1))
    val halfSize = pointSize / 2
    data.zip(zValues).foreach(point => {
      val color = colorMap(point._2)
      g2.setColor(color)
      pointType.drawAt((x2i(point._1._1), y2i(point._1._2)), pointSize, halfSize, Some(color), g2)
    })
  }
}

/**
 * Sequence of points connected by line.
 *
 * @param data sequence of data points.
 * @param color color of the lines.
 * @param lineWidth width of the line in pixels. Must be greater than 0.
 * @param lineType type of line to draw, i.e. as dashes, dashes and dots, solid line (default) etc.
 */
final case class LinePlot(
  data: Seq[(Double, Double)],
  color: Color = Color.BLACK,
  lineWidth: Int = 1,
  lineType: LineType = LineType.SOLID
) extends PlotBase {
  assert(lineWidth > 0)
  private val xy = data.unzip
  private lazy val stroke = Plot.makeStroke(lineWidth, lineType)

  override def draw(ctx: DrawingContext, plotIndex: Int): Unit = {
    import ctx._

    g2.withColor(color)
      .withStroke(stroke)
      .draw(_.drawPolyline(xy._1.map(x => x2i(x)).toArray, xy._2.map(a => y2i(a)).toArray, xy._1.size))
  }
}

/**
 * Plot shown as a closed polygon shape.
 *
 * @param data sequence of data points that define vertices of this polygon.
 * @param color color of lines that are drawn at the polygon boundaries. Default value is BLUE.
 * @param lineWidth width of the lines drawn at the polygon boundaries. Must be greater or equals to 0. If lineWidth
 *                  is 0 then only interior of the polygon is drawn/shaded using value of fillColor.
 * @param fillColor color to use to shade interior of the polygon. If fillColor is None (default) then interior of the
 *                  polygon is not shaded.
 */
final case class Shape(
  override val data: Seq[(Double, Double)],
  override val color: Color = Color.BLACK,
  lineWidth: Int = 1,
  lineType: LineType = LineType.SOLID,
  fillColor: Option[Color] = None
) extends PlotBase {
  assert(lineWidth >= 0, "Line width might be greater or equals to 0")
  private lazy val stroke = Plot.makeStroke(lineWidth, lineType)

  override def draw(ctx: DrawingContext, plotIndex: Int): Unit = {
    import ctx._
    val onScreenXY = data.map(xy => (x2i(xy._1), y2i(xy._2))).unzip
    g2.withColor(color)
      .withStroke(stroke)
      .draw { g2 =>
        if (fillColor.isDefined) {
          g2.setColor(fillColor.get)
          g2.fillPolygon(onScreenXY._1.toArray, onScreenXY._2.toArray, data.size)
        }
        if (lineWidth > 0) {
          g2.setColor(color)
          g2.setStroke(stroke)
          g2.drawPolygon(onScreenXY._1.toArray, onScreenXY._2.toArray, data.size)
        }
      }
  }
}

/**
 * Simple rectangular heat map plot of certain zFunction that maps points in x,y plane into z-values.
 */
final case class ZMapPlot(
  zFunction: (Double, Double) => Double,
  override val domain: (Double, Double),
  override val range: (Double, Double),
  zRange: (Double, Double) = (0, 0),
  colorMap: Double => Color = colormaps.viridis,
  override val inDomain: (Double, Double) => Boolean
) extends Plot {
  private var cachedZRange = zRange

  override def draw(ctx: DrawingContext, plotIndex: Int): Unit = {
    import ctx._
    val iMax = imageWidth - rightPadding
    val jMax = imageHeight - bottomPadding
    val dx = (currentDomain._2 - currentDomain._1) / (iMax - leftPadding)
    val dy = (currentRange._2 - currentRange._1) / (jMax - topPadding)


    val realZRange = cachedZRange.synchronized {
      if (cachedZRange._1 != cachedZRange._2) {
        cachedZRange
      } else {
        // we will need to derive zRange
        val xRange = leftPadding to iMax
        val yRange = topPadding to jMax
        // TODO: Reuse "data" for plotting
        val data: Array[Array[Double]] = Array.ofDim[Double](xRange.size, yRange.size)
        var minZ = Double.MaxValue
        var maxZ = Double.MinValue
        for (i <- leftPadding to iMax; j <- topPadding to jMax) {
          val x = currentDomain._1 + (i - leftPadding) * dx
          val y = currentRange._2 - (j - topPadding) * dy
          if (inDomain(x, y)) {
            val z = zFunction(x, y)
            data(i - leftPadding)(j - topPadding) = z
            if (minZ > z) minZ = z
            if (maxZ < z) maxZ = z
          }
        }
        cachedZRange = (minZ, maxZ)
        cachedZRange
      }
    }

    val zLen = realZRange._2 - realZRange._1

    def toUnit(z: Double): Double = (z - realZRange._1) / zLen

    for (i <- leftPadding to iMax; j <- topPadding to jMax) {
      val x = currentDomain._1 + (i - leftPadding) * dx
      val y = currentRange._2 - (j - topPadding) * dy
      if (inDomain(x, y)) {
        val color = colorMap(toUnit(zFunction(x, y)))
        image.setRGB(i, j, color.getRGB)
      }
    }
  }
}

/**
 * Label to be drawn at the position (x,y) on the plot. Label is anchored at lower left corner of the bounding
 * box of the text.
 *
 * @param text text of the label to draw.
 * @param x x-coordinate at which to draw label.
 * @param y y-coordinate at which to draw label.
 * @param font font to use for this label. Default font it "Sans-Serif".
 * @param color color of text. Default is black.
 * @param anchor where text is to be anchored.
 */
case class Label[A : STextLike](
    text: A, x: Double, y: Double,
    font: Font = Font.decode(Font.SANS_SERIF),
    color: Color = Color.BLACK,
    anchor: Anchor = Anchor.LEFT_LOWER,
    fontSize: Option[Int] = None,
    angle: Double = 0) extends PlotElement {
  private val sText = implicitly[STextLike[A]].asSText(text);
  private val fontToDrawWith = fontSize.map(size => font.deriveFont(size.toFloat)).getOrElse(font)

  def draw(g2: Graphics2D, atPosition: (Int, Int)): Unit = {
    sText match {
      case ptext: PlainText => ptext.draw(g2, atPosition, anchor, color, angle, fontToDrawWith)
      case latex: LaTeXText => latex.draw(g2, atPosition, anchor, color, angle, fontToDrawWith.getSize)
    }
  }
}
