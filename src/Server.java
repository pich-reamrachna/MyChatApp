package src;
import java.io.*;
import java.net.*;
import java.util.*;

public class Server {
    // ========================
    // 1. CORE DATA STRUCTURES 
    // ========================
    private final static Map<String, ClientHandler> clients = new HashMap<>();
    private final static Map<String, String> userPasswords = new HashMap<>();
    private final static Map<String, ChatRoom> rooms = new HashMap<>();

    // ========================
    // 2. MAIN SERVER SETUP
    // ========================
    public static void main(String[] args) {
        int port = 12345;
        try (ServerSocket serverSocket = new ServerSocket(port)) {          // Listen for client connection request on port 12345
            System.out.println("\nServer started on port " + port + "...");
            while (true) {                                                  // While (true) keeps the server running to accept new client connections. 
                Socket socket = serverSocket.accept();                      // Waits for a client to connect
                ClientHandler handler = new ClientHandler(socket);          // Handles the client
                new Thread(handler).start();                                // Runs the client in a new thread, after handling one client, it loops back to accept()
            }                                                               // It only stops by force
        } catch (IOException e) {
            e.printStackTrace();    // This can be printed if port is already in use or network connection not working
        }
    }

    // ========================
    // 3. CHAT ROOM IMPLEMENTATION 
    // ========================
    static class ChatRoom {
        // Room state
        String roomName;
        String password;
        Set<ClientHandler> members = new HashSet<>();
        String[][] messageHistory = new String[100][2];
        int messageCount = 0;

        // Constructor
        ChatRoom(String name, String password) {
            this.roomName = name;
            this.password = password;
        }

        // Core functionality
        void broadcast(String message, ClientHandler sender) {
            addToHistory(sender.username, message);
            for (ClientHandler member : members) {
                if (member != sender) {
                    if (message.endsWith("joined the room.") || message.endsWith("left the room.")) {
                        member.out.println(sender.username + " " + message);
                    } else {
                        member.out.println("[" + sender.username + "]: " + message);
                    }
                }
            }
        }

        // History management
        private void addToHistory(String username, String message) {
            if (messageCount >= 100) {
                System.arraycopy(messageHistory, 1, messageHistory, 0, 99);
                messageCount--;
            }
            messageHistory[messageCount][0] = username;
            messageHistory[messageCount][1] = message;
            messageCount++;
        }

        public void sendHistoryToNewMember(ClientHandler newMember) {
            if (messageCount > 0) {
                newMember.out.println("\n=== Room History ===");
                for (int i = 0; i < messageCount; i++) {
                    newMember.out.println("["+ messageHistory[i][0] +"] : " + messageHistory[i][1]);
                }
                newMember.out.println("===================");
                newMember.out.flush();
            }
        }
    }

    // ========================
    // 4. CLIENT HANDLER IMPLEMENTATION
    // ========================
    static class ClientHandler implements Runnable {
        // Client state
        private final Socket socket;
        private BufferedReader in;
        private PrintWriter out;
        private String username;
        private ChatRoom currentRoom = null;
        private final Set<String> friends = new HashSet<>();
        private String privateTarget = null;

        // Constructor
        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        // ========================
        // 4.1 CORE CLIENT LIFECYCLE
        // ========================
        public void run() {
            try {
                setupIO();
                handleAuthentication();
                handleMainMenu();
                handleMessaging();
            } catch (IOException e) {
                System.out.println("Connection error with user: " + username);
            } finally {
                cleanup();
            }
        }

        private void setupIO() throws IOException {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
        }

        // ========================
        // 4.2 AUTHENTICATION FLOW
        // ========================
        private void handleAuthentication() throws IOException {
            while (true) {
                out.print("Enter your username:");
                username = in.readLine();

                if (username == null || username.trim().isEmpty()) {
                    out.println("Invalid username.");
                    continue;
                }

                synchronized (userPasswords) {
                    if (userPasswords.containsKey(username)) {
                        handleExistingUser();
                    } else {
                        handleNewUser();
                    }
                }

                synchronized (clients) {
                    if (clients.containsKey(username)) {
                        out.println("User already logged in. Connection closing.");
                        socket.close();
                        return;
                    }
                    clients.put(username, this);
                }

                out.println("Login successful. Welcome, " + username + "!");
                break;
            }
        }

        private void handleExistingUser() throws IOException {
            out.println("Username exists. Enter your password:");
            String enteredPassword = in.readLine();
            if (!userPasswords.get(username).equals(enteredPassword)) {
                out.println("Wrong password. Try again.\n");
                throw new IOException("Authentication failed");
            }
        }

        private void handleNewUser() throws IOException {
            out.println("New user. Set your password:");
            String newPassword = in.readLine();
            if (newPassword == null || newPassword.trim().isEmpty()) {
                out.println("Password cannot be empty.");
                throw new IOException("Invalid password");
            }
            userPasswords.put(username, newPassword);
            out.println("User registered successfully.");
        }

        // ========================
        // 4.3 MAIN MENU FLOW
        // ========================
        private void handleMainMenu() throws IOException {
            while (true) {
                out.println("Do you want to:");
                out.println("1. Join a Room");
                out.println("2. Create a Room");
                out.println("3. Friend Menu");
                out.print("Enter: ");

                String option = in.readLine();

                if ("1".equals(option)) {
                    if (handleJoinRoom()) break;
                } else if ("2".equals(option)) {
                    if (handleCreateRoom()) break;
                } else if ("3".equals(option)) {
                    handleFriendMenu();
                } else {
                    out.println("Invalid option.");
                }
            }
        }

        private boolean handleJoinRoom() throws IOException {
            showAllRooms();
            if (rooms.isEmpty()) {
                out.println("No rooms exist. Please create one first.");
                return false;
            }
            
            out.println("Enter room name (or /back to cancel):");
            String roomName = in.readLine();
            
            if ("/back".equalsIgnoreCase(roomName)) {
                return false;
            }

            synchronized (rooms) {
                ChatRoom room = rooms.get(roomName);
                if (room == null) {
                    out.println("Room does not exist. Try again or type /back to go back.");
                    return false;
                }

                out.println("Enter room password:");
                String pass = in.readLine();

                if (!room.password.equals(pass)) {
                    out.println("Incorrect password. Try again.");
                    return false;
                }

                room.members.add(this);
                currentRoom = room;
                out.println("Joined room: " + roomName);
                room.sendHistoryToNewMember(this);
                room.broadcast("joined the room.", this);
                return true;
            }
        }

        private boolean handleCreateRoom() throws IOException {
            out.println("Enter new room name:");
            String roomName = in.readLine();

            synchronized (rooms) {
                if (rooms.containsKey(roomName)) {
                    out.println("Room already exists. Choose another name.");
                    return false;
                }

                out.println("Set room password:");
                String pass = in.readLine();

                ChatRoom room = new ChatRoom(roomName, pass);
                rooms.put(roomName, room);
                room.members.add(this);
                currentRoom = room;
                out.println("Room created. You joined: " + roomName);
                return true;
            }
        }

        // ========================
        // 4.4 FRIEND SYSTEM
        // ========================
        private void handleFriendMenu() throws IOException {
            while (true) {
                out.println("\nFriend Menu:");
                out.println("1. View friends");
                out.println("2. Add friend");
                out.println("3. Message a friend");
                out.println("4. Back to main menu");
                out.print("Enter your choice: ");

                String input = in.readLine();
                if ("1".equals(input)) {
                    out.println("Your friends: " + friends);
                } else if ("2".equals(input)) {
                    handleAddFriend();
                } else if ("3".equals(input)) {
                    handlePrivateMessage();
                } else if ("4".equals(input)) {
                    break;
                } else {
                    out.println("Invalid option.");
                }
            }
        }

        private void handleAddFriend() throws IOException {
            out.println("Enter username to add:");
            String friendName = in.readLine();
            if (clients.containsKey(friendName) && !friendName.equals(username)) {
                friends.add(friendName);
                out.println(friendName + " has been added to your friend list.");
            } else {
                out.println("User not found.");
            }
        }

        private void handlePrivateMessage() throws IOException {
            out.println("Enter a user username:");
            String target = in.readLine();

            if (friends.contains(target)) {
                ClientHandler targetHandler = clients.get(target);

                if (targetHandler != null) {
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

                        targetHandler = clients.get(privateTarget);
                        if (targetHandler != null) {
                            targetHandler.out.println("[" + username + "]: " + msg);
                        } else {
                            out.println("Friend is no longer online.");
                            privateTarget = null;
                            break;
                        }
                    }
                }
            } else {
                out.println("User is not in your friend list.");
            }
        }

        // ========================
        // 4.5 MESSAGING FLOW
        // ========================
        private void handleMessaging() throws IOException {
            String msg;
            while ((msg = in.readLine()) != null) {
                if (msg.equalsIgnoreCase("/exit")) {
                    break;
                }

                if (currentRoom != null) {
                    currentRoom.broadcast(msg, this);
                }              
            }
        }

        // ========================
        // 4.6 UTILITY METHODS
        // ========================
        public void showAllRooms() {
            synchronized (rooms) {
                if (rooms.isEmpty()) {
                    out.println("No rooms available.");
                } else {
                    out.println("Available Rooms:");
                    for (Map.Entry<String, ChatRoom> entry : rooms.entrySet()) {
                        String name = entry.getKey();
                        int memberCount = entry.getValue().members.size();
                        out.println("- " + name + " (" + memberCount + " members)");
                    }
                }
            }
            out.flush();
        }

        private void cleanup() {
            try {
                if (in != null) in.close();
                if (out != null) out.close();
                if (socket != null && !socket.isClosed()) socket.close();
            } catch (IOException e) {}

            synchronized (clients) {
                clients.remove(username);
            }

            if (currentRoom != null) {
                synchronized (rooms) {
                    currentRoom.members.remove(this);
                    currentRoom.broadcast("left the room.", this);
                }
            }

            System.out.println("User " + username + " disconnected.");
        }
    }
}