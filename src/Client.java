package src;
// Client.java
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

            // === LOGIN FLOW ===
            // Wait for server to ask for username
            String usernamePrompt = in.readLine();
            System.out.println(usernamePrompt);
            String username = scanner.nextLine();
            out.println(username);

            // Now wait for server to ask for password or new registration
            String passwordPrompt = in.readLine();
            System.out.println(passwordPrompt);
            String password = scanner.nextLine();
            out.println(password);

            // Receive one or two lines of response
            String line1 = in.readLine();
            System.out.println(line1);

            // Some servers will send a second line (login successful)
            if (!line1.toLowerCase().contains("successful")) {
                String line2 = in.readLine();
                System.out.println(line2);

                if (!line2.toLowerCase().contains("successful")) {
                    System.out.println("Login failed. Exiting client.");
                    return;
                }
            }

            // Reader thread: listens for messages from server
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
            readerThread.start();

            // Console input loop
            String msg;
            while (true) {
                if (scanner.hasNextLine()) {
                    msg = scanner.nextLine();

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
                if (readerThread != null) readerThread.join(); // Wait for thread to finish
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }

            System.out.println("Client shut down.");
        }
    }
}