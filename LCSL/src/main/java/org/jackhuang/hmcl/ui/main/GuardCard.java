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

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.util.Duration;
import org.jackhuang.hmcl.server.LuCunAPI;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.theme.Themes;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// The card that tells how the survival server is doing right now.
///
/// It is the card of the website: the emblem of the guard, the four numbers that matter, the
/// curve of the delays of the last hour and the names of the players who are in. Everything on it
/// is read from the website, which measures the server itself, so the numbers are the real ones.
///
/// The card works whether the player is signed in or not.
@NotNullByDefault
public final class GuardCard extends VBox {

    /// The label of the emblem.
    private final Label nameLabel = new Label("LCS-G");

    /// The line under the name of the emblem.
    private final Label subLabel = new Label(i18n("guard.sub"));

    /// The word in the pill at the top right, telling how the server is doing.
    private final Label pillLabel = new Label(i18n("guard.connecting"));

    /// The dot in the pill.
    private final Circle pillDot = new Circle(3.5);

    /// The number of the players who are in.
    private final Label playersValue = new Label("--");

    /// The limit of the players, right after the number.
    private final Label playersMax = new Label("");

    /// The delay the server answered with.
    private final Label latencyValue = new Label("--");

    /// The unit of the delay.
    private final Label latencyUnit = new Label("ms");

    /// The ticks per second the server keeps up with.
    private final Label tpsValue = new Label("--");

    /// The number of connections the guard turned away today.
    private final Label interceptsValue = new Label("--");

    /// The word in the badge under the curve.
    private final Label statusLabel = new Label(i18n("guard.connecting"));

    /// The version of the server, next to the badge.
    private final Label versionLabel = new Label("--");

    /// The motto of the server, one line for every line it has.
    private final VBox motdBox = new VBox();

    /// The names of the players who are in.
    private final FlowPane playersPane = new FlowPane();

    /// The curve of the delays of the last hour.
    private final GuardSparkline sparkline = new GuardSparkline();

    /// The word shown on the curve while there is nothing to draw yet.
    private final Label sparkEmptyLabel = new Label(i18n("guard.spark.empty"));

    /// The ring that turns around the emblem.
    private final Circle ring = new Circle(20);

    /// The plain ring the turning one is drawn over.
    private final Circle ringBase = new Circle(20);

    /// Whether a load is on its way.
    private boolean loading;

    public GuardCard() {
        getStyleClass().add("guard-card");
        setPadding(new Insets(16, 16, 14, 16));
        setSpacing(12);

        var emblem = new StackPane();
        emblem.getStyleClass().add("guard-emblem");
        ringBase.getStyleClass().add("guard-ring-base");
        ring.getStyleClass().add("guard-ring-spin");
        ring.getStrokeDashArray().setAll(34.0, 100.0);
        emblem.getChildren().setAll(ringBase, ring, SVG.VERIFIED_USER.createIcon(22));

        var id = new VBox();
        id.getStyleClass().add("guard-id");
        id.setSpacing(1);
        nameLabel.getStyleClass().add("guard-name");
        nameLabel.setMinWidth(Region.USE_PREF_SIZE);
        subLabel.getStyleClass().add("guard-sub");
        subLabel.setMinWidth(0);
        id.getChildren().setAll(nameLabel, subLabel);

        var pill = new HBox();
        pill.getStyleClass().add("guard-pill");
        pill.setAlignment(Pos.CENTER);
        pill.setMinWidth(Region.USE_PREF_SIZE);
        pillDot.getStyleClass().add("guard-dot");
        pill.getChildren().setAll(pillDot, pillLabel);

        var head = new HBox();
        head.getStyleClass().add("guard-head");
        head.setAlignment(Pos.CENTER_LEFT);
        head.getChildren().setAll(emblem, id, pill);
        HBox.setHgrow(id, Priority.ALWAYS);

        var metrics = new HBox();
        metrics.getStyleClass().add("guard-metrics");
        metrics.setAlignment(Pos.CENTER);
        metrics.getChildren().setAll(
                metric(playersValue, playersMax, i18n("guard.metrics.players")),
                metric(latencyValue, latencyUnit, i18n("guard.metrics.latency")),
                metric(tpsValue, null, i18n("server.tps")),
                metric(interceptsValue, null, i18n("guard.metrics.intercepts")));
        for (var child : metrics.getChildren())
            HBox.setHgrow(child, Priority.ALWAYS);

        var statusRow = new HBox();
        statusRow.getStyleClass().add("guard-status-row");
        statusRow.setAlignment(Pos.CENTER_LEFT);
        statusLabel.getStyleClass().add("guard-badge");
        versionLabel.getStyleClass().add("guard-version");

        var refresh = new Label(i18n("server.refresh"));
        refresh.getStyleClass().add("guard-refresh");
        refresh.setCursor(Cursor.HAND);
        refresh.setOnMouseClicked(e -> refresh());

        var statusSpacer = new Region();
        statusSpacer.setMinWidth(0);

        statusRow.getChildren().setAll(statusLabel, versionLabel, statusSpacer, refresh);
        HBox.setHgrow(statusSpacer, Priority.ALWAYS);

        motdBox.getStyleClass().add("guard-motd");
        motdBox.setSpacing(2);
        motdBox.setVisible(false);

        playersPane.getStyleClass().add("guard-players");

        sparkEmptyLabel.getStyleClass().add("guard-spark-empty");
        var sparkPane = new StackPane();
        sparkPane.setPrefHeight(54);
        sparkPane.getChildren().setAll(sparkline, sparkEmptyLabel);

        setTone("idle");
        getChildren().setAll(head, metrics, sparkPane, statusRow, motdBox, playersPane);

        Themes.colorSchemeProperty().addListener(ignored -> sparkline.requestLayout());

        startRing();
        refresh();
    }

    /// Builds one of the four numbers of the card.
    ///
    /// @param value the label that holds the number
    /// @param unit the label that holds the unit, or `null` when the number stands alone
    /// @param caption the line under the number
    /// @return the box that holds the number
    private static VBox metric(Label value, @Nullable Label unit, String caption) {
        value.getStyleClass().add("guard-m-val");

        var row = new HBox();
        row.setAlignment(Pos.BASELINE_CENTER);
        row.getChildren().add(value);
        if (unit != null) {
            unit.getStyleClass().add("guard-m-unit");
            row.getChildren().add(unit);
        }

        var captionLabel = new Label(caption);
        captionLabel.getStyleClass().add("guard-m-lbl");

        var box = new VBox();
        box.getStyleClass().add("guard-metric");
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(9, 4, 7, 4));
        box.getChildren().setAll(row, captionLabel);
        return box;
    }

    /// Reloads the state of the server from the website.
    public void refresh() {
        if (loading)
            return;
        loading = true;

        Task.supplyAsync(GuardCard::load)
                .whenComplete(Schedulers.javafx(), (result, exception) -> {
                    loading = false;
                    if (result != null) {
                        showResult(result);
                    } else {
                        showFailure(exception);
                    }
                })
                .start();
    }

    /// Reads everything the card shows in one background step.
    private static GuardData load() throws IOException {
        LuCunAPI.GuardStatus status = LuCunAPI.fetchGuardStatus();

        String motd = null;
        try {
            motd = LuCunAPI.fetchServerStatus().motd();
        } catch (IOException e) {
            LOG.warning("Failed to read the motto of the server", e);
        }
        return new GuardData(status, motd);
    }

    /// Fills the card with what the website sent.
    private void showResult(GuardData data) {
        var status = data.status();

        setTone(toneOf(status));
        pillLabel.setText(postureLabel(status));

        if (status.online()) {
            playersValue.setText(String.valueOf(status.players_online()));
            playersMax.setText("/" + status.players_max());
            setValueTone(playersValue, "guard-val-ok");
        } else {
            playersValue.setText(i18n("guard.offline"));
            playersMax.setText("");
            setValueTone(playersValue, "guard-val-danger");
        }

        Integer latency = status.latency_ms();
        if (latency == null) {
            latencyValue.setText("--");
            setValueTone(latencyValue, "guard-val-muted");
        } else {
            latencyValue.setText(String.valueOf(latency));
            setValueTone(latencyValue, latency < 50 ? "guard-val-ok" : latency < 200 ? "guard-val-warn" : "guard-val-danger");
        }

        Double tps = status.tps();
        if (tps == null) {
            tpsValue.setText("--");
            setValueTone(tpsValue, "guard-val-muted");
        } else {
            tpsValue.setText(String.format("%.1f", tps));
            setValueTone(tpsValue, tps >= 16.5 ? "guard-val-ok" : "guard-val-warn");
        }

        int intercepts = status.guard() == null ? 0 : status.guard().intercepts_24h();
        interceptsValue.setText(String.valueOf(intercepts));
        setValueTone(interceptsValue, intercepts > 0 ? "guard-val-info" : "guard-val-muted");

        statusLabel.setText(status.online() ? i18n("server.online") : i18n("guard.offline"));
        setValueTone(statusLabel, status.online() ? "guard-val-ok" : "guard-val-danger");
        versionLabel.setText(status.version() == null || status.version().isEmpty() ? "--" : status.version());

        String motd = data.motd();
        if (motd == null || motd.isBlank()) {
            motdBox.getChildren().clear();
            motdBox.setVisible(false);
        } else {
            motdBox.getChildren().setAll(motd.lines().map(GuardCard::mottoLine).toList());
            motdBox.setVisible(true);
        }

        playersPane.getChildren().setAll(playerBadges(status.player_list()));

        List<Double> delays = status.samples() == null ? List.of() : status.samples().stream()
                .map(LuCunAPI.GuardStatus.Sample::latency_ms)
                .filter(Objects::nonNull)
                .toList();
        sparkline.setValues(delays);
        sparkline.setWarnValue(status.thresholds() == null ? -1 : status.thresholds().latency_warn_ms());
        sparkEmptyLabel.setVisible(delays.size() < 2);
    }

    /// Puts the card back to what it shows before the website answers.
    private void showFailure(@Nullable Exception exception) {
        setTone("idle");
        pillLabel.setText(i18n("guard.connecting"));
        statusLabel.setText(i18n("guard.connecting"));
        setValueTone(statusLabel, "guard-val-muted");
        versionLabel.setText("--");
        playersValue.setText("--");
        playersMax.setText("");
        latencyValue.setText("--");
        tpsValue.setText("--");
        interceptsValue.setText("--");
        motdBox.getChildren().clear();
        motdBox.setVisible(false);
        playersPane.getChildren().clear();
        if (exception != null)
            LOG.warning("Failed to read the state of the server", exception);
    }

    /// Makes one line of the motto of the server.
    ///
    /// @param line the line the server wrote
    /// @return the label that shows the line
    private static Label mottoLine(String line) {
        var label = new Label(line);
        label.setMaxWidth(Double.MAX_VALUE);
        return label;
    }

    /// Returns the word that tells how the server is doing.
    ///
    /// @param status the state of the server
    /// @return the word for the pill
    private static String postureLabel(LuCunAPI.GuardStatus status) {
        var posture = status.posture();
        if (posture == null)
            return i18n("guard.healthy");
        return switch (posture.level()) {
            case "warning" -> i18n("guard.warning");
            case "danger" -> i18n("guard.danger");
            case "offline" -> i18n("guard.down");
            default -> i18n("guard.healthy");
        };
    }

    /// Builds the badges of the players who are in.
    ///
    /// @param names the names of the players, or `null` when the website sent none
    /// @return the badges, at most twelve of them
    private static List<Label> playerBadges(@Nullable List<String> names) {
        if (names == null)
            return List.of();
        return names.stream().limit(12).map(name -> {
            var badge = new Label(name);
            badge.getStyleClass().add("guard-player");
            return badge;
        }).toList();
    }

    /// Returns the tone the card is in for the state the website sent.
    ///
    /// @param status the state of the server
    /// @return one of `ok`, `warn`, `danger` and `idle`
    private static String toneOf(LuCunAPI.GuardStatus status) {
        if (!status.online())
            return "idle";
        String level = status.posture() == null ? "active" : status.posture().level();
        return switch (level) {
            case "warning" -> "warn";
            case "danger" -> "danger";
            case "offline" -> "idle";
            default -> "ok";
        };
    }

    /// Puts the card in a tone, which colors the emblem and the pill.
    ///
    /// @param tone one of `ok`, `warn`, `danger` and `idle`
    private void setTone(String tone) {
        getStyleClass().removeAll("guard-tone-ok", "guard-tone-warn", "guard-tone-danger", "guard-tone-idle");
        getStyleClass().add("guard-tone-" + tone);
        sparkline.setTone(tone);
    }

    /// Sets the color of one of the four numbers.
    ///
    /// @param label the label that holds the number
    /// @param style the style class that gives the number its color
    private static void setValueTone(Label label, String style) {
        label.getStyleClass().removeAll("guard-val-ok", "guard-val-warn", "guard-val-danger", "guard-val-muted", "guard-val-info");
        label.getStyleClass().add(style);
    }

    /// Starts the ring that turns around the emblem.
    private void startRing() {
        if (!AnimationUtils.isAnimationEnabled())
            return;

        Timeline timeline = new Timeline(new KeyFrame(Duration.ZERO, new KeyValue(ring.rotateProperty(), 0.0)),
                new KeyFrame(Duration.seconds(6), new KeyValue(ring.rotateProperty(), 360.0)));
        timeline.setCycleCount(Animation.INDEFINITE);
        timeline.play();
    }

    /// Everything the card shows, read from the website in one go.
    private record GuardData(LuCunAPI.GuardStatus status, @Nullable String motd) {
    }
}
