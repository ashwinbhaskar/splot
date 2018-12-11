package xyz.devfortress.splot

import java.awt.{BasicStroke, Color, Stroke}

object Grid {
  val DEFAULT_GRID_PLOTTER: (DrawingContext, Seq[Int], Seq[Int]) => Unit = gridPlotter()

  def gridPlotter(color: Color = new Color(0, 0, 0, 50), stroke: Stroke = new BasicStroke(1))
      (ctx: DrawingContext, xs: Seq[Int], ys: Seq[Int]): Unit = {
    import ctx._
    val savedStroke = g2.getStroke
    val savedColor = g2.getColor

    g2.setStroke(stroke)
    g2.setColor(color)
    val leftEdgeOfPlot = imageWidth - rightPadding

    val bottomEdgeOfPlot = imageHeight - bottomPadding
    // draw vertical grid
    for (x <- xs) {
      g2.drawLine(x, topPadding, x, bottomEdgeOfPlot)
    }

    // draw horizontal grid
    for (y <- ys) {
      g2.drawLine(leftPadding, y, leftEdgeOfPlot, y)
    }

    // restore original stroke and color
    g2.setColor(savedColor)
    g2.setStroke(savedStroke)
  }
}