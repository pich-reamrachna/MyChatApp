package src;

import java.util.HashSet;
import java.util.Set;

public class ChatRoom {
    String roomName;
    String password;
    Set<ClientHandler> members = new HashSet<>();
    String[][] messageHistory = new String[100][2];
    int messageCount = 0;

    public ChatRoom(String name, String password) {
        this.roomName = name;
        this.password = password;
    }

    public void broadcast(String message, ClientHandler sender) {
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
                newMember.out.println("[" + messageHistory[i][0] + "] : " + messageHistory[i][1]);
            }
            newMember.out.println("===================");
            newMember.out.flush();
        }
    }
}
