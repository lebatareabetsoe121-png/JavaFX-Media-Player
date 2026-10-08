package com.mediaplayer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

public class PlaylistManager {

    private ObservableList<File> playlistFiles;
    private static final String PLAYLIST_FILE = "playlist.txt";

    // Constructor: Initializes the list and loads saved files
    public PlaylistManager() {
        playlistFiles = FXCollections.observableArrayList();
        loadPlaylist();
    }

    // Returns the list so the UI (ListView) can observe it
    public ObservableList<File> getPlaylist() {
        return playlistFiles;
    }

    //adds files to the playlist
    public void addFiles(Stage stage) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Add Media Files");
         fileChooser.getExtensionFilters().addAll(
       new FileChooser.ExtensionFilter("Media Files", "*.mp4", "*.mp3", "*.wav", "*.m4a", "*.mkv", "*.avi")
 );
        
        List<File> newFiles = fileChooser.showOpenMultipleDialog(stage);
        if (newFiles != null) {
            playlistFiles.addAll(newFiles);
            savePlaylist();
        }
    }

    // Removes a file at a specific index
    public void removeFile(int index) {
        if (index >= 0 && index < playlistFiles.size()) {
            playlistFiles.remove(index);
            savePlaylist();
        }
    }

    // Saves the current playlist paths to a text file
    private void savePlaylist() {
        try {
            List<String> lines = new ArrayList<>();
            for (File f : playlistFiles) {
                lines.add(f.getAbsolutePath());
            }
            Files.write(Paths.get(PLAYLIST_FILE), lines);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Loads the playlist paths from the text file on startup
    private void loadPlaylist() {
        try {
            if (Files.exists(Paths.get(PLAYLIST_FILE))) {
                List<String> lines = Files.readAllLines(Paths.get(PLAYLIST_FILE));
                for (String line : lines) {
                    File f = new File(line);
                    if (f.exists()) {
                        playlistFiles.add(f);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}