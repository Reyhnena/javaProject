package com.example.socket;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.paint.Color;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.*;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class DrawingCanvas extends Application {
    private GraphicsContext gc;
    private double startX, startY;
    private String selectedTool = "LINE";
    private Color selectedColor = Color.BLACK;
    private Canvas canvas;
    private String text;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private int activeUsers = 0;
    private String username;

    private final Map<String, String> users = new HashMap<>(); // ذخیره نام‌های کاربری و رمز عبور

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        loadUserDatabase(); // بارگذاری اطلاعات کاربران
        showLoginPage(primaryStage);
    }

    private void showLoginPage(Stage primaryStage) {
        Stage loginStage = new Stage();
        loginStage.setTitle("Login or Register");

        TabPane tabPane = new TabPane();
        Tab loginTab = new Tab("Login", createLoginPane(primaryStage, loginStage));
        Tab registerTab = new Tab("Register", createRegisterPane());

        tabPane.getTabs().addAll(loginTab, registerTab);
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Scene scene = new Scene(tabPane, 400, 300);
        loginStage.setScene(scene);
        loginStage.show();
    }

    private GridPane createLoginPane(Stage primaryStage, Stage loginStage) {
        GridPane loginPane = new GridPane();
        loginPane.setPadding(new Insets(10));
        loginPane.setHgap(10);
        loginPane.setVgap(10);

        Label usernameLabel = new Label("Username:");
        TextField usernameField = new TextField();
        Label passwordLabel = new Label("Password:");
        PasswordField passwordField = new PasswordField();
        Button loginButton = new Button("Login");

        loginPane.add(usernameLabel, 0, 0);
        loginPane.add(usernameField, 1, 0);
        loginPane.add(passwordLabel, 0, 1);
        loginPane.add(passwordField, 1, 1);
        loginPane.add(loginButton, 1, 2);

        loginButton.setOnAction(e -> {
            username = usernameField.getText();
            String password = passwordField.getText();

            if (users.containsKey(username) && users.get(username).equals(password)) {
                showAlert("Success", "Login successful!");
                loginStage.close();
                connectToServer();
                sendUsernameToServer();
                showDrawingCanvas(primaryStage);
            } else {
                showAlert("Error", "Invalid username or password!");
            }
        });

        return loginPane;
    }

    private GridPane createRegisterPane() {
        GridPane registerPane = new GridPane();
        registerPane.setPadding(new Insets(10));
        registerPane.setHgap(10);
        registerPane.setVgap(10);

        Label usernameLabel = new Label("Username:");
        TextField usernameField = new TextField();
        Label passwordLabel = new Label("Password:");
        PasswordField passwordField = new PasswordField();
        Button registerButton = new Button("Register");

        registerPane.add(usernameLabel, 0, 0);
        registerPane.add(usernameField, 1, 0);
        registerPane.add(passwordLabel, 0, 1);
        registerPane.add(passwordField, 1, 1);
        registerPane.add(registerButton, 1, 2);

        registerButton.setOnAction(e -> {
            String newUsername = usernameField.getText();
            String newPassword = passwordField.getText();

            if (newUsername.isEmpty() || newPassword.isEmpty()) {
                showAlert("Error", "Username or password cannot be empty!");
            } else if (users.containsKey(newUsername)) {
                showAlert("Error", "Username already exists!");
            } else {
                users.put(newUsername, newPassword);
                saveUserDatabase();
                showAlert("Success", "Registration successful! You can now log in.");
            }
        });

        return registerPane;
    }

    private void showDrawingCanvas(Stage primaryStage) {
        primaryStage.setTitle("Drawing Canvas");

        BorderPane borderPane = new BorderPane();
        canvas = new Canvas(800, 600);
        gc = canvas.getGraphicsContext2D();

        ToolBar toolBar = new ToolBar();
        Button lineButton = new Button("Line");
        Button shapeButton = new Button("Shape");
        Button circleButton = new Button("Circle");
        Button textButton = new Button("Text");
        Button eraseButton = new Button("Erase");
        Button paintButton = new Button("Paint");
        ColorPicker colorPicker = new ColorPicker();


        lineButton.setOnAction(e -> selectedTool = "LINE");
        shapeButton.setOnAction(e -> selectedTool = "SHAPE");
        circleButton.setOnAction(e -> selectedTool = "CIRCLE");
        textButton.setOnAction(e -> {
            selectedTool = "Text";
            getUserInput();
        });
        eraseButton.setOnAction(e -> selectedTool = "ERASER");
        paintButton.setOnAction(e -> selectedTool = "PAINT");
        colorPicker.setOnAction(e -> selectedColor = colorPicker.getValue());

        toolBar.getItems().addAll(lineButton, shapeButton, circleButton, textButton, eraseButton, paintButton, colorPicker);
        borderPane.setTop(toolBar);
        borderPane.setCenter(canvas);
        BorderPane.setMargin(canvas, new Insets(10));

        Scene scene = new Scene(borderPane, 800, 600);
        primaryStage.setScene(scene);
        primaryStage.show();

       Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            requestActiveUsers();
            drawActiveUsers();
        }));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();

        canvas.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
            startX = e.getX();
            startY = e.getY();
        });

        canvas.addEventHandler(MouseEvent.MOUSE_RELEASED, e -> {
            double endX = e.getX();
            double endY = e.getY();
            draw(startX, startY, endX, endY);
        });
    }

    private void connectToServer() {
        try {
            socket = new Socket("localhost", 8080);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendUsernameToServer() {
        if (out != null) {
            out.println("USERNAME:" + username);
        }
    }

    private void requestActiveUsers() {
        if (out != null) {
            out.println("GET_ACTIVE_USERS");
            try {
                String response = in.readLine();
                if (response != null && response.startsWith("Active users:")) {
                    activeUsers = Integer.parseInt(response.split(":")[1].trim());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void drawActiveUsers() {
        gc.clearRect(0, 0, 200, 50);
       gc.setFill(Color.BLACK);
       gc.fillText("Active Users: " + activeUsers, 10, 30);
    }
    private void getUserInput() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Text Input");
        dialog.setHeaderText("Enter text to draw on canvas:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(text -> drawTextOnCanvas(text));

    }
    private void drawTextOnCanvas(String text) {
        gc.clearRect(0, 0, canvas.getWidth()/4, canvas.getHeight()/4);
        gc.strokeText(text, 100,100);
    }

    private void draw(double startX, double startY, double endX, double endY) {
        gc.setStroke(selectedColor);

        switch (selectedTool) {
            case "LINE":
                gc.strokeLine(startX, startY, endX, endY);
                break;
            case "SHAPE":
                gc.strokeRect(startX, startY, endX - startX, endY - startY);
                break;
            case "CIRCLE":
                gc.strokeOval(startX, startY, endX - startX, endY - startY);
                break;
            case "ERASER":
                gc.clearRect(startX, startY, 30, 30);
                break;
            case "PAINT":
                gc.setFill(selectedColor);
                gc.fillOval(startX, startY, 40, 40);
                break;
        }
    }

    private void loadUserDatabase() {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream("users.dat"))) {
            Map<String, String> loadedUsers = (Map<String, String>) ois.readObject();
            users.putAll(loadedUsers);
        } catch (IOException | ClassNotFoundException e) {
            // فایل وجود ندارد یا مشکلی در بارگذاری رخ داده است
        }
    }

    private void saveUserDatabase() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream("users.dat"))) {
            oos.writeObject(users);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        if (socket != null) {
            socket.close();
        }
    }
}