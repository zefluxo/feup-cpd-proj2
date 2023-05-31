package game;

import java.io.IOException;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import server.ClientInfo;
import server.ServerCommunicator;

public class Game implements Runnable {

    private List<ClientInfo> players;
    
    public ClientInfo winner;
    public boolean isRanked;
    
    public Game(List<ClientInfo> players, boolean isRanked) {
        this.players = players;
        this.isRanked = isRanked;
    }

    public List<ClientInfo> getPlayers() {
        return this.players;
    }

    private void greetPlayers() throws IOException {

        String playersNames = String.join("\n", this.players.stream()
                                     .map(player -> player.name)
                                     .collect(Collectors.toList()));
                                     

        for (ClientInfo player: players) {
            player.getCommunicator().write("Found game with players: \n" + playersNames + "\n");
        }

    }

    private void sendGameResult() throws IOException {

        for (ClientInfo player: players) {
            player.getCommunicator().write("Winner was: " + this.winner.name + "!");
        }

    }

    @Override
    public void run() {

        try { greetPlayers(); } 
        catch (IOException e) { e.printStackTrace(); }


        for (ClientInfo player: players) {

            ServerCommunicator playerCommunicator = player.getCommunicator();
            String playerInput;
            try {

                playerInput = playerCommunicator.readString();
                while (playerInput == null)  {
                    playerInput = playerCommunicator.readString();
                } 
                System.out.println("[GAME] - " + player.name + " input: " + playerInput);

            } catch (IOException e) {
                e.printStackTrace();
            } 


        }

        
        Random random = new Random();
        int randInt = random.nextInt(players.size());

        this.winner = players.get(randInt);

        if (isRanked) {
            
            for (ClientInfo player: players) {
                if (player.equals(winner)) player.elo += 10;
                else if (player.elo > 10) player.elo -= 10; 
            }

        }

        try { Thread.sleep(2000); } 
        catch (InterruptedException e) { e.printStackTrace(); }

        try { sendGameResult(); } 
        catch (IOException e) { e.printStackTrace(); }

    }
}