package com.example.socket;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

public class PaintServer {
    private static final int PORT = 12345;
    private static final String fileUser = "users.txt";
    private Map<String, String> users = new HashMap<>();
    private int activeUsers = 0; // تعداد کاربران فعال

    public static void main(String[] args) {
        new PaintServer().startServer();
    }

    private void loadUsers() throws IOException {
        File file = new File(fileUser);
        if (file.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(":");
                    if (parts.length == 2) {
                        users.put(parts[0], parts[1]);
                    }
                }
            }
        }
    }

    public void startServer() {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            loadUsers();
            System.out.println("Server is running on port " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                new ClientHandler(clientSocket).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void saveUsersInfo() throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileUser))) {
            for (Map.Entry<String, String> entry : users.entrySet()) {
                writer.write(entry.getKey() + ":" + entry.getValue());
                writer.newLine();
            }
        }
    }

    private synchronized String signUp(String username, String password) throws IOException {
        if (users.containsKey(username)) {
            return "this user is already existing";
        }
        users.put(username, password);
        saveUsersInfo();
        return "sign up successful";
    }

    private synchronized String login(String username, String password) {
        if (users.containsKey(username) && users.get(username).equals(password)) {
            return "login successful";
        }
        return "login failed";
    }

    private synchronized int getActiveUsers() {
        return activeUsers; // برگرداندن تعداد کاربران فعال
    }

    private class ClientHandler extends Thread {
        private Socket socket;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

                String message;
                while ((message = in.readLine()) != null) {
                    String[] parts = message.split(" ");
                    String command = parts[0];

                    if ("REGISTER".equals(command)) {
                        String username = parts[1];
                        String password = parts[2];
                        String response = signUp(username, password);
                        out.println(response);
                    } else if ("LOGIN".equals(command)) {
                        String username = parts[1];
                        String password = parts[2];
                        String response = login(username, password);
                        if ("login successful".equals(response)) {
                            synchronized (this) {
                                activeUsers++; // افزایش کاربران فعال
                            }
                        }
                        out.println(response);
                    } else if ("GET_ACTIVE_USERS".equals(command)) { // دریافت تعداد کاربران فعال
                        out.println("Active users: " + getActiveUsers());
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                synchronized (this) {
                    activeUsers--; // کاهش کاربران فعال هنگام قطع اتصال
                }
            }
        }
    }
}
