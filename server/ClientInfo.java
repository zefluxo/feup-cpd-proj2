package server;

import java.util.UUID;

public class ClientInfo {

    private ServerCommunicator communicator;
    private long startTime;
    private String token;
    
    public long disconnectedTime;
    public String name;
    public int elo;

    
    public ClientInfo(ServerCommunicator communicator, String name, int elo) {
        this.communicator = communicator;
        this.startTime = System.currentTimeMillis();
        this.disconnectedTime = 0;
        this.token = UUID.randomUUID().toString();

        this.name = name;
        this.elo = elo;
    }

    public int getTimeElapsed() {    
        long currTime = System.currentTimeMillis();
        return ((int) (currTime - startTime))/1000;
    }

    public int getTimeSinceDisconnect() {
        long currTime = System.currentTimeMillis();
        return ((int) (currTime - disconnectedTime))/1000;
    }
    
    public ServerCommunicator getCommunicator() { return communicator; }
    public void setCommunicator(ServerCommunicator communicator) { this.communicator = communicator; }

    public String getToken() { return token; }

    @Override
    public String toString() { return String.format("%s - %d elo", name, elo); }

    @Override
    public boolean equals(Object obj) { return this.name.equals(((ClientInfo) obj).name); }
}
