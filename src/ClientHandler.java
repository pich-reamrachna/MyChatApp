package src;

import java.io.*;
import java.net.Socket;
import java.util.HashSet;
import java.util.Set;

public class ClientHandler implements Runnable {
    private final Socket socket;
    BufferedReader in;
    PrintWriter out;
    String username;
    ChatRoom currentRoom = null;
    final Set<String> friends = new HashSet<>();
    String privateTarget = null;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            handleLogin();

            while (true) {
                out.println("=== Main Menu ===");
                out.println("1. Join a Room");
                out.println("2. Create a Room");
                out.println("3. Friend Menu");
                out.println("Enter: ");
                
                String option = in.readLine();

                if ("1".equals(option)) {
                    joinRoom();
                } else if ("2".equals(option)) {
                    createRoom();
                } else if ("3".equals(option)) {
                    handleFriendMenu();
                } else {
                    out.println("Invalid option.");
                }

                if (currentRoom != null) break;
            }

            // Main chat loop
            String msg;
            while ((msg = in.readLine()) != null) {
                if (msg.equalsIgnoreCase("/exit")) break;
                if (currentRoom != null) currentRoom.broadcast(msg, this);
            }

        } catch (IOException e) {
            System.out.println("Connection error with user: " + username);
        } finally {
            cleanup();
        }
    }

    private void handleLogin() throws IOException {
        while (true) {
            out.println("PROMPT:Enter your username:");
            username = in.readLine();

            if (username == null || username.trim().isEmpty()) {
                // Client disconnected
                out.println("Invalid username. Please try again...");
                continue;
            }
            username = username.trim();
            System.out.println("Received username: '" + username + "'");


            synchronized (Server.userPasswords) {
                if (Server.userPasswords.containsKey(username)) {
                    // Existing user, ask for password
                    out.println("PROMPT:Username exists. Enter your password:");
                    String enteredPassword = in.readLine();

                    if (enteredPassword == null) {
                        socket.close();
                        return;
                    }
                    enteredPassword = enteredPassword.trim();

                    if (!Server.userPasswords.get(username).equals(enteredPassword)) {
                        out.println("Wrong password. Try again.\n");
                        continue;
                    }
                } else {
                    // New user registration
                    out.println("PROMPT:You're a new user! Please set your password:");
            
                    String newPassword = in.readLine();
                    if (newPassword == null || newPassword.trim().isEmpty()) {
                        out.println("Password cannot be empty. Please try again...");
                        continue;
                    }


                    Server.userPasswords.put(username, newPassword);
                    out.println("User registered successfully.");
                }
            }

            synchronized (Server.clients) {
                if (Server.clients.containsKey(username)) {
                    out.println("User already logged in. Connection closing.");
                    socket.close();
                    return;
                }
                Server.clients.put(username, this);
            }

            out.println("Login successful. Welcome, " + username + "!");
            break;
        }
    }

    private void showAllRooms() {
        synchronized (Server.rooms) {
            if (Server.rooms.isEmpty()) {
                out.println("No rooms available.");
            } else {
                out.println("Available Rooms:");
                for (String name : Server.rooms.keySet()) {
                    int memberCount = Server.rooms.get(name).members.size();
                    out.println("- " + name + " (" + memberCount + " members)");
                }
            }
        }
        out.flush();
    }

    private void joinRoom() throws IOException {
        showAllRooms();
        if (Server.rooms.isEmpty()) {
            out.println("No rooms exist. Please create one first.");
            return;
        }

        out.println("Enter room name (or /back to cancel):");
        String roomName = in.readLine();
        if ("/back".equalsIgnoreCase(roomName)) return;

        synchronized (Server.rooms) {
            ChatRoom room = Server.rooms.get(roomName);
            if (room == null) {
                out.println("Room does not exist.");
                return;
            }

            out.println("Enter room password:");
            String pass = in.readLine();
            if (!room.password.equals(pass)) {
                out.println("Incorrect password.");
                return;
            }

            room.members.add(this);
            currentRoom = room;
            out.println("Joined room: " + roomName);
            room.sendHistoryToNewMember(this);
            room.broadcast("joined the room.", this);
        }
    }

    private void createRoom() throws IOException {
        out.println("Enter new room name:");
        String roomName = in.readLine();

        synchronized (Server.rooms) {
            if (Server.rooms.containsKey(roomName)) {
                out.println("Room already exists.");
                return;
            }

            out.println("Set room password:");
            String pass = in.readLine();
            ChatRoom room = new ChatRoom(roomName, pass);
            Server.rooms.put(roomName, room);
            room.members.add(this);
            currentRoom = room;
            out.println("Room created. You joined: " + roomName);
        }
    }

    private void handleFriendMenu() throws IOException {
        while (true) {
            out.println("\nFriend Menu:");
            out.println("1. View friends");
            out.println("2. Add friend");
            out.println("3. Message a friend");
            out.println("4. Back to main menu");
            out.println("Enter: ");
            String input = in.readLine();

            if ("1".equals(input)) {
                out.println("Your friends: " + friends);
            } else if ("2".equals(input)) {
                out.println("Enter username to add:");
                String friendName = in.readLine();
                if (Server.clients.containsKey(friendName) && !friendName.equals(username)) {
                    friends.add(friendName);
                    out.println(friendName + " has been added to your friend list.");
                } else {
                    out.println("User not found.");
                }
            } else if ("3".equals(input)) {
                out.println("Enter a user username:");
                String target = in.readLine();
                if (!friends.contains(target)) {
                    out.println("User is not in your friend list.");
                    continue;
                }

                ClientHandler targetHandler = Server.clients.get(target);
                if (targetHandler == null) {
                    out.println("User is offline.");
                    continue;
                }

                out.println("Start messaging " + target + " (type /back to stop):");
                privateTarget = target;
                targetHandler.privateTarget = this.username;

                while (true) {
                    String msg = in.readLine();
                    if (msg.equalsIgnoreCase("/back")) {
                        privateTarget = null;
                        targetHandler.privateTarget = null;
                        break;
                    }

                    if (Server.clients.containsKey(privateTarget)) {
                        Server.clients.get(privateTarget).out.println("[" + username + "]: " + msg);
                    } else {
                        out.println("Friend is no longer online.");
                        privateTarget = null;
                        break;
                    }
                }
            } else if ("4".equals(input)) {
                break;
            } else {
                out.println("Invalid option.");
            }
        }
    }

    private void cleanup() {
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {}

        synchronized (Server.clients) {
            Server.clients.remove(username);
        }

        if (currentRoom != null) {
            synchronized (Server.rooms) {
                currentRoom.members.remove(this);
                currentRoom.broadcast("left the room.", this);
            }
        }

        System.out.println("User " + username + " disconnected.");
    }
}
