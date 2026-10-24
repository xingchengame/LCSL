/*
 * LuCUNserver Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
 * Copyright (C) 2025-2026 LuCunServer
 * Not affiliated with Mojang Studios or Microsoft.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.ui.main;

import javafx.geometry.HPos;
import javafx.geometry.VPos;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Line;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.PathElement;
import org.glavo.monetfx.ColorRole;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.List;

import static org.jackhuang.hmcl.theme.Themes.getColorScheme;

/// The curve of the delays the server answered with during the last hour.
///
/// It is drawn the way the website draws it: the measured delays as a line, a soft area under it
/// and a dashed line where the delay gets too long.
@NotNullByDefault
public final class GuardSparkline extends Region {

    /// The color of the line, one of `ok`, `warn`, `danger` and `idle`.
    private String tone = "idle";

    /// The delay the website warns about, or a negative number when it did not send one.
    private double warnValue = -1;

    /// The measured delays, oldest first.
    private @Unmodifiable List<Double> values = List.of();

    private final Path area = new Path();
    private final Path line = new Path();
    private final Line threshold = new Line();

    public GuardSparkline() {
        getStyleClass().add("guard-spark");

        area.getStyleClass().add("spark-area");
        line.getStyleClass().add("spark-line");
        threshold.getStyleClass().add("spark-thr");

        area.setFill(Color.TRANSPARENT);
        line.setFill(Color.TRANSPARENT);
        threshold.setStrokeWidth(1);
        threshold.setVisible(false);

        getChildren().addAll(area, line, threshold);
    }

    /// Sets the delays the curve shows.
    ///
    /// @param values the measured delays, oldest first
    public void setValues(@Unmodifiable List<Double> values) {
        this.values = values;
        requestLayout();
    }

    /// Sets the delay the website warns about.
    ///
    /// @param warnValue the delay in milliseconds, or a negative number when there is none
    public void setWarnValue(double warnValue) {
        this.warnValue = warnValue;
        requestLayout();
    }

    /// Sets the color of the line.
    ///
    /// @param tone one of `ok`, `warn`, `danger` and `idle`
    public void setTone(String tone) {
        this.tone = tone;
        requestLayout();
    }

    @Override
    protected void layoutChildren() {
        double width = getWidth();
        double height = getHeight();
        if (width <= 0 || height <= 0)
            return;

        for (var child : getChildren())
            layoutInArea(child, 0, 0, width, height, 0, HPos.LEFT, VPos.TOP);

        area.getElements().clear();
        line.getElements().clear();

        if (values.size() < 2) {
            threshold.setVisible(false);
            return;
        }

        double max = Math.max(60, values.stream().mapToDouble(Double::doubleValue).max().orElse(60));
        double step = width / (values.size() - 1);

        List<PathElement> areaElements = new ArrayList<>();
        List<PathElement> lineElements = new ArrayList<>();

        double first = y(values.get(0), max, height);
        areaElements.add(new MoveTo(0, height));
        areaElements.add(new LineTo(0, first));
        lineElements.add(new MoveTo(0, first));

        for (int i = 1; i < values.size(); i++) {
            double x = i * step;
            double y = y(values.get(i), max, height);
            areaElements.add(new LineTo(x, y));
            lineElements.add(new LineTo(x, y));
        }

        areaElements.add(new LineTo(width, height));
        area.getElements().setAll(areaElements);
        line.getElements().setAll(lineElements);

        if (warnValue > 0) {
            double y = y(warnValue, max, height);
            threshold.setStartX(0);
            threshold.setStartY(y);
            threshold.setEndX(width);
            threshold.setEndY(y);
            threshold.setVisible(true);
        } else {
            threshold.setVisible(false);
        }

        Color color = toneColor();
        area.setFill(new LinearGradient(0, 0, 0, height, false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.color(color.getRed(), color.getGreen(), color.getBlue(), 0.30)),
                new Stop(1, Color.color(color.getRed(), color.getGreen(), color.getBlue(), 0))));
        line.setStroke(color);
        threshold.setStroke(color);
    }

    /// Puts a delay on the vertical axis, the way the website does.
    ///
    /// @param value the delay in milliseconds
    /// @param max the largest delay of the curve
    /// @param height the height of the curve
    /// @return the distance from the top of the curve
    private static double y(double value, double max, double height) {
        return height * (0.96 - 0.92 * value / max);
    }

    /// Returns the color of the tone the card is in.
    private Color toneColor() {
        ColorRole role = switch (tone) {
            case "ok" -> ColorRole.TERTIARY;
            case "warn" -> ColorRole.SECONDARY;
            case "danger" -> ColorRole.ERROR;
            default -> ColorRole.ON_SURFACE_VARIANT;
        };
        return getColorScheme().getColor(role);
    }
}
