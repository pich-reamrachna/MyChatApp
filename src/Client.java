package src;

import java.io.*;
import java.net.*;
import java.util.Scanner;

public class Client {
    public static void main(String[] args) {
        // String host = "3.107.76.29";
        String host = "localhost";
        int port = 12345;

        Socket socket = null;
        Scanner scanner = null;
        PrintWriter out = null;
        Thread readerThread = null;

        try {
            // Connect to server
            socket = new Socket(host, port);
            System.out.println("Connected to chat server.");
            scanner = new Scanner(System.in);

            // Setup input/output streams
            InputStream is = socket.getInputStream();
            BufferedReader in = new BufferedReader(new InputStreamReader(is));
            out = new PrintWriter(socket.getOutputStream(), true);

            /*** LOGIN FLOW */
            while (true) {
                String line = in.readLine();
                if (line == null) {
                    System.out.println("Server disconnected during login.");
                    return;
                }

                // If this line is a prompt (like "Enter your username:"), print without newline
                if (line.toLowerCase().contains("username") || line.toLowerCase().contains("password")) {
                    System.out.println();
                    System.out.print(line + " ");  // prompt stays on same line
                    String input = scanner.nextLine();
                    out.println(input);
                    
                } else {
                    // For other messages, print normally
                    System.out.println(line);
                }

                if (line.toLowerCase().contains("successful")) {
                    break;
                } else if (line.toLowerCase().contains("connection closing")) {
                    return;
                }
            }

            // Reader thread: listens for messages from server and prints each on new line
            readerThread = new Thread(() -> {
                try {
                    String response;
                    while ((response = in.readLine()) != null) {
                        System.out.println(response);
                    }
                } catch (IOException e) {
                    System.out.println("Connection closed by server.");
                }
            });
            readerThread.setDaemon(true);
            readerThread.start();

            // === USER INPUT LOOP === // 
            while (true) {
                if (scanner.hasNextLine()) {
                    String msg = scanner.nextLine();

                    if ("/exit".equalsIgnoreCase(msg)) {
                        System.out.println("Closing connection...");
                        break;
                    }

                    if (out != null) {
                        out.println(msg);
                    }
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (scanner != null) scanner.close();
                if (out != null) out.close();
                if (socket != null && !socket.isClosed()) socket.close();
                if (readerThread != null && readerThread.isAlive()) readerThread.join();
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("Client shut down.");
        }
    }
}
