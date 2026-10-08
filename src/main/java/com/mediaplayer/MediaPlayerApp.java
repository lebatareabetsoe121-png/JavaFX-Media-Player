package com.mediaplayer;

import java.io.File;

import javafx.animation.FadeTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Slider;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.util.Duration;

public class MediaPlayerApp extends Application {

    private MediaView mediaView;
    private MediaPlayer mediaPlayer;
    private ListView<File> playlistView;
    private Slider volumeSlider;
    private Slider progressBar;
    private Label statusLabel;
    private Label timeLabel;
    private Label nowPlayingLabel;
    private Label volPercentLabel;
    private StackPane overlayControls;
    private Button playPauseBtn;
    private Button volBtn; // <-- DECLARED HERE SO IT COMPILES

    private PlaylistManager playlistManager;

    private boolean isProgrammaticUpdate = false;
    private int currentIndex = -1;
    private boolean isMuted = false;
    private boolean isDraggingProgress = false;

    @Override
    public void start(Stage primaryStage) {
        playlistManager = new PlaylistManager();

        BorderPane root = new BorderPane();

        // --- Header ---
        nowPlayingLabel = new Label("NOW PLAYING: NONE");
        nowPlayingLabel.getStyleClass().add("header-label");
        nowPlayingLabel.setMaxWidth(Double.MAX_VALUE);
        root.setTop(nowPlayingLabel);

        // --- Center: Video Playback Area ---
        StackPane videoPane = new StackPane();
        videoPane.getStyleClass().add("video-pane");
        videoPane.setMinSize(0, 0);

        // 1. The Audio Placeholder
        StackPane audioPlaceholder = new StackPane();
        audioPlaceholder.getStyleClass().add("audio-placeholder");
        Label musicIcon = new Label("\u266B");
        musicIcon.getStyleClass().add("audio-icon");
        audioPlaceholder.getChildren().add(musicIcon);

        // 2. The Media View (Video)
        mediaView = new MediaView();
        mediaView.setPreserveRatio(false);
        mediaView.setSmooth(true);
        mediaView.setCache(false);
        mediaView.fitWidthProperty().bind(videoPane.widthProperty());
        mediaView.fitHeightProperty().bind(videoPane.heightProperty());

        // 3. The Overlay Controls (CENTERED with black background)
        overlayControls = new StackPane();
        overlayControls.getStyleClass().add("overlay-controls");
        overlayControls.setOpacity(0);
        overlayControls.setPickOnBounds(false);

        HBox transportControls = new HBox(30); 
        transportControls.setAlignment(Pos.CENTER); // Centered as requested
        transportControls.setPadding(new Insets(0, 0, 5, 0));
        
        // 1. STOP BUTTON (Clean square)
        Button stopBtn = new Button("\u25A0"); 
        stopBtn.getStyleClass().add("overlay-btn");
        stopBtn.setTooltip(new Tooltip("Stop (S)"));
        
        // 2. REWIND / PREVIOUS
        Button prevBtn = new Button("\u23EE"); 
        prevBtn.getStyleClass().add("overlay-btn");
        prevBtn.setTooltip(new Tooltip("Previous (P)"));
        
        // 3. PLAY / PAUSE (Outline triangle for Play)
        playPauseBtn = new Button("\u25B7"); 
        playPauseBtn.getStyleClass().addAll("overlay-btn", "play-btn-large");
        playPauseBtn.setTooltip(new Tooltip("Play / Pause (Space)"));
        
        // 4. FAST FORWARD / NEXT
        Button nextBtn = new Button("\u23ED"); 
        nextBtn.getStyleClass().add("overlay-btn");
        nextBtn.setTooltip(new Tooltip("Next (N)"));
        
        // 5. VOLUME (Musical note instead of emoji)
        volBtn = new Button("\u266B"); 
        volBtn.getStyleClass().add("overlay-btn");
        volBtn.setTooltip(new Tooltip("Mute/Unmute (M)"));

        transportControls.getChildren().addAll(stopBtn, prevBtn, playPauseBtn, nextBtn, volBtn);
        overlayControls.getChildren().add(transportControls);

        // Add them to the StackPane
        videoPane.getChildren().addAll(audioPlaceholder, mediaView, overlayControls);

        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(videoPane.widthProperty());
        clip.heightProperty().bind(videoPane.heightProperty());
        videoPane.setClip(clip);

        // --- Hover Effects for Overlay ---
        videoPane.setOnMouseEntered(e -> showOverlay());
        videoPane.setOnMouseExited(e -> hideOverlay());

        // --- Right: Playlist ---
        VBox playlistBox = new VBox(12);
        playlistBox.getStyleClass().add("playlist-pane");
        playlistBox.setMinWidth(160);

        Label playlistTitle = new Label("PLAYLIST");
        playlistTitle.getStyleClass().add("playlist-title");

        ObservableList<File> playlistFiles = playlistManager.getPlaylist();
        playlistView = new ListView<>(playlistFiles);
        playlistView.setPlaceholder(new Label("No media yet.\nClick + ADD to get started."));
        playlistView.setCellFactory(lv -> new ListCell<File>() {
            @Override
            protected void updateItem(File item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item.getName());
            }
        });
        VBox.setVgrow(playlistView, Priority.ALWAYS);

        HBox playlistControls = new HBox(10);
        playlistControls.setAlignment(Pos.CENTER);
        Button addBtn = new Button("+ ADD");
        addBtn.getStyleClass().addAll("button", "success-btn");
        Button removeBtn = new Button("\u2212 REMOVE");
        removeBtn.getStyleClass().addAll("button", "danger-btn");
        for (Button b : new Button[] { addBtn, removeBtn }) {
            b.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(b, Priority.ALWAYS);
        }
        playlistControls.getChildren().addAll(addBtn, removeBtn);

        statusLabel = new Label("Ready");
        statusLabel.getStyleClass().add("status-label");

        playlistBox.getChildren().addAll(playlistTitle, playlistView, playlistControls, statusLabel);

        // --- Split pane ---
        SplitPane mainSplit = new SplitPane();
        mainSplit.getItems().addAll(videoPane, playlistBox);
        mainSplit.setDividerPositions(0.74);
        root.setCenter(mainSplit);

        // --- Bottom: Controls (Progress & Volume only) ---
        VBox bottomPane = new VBox(12);
        bottomPane.getStyleClass().add("controls-pane");
        bottomPane.setPadding(new Insets(14, 24, 16, 24));

        HBox progressRow = new HBox(14);
        progressRow.setAlignment(Pos.CENTER);
        progressBar = new Slider(0, 100, 0);
        progressBar.getStyleClass().add("progress-slider");
        HBox.setHgrow(progressBar, Priority.ALWAYS);
        timeLabel = new Label("00:00 / 00:00");
        timeLabel.getStyleClass().add("time-label");
        progressRow.getChildren().addAll(progressBar, timeLabel);

        HBox volumeControls = new HBox(10);
        volumeControls.setAlignment(Pos.CENTER_RIGHT);
        volumeSlider = new Slider(0, 1, 0.5);
        volumeSlider.setPrefWidth(110);
        volumeSlider.setMinWidth(60);
        volPercentLabel = new Label("50%");
        volPercentLabel.getStyleClass().add("volume-percent");
        Label volIcon = new Label("VOL");
        volIcon.getStyleClass().add("volume-icon");
        volumeControls.getChildren().addAll(volIcon, volumeSlider, volPercentLabel);

        HBox controlsRow = new HBox(20);
        controlsRow.setAlignment(Pos.CENTER);
        HBox.setHgrow(progressRow, Priority.ALWAYS);
        controlsRow.getChildren().addAll(progressRow, volumeControls);

        bottomPane.getChildren().add(controlsRow);
        root.setBottom(bottomPane);

        // --- Event Handlers ---
        playPauseBtn.setOnAction(e -> { 
            togglePlayPause(); 
            updatePlayPauseButton(); 
            showOverlay(); 
        });
        stopBtn.setOnAction(e -> { 
            stopPlayback(); 
            updatePlayPauseButton(); 
            showOverlay(); 
        });
        prevBtn.setOnAction(e -> { 
            playPrevious(); 
            updatePlayPauseButton(); 
            showOverlay(); 
        });
        nextBtn.setOnAction(e -> { 
            playNext(); 
            updatePlayPauseButton(); 
            showOverlay(); 
        });
        volBtn.setOnAction(e -> { 
            toggleMute(); 
            volBtn.setText(isMuted ? "\u266A" : "\u266B"); // Toggle music notes
            showOverlay(); 
        });

        addBtn.setOnAction(e -> playlistManager.addFiles(primaryStage));

        removeBtn.setOnAction(e -> {
            int selectedIndex = playlistView.getSelectionModel().getSelectedIndex();
            if (selectedIndex != -1) {
                String removedName = playlistManager.getPlaylist().get(selectedIndex).getName();
                if (selectedIndex == currentIndex) {
                    stopPlayback();
                    mediaPlayer = null;
                    mediaView.setMediaPlayer(null);
                    nowPlayingLabel.setText("NOW PLAYING: NONE");
                    updatePlayPauseButton();
                } else if (selectedIndex < currentIndex) {
                    currentIndex--;
                }
                playlistManager.removeFile(selectedIndex);
                updateStatus("Removed: " + removedName);
            }
        });

        volumeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            int percent = (int) Math.round(newVal.doubleValue() * 100);
            volPercentLabel.setText(percent + "%");
            if (mediaPlayer != null) mediaPlayer.setVolume(newVal.doubleValue());
            updateSliderFill(volumeSlider, "#a855f7");
        });

        progressBar.valueProperty().addListener((obs, oldVal, newVal) -> {
            updateSliderFill(progressBar, "#22d3ee");
            if (!isProgrammaticUpdate && mediaPlayer != null && mediaPlayer.getMedia() != null
                    && mediaPlayer.getMedia().getDuration() != null) {
                double newTime = newVal.doubleValue() / 100.0 * mediaPlayer.getMedia().getDuration().toMillis();
                mediaPlayer.seek(Duration.millis(newTime));
            }
        });
        progressBar.setOnMousePressed(e -> isDraggingProgress = true);
        progressBar.setOnMouseReleased(e -> isDraggingProgress = false);

        playlistView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                int newIndex = playlistManager.getPlaylist().indexOf(newVal);
                if (newIndex != -1 && newIndex != currentIndex) loadAndPlay(newIndex);
            }
        });

        // --- Scene, responsive scaling & keyboard ---
        Scene scene = new Scene(root, 1200, 750);
        scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());

        Runnable rescale = () -> {
            double size = Math.min(scene.getWidth() / 85.0, scene.getHeight() / 50.0);
            size = Math.max(11, Math.min(26, size));
            root.setStyle("-fx-font-size: " + String.format("%.1f", size) + "px;");
        };
        scene.widthProperty().addListener((o, a, b) -> rescale.run());
        scene.heightProperty().addListener((o, a, b) -> rescale.run());
        rescale.run();

        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            KeyCode code = event.getCode();
            if (code == KeyCode.SPACE) { 
                togglePlayPause(); 
                updatePlayPauseButton();
                showOverlay(); 
                event.consume(); 
            }
            else if (code == KeyCode.S) { 
                stopPlayback(); 
                updatePlayPauseButton();
                showOverlay(); 
                event.consume(); 
            }
            else if (code == KeyCode.N) { 
                playNext(); 
                updatePlayPauseButton();
                showOverlay(); 
                event.consume(); 
            }
            else if (code == KeyCode.P) { 
                playPrevious(); 
                updatePlayPauseButton();
                showOverlay(); 
                event.consume(); 
            }
            else if (code == KeyCode.UP) { increaseVolume(); event.consume(); }
            else if (code == KeyCode.DOWN) { decreaseVolume(); event.consume(); }
            else if (code == KeyCode.M) { 
                toggleMute(); 
                volBtn.setText(isMuted ? "\u266A" : "\u266B"); 
                event.consume(); 
            }
        });

        primaryStage.setTitle("Media Player");
        primaryStage.setMinWidth(640);
        primaryStage.setMinHeight(420);
        primaryStage.setScene(scene);
        primaryStage.show();
        scene.getRoot().requestFocus();

        Platform.runLater(() -> {
            updateSliderFill(progressBar, "#22d3ee");
            updateSliderFill(volumeSlider, "#a855f7");
        });
    }

    private void updatePlayPauseButton() {
        if (mediaPlayer == null) {
            playPauseBtn.setText("\u25B7"); // Outline Play icon
        } else if (mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING) {
            playPauseBtn.setText("\u23F8"); // Pause icon
        } else {
            playPauseBtn.setText("\u25B7"); // Outline Play icon
        }
    }

    // --- Overlay Fade Logic ---
    private void showOverlay() {
        FadeTransition ft = new FadeTransition(Duration.millis(300), overlayControls);
        ft.setFromValue(overlayControls.getOpacity());
        ft.setToValue(1.0);
        ft.play();
    }

    private void hideOverlay() {
        FadeTransition ft = new FadeTransition(Duration.millis(500), overlayControls);
        ft.setFromValue(overlayControls.getOpacity());
        ft.setToValue(0.0);
        ft.play();
    }

    /** Paints the played portion of a slider's track with an accent color. */
    private void updateSliderFill(Slider s, String color) {
        Node track = s.lookup(".track");
        if (track == null) return;
        double pct = (s.getValue() - s.getMin()) / (s.getMax() - s.getMin()) * 100.0;
        track.setStyle(String.format(
            "-fx-background-color: linear-gradient(to right, %s %.1f%%, #26263e %.1f%%);",
            color, pct, pct));
    }

    // --- Media Control Methods ---
    private void play() { if (mediaPlayer != null) { mediaPlayer.play(); updateStatus("Playing"); } else if (!playlistManager.getPlaylist().isEmpty()) loadAndPlay(0); }
    private void pause() { if (mediaPlayer != null) { mediaPlayer.pause(); updateStatus("Paused"); } }
    private void stopPlayback() { if (mediaPlayer != null) { mediaPlayer.stop(); updateStatus("Stopped"); } }
    private void togglePlayPause() {
        if (mediaPlayer != null) { if (mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING) pause(); else play(); }
        else if (!playlistManager.getPlaylist().isEmpty()) play();
    }
    private void playNext() { if (playlistManager.getPlaylist().isEmpty()) return; loadAndPlay((currentIndex + 1) % playlistManager.getPlaylist().size()); }
    private void playPrevious() { if (playlistManager.getPlaylist().isEmpty()) return; loadAndPlay((currentIndex - 1 + playlistManager.getPlaylist().size()) % playlistManager.getPlaylist().size()); }
    private void increaseVolume() {
        if (mediaPlayer != null) { double vol = Math.min(1.0, mediaPlayer.getVolume() + 0.1); mediaPlayer.setVolume(vol); volumeSlider.setValue(vol); }
    }
    private void decreaseVolume() {
        if (mediaPlayer != null) { double vol = Math.max(0.0, mediaPlayer.getVolume() - 0.1); mediaPlayer.setVolume(vol); volumeSlider.setValue(vol); }
    }
    private void toggleMute() {
        if (mediaPlayer != null) { isMuted = !isMuted; mediaPlayer.setMute(isMuted); updateStatus(isMuted ? "Muted" : "Unmuted"); }
    }

    // --- Helper Methods ---
    private void loadAndPlay(int index) {
        if (index < 0 || index >= playlistManager.getPlaylist().size()) return;
        currentIndex = index;
        if (mediaPlayer != null) { mediaPlayer.stop(); mediaPlayer.dispose(); }

        File file = playlistManager.getPlaylist().get(index);
        Media media = new Media(file.toURI().toString());
        mediaPlayer = new MediaPlayer(media);

        mediaPlayer.setVolume(volumeSlider.getValue());
        mediaPlayer.setMute(isMuted);
        mediaView.setMediaPlayer(mediaPlayer);

        mediaPlayer.play();
        updateStatus("Playing");
        updatePlayPauseButton();

        Platform.runLater(() -> {
            if (mediaPlayer != null && mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING) {
                mediaPlayer.seek(Duration.millis(10));
            }
        });

        mediaPlayer.setOnError(() -> { updateStatus("Error playing file"); });

        mediaPlayer.currentTimeProperty().addListener((obs, oldTime, newTime) -> {
            if (!isDraggingProgress && mediaPlayer.getMedia() != null && mediaPlayer.getMedia().getDuration() != null) {
                double progress = newTime.toMillis() / mediaPlayer.getMedia().getDuration().toMillis() * 100;
                isProgrammaticUpdate = true;
                progressBar.setValue(progress);
                isProgrammaticUpdate = false;

                int currentSecs = (int) newTime.toSeconds();
                int totalSecs = (int) mediaPlayer.getMedia().getDuration().toSeconds();
                timeLabel.setText(formatTime(currentSecs) + " / " + formatTime(totalSecs));
            }
        });

        mediaPlayer.setOnEndOfMedia(this::playNext);

        playlistView.getSelectionModel().clearSelection();
        playlistView.getSelectionModel().select(index);
        nowPlayingLabel.setText("NOW PLAYING:  " + file.getName().toUpperCase());
    }

    private String formatTime(int totalSeconds) {
        int mins = totalSeconds / 60;
        int secs = totalSeconds % 60;
        return String.format("%02d:%02d", mins, secs);
    }

    private void updateStatus(String action) { statusLabel.setText(action); }

    public static void main(String[] args) { launch(args); }
}