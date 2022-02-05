package com.geekbrains.cloud.files;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.Objects;

public class FileApp extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        Parent parent = FXMLLoader.load(Objects.requireNonNull(getClass().getResource("files-layout.fxml")));
        primaryStage.setScene(new Scene(parent));
        primaryStage.show();
    }
}
