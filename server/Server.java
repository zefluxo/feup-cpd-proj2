package server;

import java.io.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import game.Game;
import utils.PasswordHashes;

public class Server {

    private static String db = "./docs/db_user.csv";

    private static int BASE_ELO = 100;
    private static int PLAYERS_PER_GAME = 2;
    private static int ELO_RELAX_PERIOD = 5;
    private static int ELO_RELAX_QUANTITY = 50;
    private static int DISCONNECT_PERIOD = 30;

    private Thread connectionListener;
    private Thread disconnectHandler;
    private Thread eloHandler;
    private Thread simpleMatchmakingThread;
    private Thread rankedMatchmakingThread;

    private ServerSocketChannel serverSocketChannel;
    private Selector selector;
    private List<ServerCommunicator> connections;
    private List<ClientInfo> loggedInClients;
    private List<ClientInfo> simplePlayerQueue;
    private List<ClientInfo> rankedPlayerQueue;
    private ThreadPool onlineGames;

    public Server(int port) throws IOException {         
            
            initServer(port);
            initSelector();
            this.connections = new ArrayList<>();
            this.loggedInClients = new ArrayList<>();
            this.simplePlayerQueue = new LinkedList<>();
            this.rankedPlayerQueue = new ArrayList<>();
            this.onlineGames = new ThreadPool(5);

            System.out.println("[SERVER] - Server created, starting...");
            run();

    }

    private void initServer(int port) throws IOException {
        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.bind(new InetSocketAddress(port));
    }

    private void initSelector() throws IOException {
        this.selector = Selector.open();
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
    }

    private void createConnectionListener() {
        this.connectionListener = new Thread(() -> {    
            try {

                while (!Thread.currentThread().isInterrupted()) {

                    selector.selectNow();

                    Set<SelectionKey> keys = selector.selectedKeys();
                    for (SelectionKey key: keys)  {
                
                        if (!key.isValid()) continue;
                        if (key.isAcceptable()) {
                            
                            ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
                            SocketChannel clientChannel = serverChannel.accept();
                            
                            if (clientChannel == null) continue;
                            System.out.println("[SERVER] - New client connected: " + clientChannel.getRemoteAddress());

                            clientChannel.configureBlocking(false);

                            SelectionKey newClientKey = clientChannel.register(selector, SelectionKey.OP_READ);                            
                            ServerCommunicator newCommunicator = new ServerCommunicator(newClientKey);
                           
                            synchronized (connections) {
                                connections.add(newCommunicator);
                            }
                        
                
                        } else if (key.isReadable()) {
                            
                            System.out.println("[SERVER] - Reading from: " + ((SocketChannel) key.channel()).getRemoteAddress());

                            ServerCommunicator communicator = getCommunicator(key);
                            if (communicator == null) continue;

                            ClientInfo client = getClient(key);
              
                            String clientInput = communicator.readString();
                            if (clientInput == null) {
                                communicator.close();
                                continue;
                            }

                            ClientInfo existingClient;
                            if ((existingClient = checkToken(clientInput)) != null) {
                                existingClient.setCommunicator(communicator);
                                communicator.write("Reconnected, back in queue.");
                                continue;
                            } 

                            String[] clientResponse = clientInput.split(":", 2);
                            if (clientResponse.length < 2) {

                                if (client != null && client.name != null) { 
                                    try {
                                        
                                        int choice = Integer.parseInt(clientInput);
        
                                        if (choice == 1) {

                                            synchronized (simplePlayerQueue) {
                                                System.out.println("[SERVER] - Adding new player to simple queue: " + client.toString());
                                                simplePlayerQueue.add(client);
                                                simplePlayerQueue.notify();
                                            }
    
                                        } else if (choice == 2) {

                                            synchronized (rankedPlayerQueue) {
                                                System.out.println("[SERVER] - Adding new player to ranked queue: " + client.toString());
                                                rankedPlayerQueue.add(client);
                                                rankedPlayerQueue.notify();
                                            }
    
                                        } else if (choice == 3) client.getCommunicator().close();
    
    
                                    } catch (NumberFormatException e) {
                                        e.printStackTrace();
                                    }

                                    continue;
                                    
                                }
                                communicator.write("Invalid token");
                                continue;
                            }

                            String gameMode = clientResponse[0];
                            if (gameMode.equals("3")) {
                                communicator.close();
                                continue;
                            }

                            String credentials = clientResponse[1];

                            authenticate(communicator, credentials, gameMode);
                            continue;

                        }
                    }
                    keys.clear();
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        connectionListener.start();

    }

    private void createSimpleMatchmaking() {
        this.simpleMatchmakingThread = new Thread(() -> {

            while (!Thread.currentThread().isInterrupted()) {
                
                synchronized (simplePlayerQueue) {
                    
                    while (simplePlayerQueue.size() < PLAYERS_PER_GAME) {
                        try { simplePlayerQueue.wait(); } 
                        catch (InterruptedException e) { e.printStackTrace(); }
                    }

                    List<ClientInfo> players = getNormalPlayers();
                    if (players.size() < PLAYERS_PER_GAME) continue;
                    
                    Game game = new Game(players, false);
                    onlineGames.submit(game);

                    System.out.println("[GAME] - Added game to queue");

                }

            }

        });
        simpleMatchmakingThread.start();
    }

    private void createRankedMatchmaking() {
        this.rankedMatchmakingThread = new Thread(() -> {

            while (!Thread.currentThread().isInterrupted()) {
                
                synchronized (rankedPlayerQueue) {
                    
                    while (rankedPlayerQueue.size() < PLAYERS_PER_GAME) {
                        try { rankedPlayerQueue.wait(); } 
                        catch (InterruptedException e) { e.printStackTrace(); }
                    }

                    List<ClientInfo> players = getRankedPlayers();
                    if (players == null) continue;

                    
                    Game game = new Game(players, true);
                    onlineGames.submit(game);
                    
                    System.out.println("[GAME] - Starting a new ranked game!");

                }

            }

        });
        rankedMatchmakingThread.start();
    }

    private void createDisconnectHandler() {
        this.disconnectHandler = new Thread(() -> {

            while (!Thread.currentThread().isInterrupted()) {

                synchronized (connections) {

                    Iterator<ServerCommunicator> it = connections.iterator();
                    while (it.hasNext()) {

                        ServerCommunicator communicator = it.next();
                        if (!communicator.isConnected()) {
                            it.remove();
                        }
                    }

                }

                synchronized (loggedInClients) {
                    Iterator<ClientInfo> it = loggedInClients.iterator();
                    while (it.hasNext()) {

                        ClientInfo client = it.next();
                        if (!client.getCommunicator().isConnected()) {

                            if (client.disconnectedTime == 0) {
                                client.disconnectedTime = System.currentTimeMillis();
                                continue;
                            }

                            if (client.getTimeSinceDisconnect() > DISCONNECT_PERIOD) {
                                it.remove();
                                System.out.println("[SERVER] - Removed " + client.toString() + " from logged in clients");
                            } 

                        } else if (client.disconnectedTime != 0) client.disconnectedTime = 0;
                    }

                }

                synchronized (simplePlayerQueue) {
                
                    Iterator<ClientInfo> it = simplePlayerQueue.iterator();
                    while (it.hasNext()) {

                        ClientInfo client = it.next();
                        if (!client.getCommunicator().isConnected()) {

                            if (client.disconnectedTime == 0) {
                                client.disconnectedTime = System.currentTimeMillis();
                                continue;
                            }

                            if (client.getTimeSinceDisconnect() > DISCONNECT_PERIOD) {
                                it.remove();
                                System.out.println("[SERVER] - Removed " + client.toString() + " from the simple queue");
                            } 

                        } else if (client.disconnectedTime != 0) client.disconnectedTime = 0;

                    }

                }

                synchronized (rankedPlayerQueue) {
                
                    Iterator<ClientInfo> it = rankedPlayerQueue.iterator();
                    while (it.hasNext()) {

                        ClientInfo client = it.next();
                        if (!client.getCommunicator().isConnected()) {

                            if (client.disconnectedTime == 0) {
                                client.disconnectedTime = System.currentTimeMillis();
                                continue;
                            }

                            if (client.getTimeSinceDisconnect() > DISCONNECT_PERIOD) {
                                it.remove();
                                System.out.println("[SERVER] - Removed " + client.toString() + " from the ranked queue");
                            } 

                        } else if (client.disconnectedTime != 0) client.disconnectedTime = 0;
                        
                    }

                }

            }

        });
        disconnectHandler.start();
    }
 
    private void createEloHandler() {

        this.eloHandler = new Thread(() -> {

            while (!Thread.currentThread().isInterrupted()) {

                synchronized (onlineGames) {

                    Iterator<Runnable> iterator = onlineGames.completedTasks.iterator();
                    while (iterator.hasNext()) {
                        
                        Game game = (Game) iterator.next();

                        if (game.winner == null) continue;
                        System.out.println("[GAME] - Winner: " + game.winner.toString());
                        List<ClientInfo> players = game.getPlayers();

                        for (ClientInfo player: players) {

                            SocketChannel playerChannel = player.getCommunicator().getChannel();
                            try {

                                SelectionKey newKey = playerChannel.register(selector, SelectionKey.OP_READ);
                                player.getCommunicator().setKey(newKey);


                            } catch (ClosedChannelException e) {
                                e.printStackTrace();
                            }

                        }



                        if (!game.isRanked)  {
                            iterator.remove();
                            continue;
                        }


                        try {

                            Path path = Path.of(db);
                            List<String> lines = Files.readAllLines(path);
                
                            for (ClientInfo player: players) {

                                for (int i = 0; i < lines.size(); i++) {
    
                                    String[] values = lines.get(i).split(",");
                                    if (values[0].equals(player.name)) {
    
                                        String password = values[1];
                                        lines.set(i, String.format("%s,%s,%d", player.name, password, player.elo));
                                        break;

                                    } 
    
    
                                }
                            }

                            Files.write(path, lines, StandardCharsets.UTF_8);
                
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                        iterator.remove();
                       
                    }

                }

            }

        });
        eloHandler.start();

    }

    private ClientInfo checkToken(String input) {

        synchronized (simplePlayerQueue) {

            Iterator<ClientInfo> it = simplePlayerQueue.iterator();
            while (it.hasNext()) {

                ClientInfo curr = it.next();
                if (curr.getToken().equals(input)) {
                    return curr;
                }

            }

        }

        synchronized (rankedPlayerQueue) {

            Iterator<ClientInfo> it = rankedPlayerQueue.iterator();
            while (it.hasNext()) {

                ClientInfo curr = it.next();
                if (curr.getToken().equals(input)) {
                    return curr;
                }

            }
        }

        return null;

    }

    private void authenticate(ServerCommunicator communicator, String input, String gameMode) throws IOException {
        String delimiter = ":";
        String[] clientResponse = input.split(delimiter);
       
        String choice = clientResponse[0];

        String credentialString = clientResponse[1];
        String credentialDelimiter = "/";  

        String[] credentials = credentialString.split(credentialDelimiter);
        
        if (credentials.length < 2) return;
        
        String username = credentials[0];
        String password = credentials[1];

        String elo = "";
        
        switch (choice) {
            
            case "1": {

                elo = validLoginCredentials(credentials);

                synchronized (loggedInClients) {
                    Iterator<ClientInfo> it = loggedInClients.iterator();
                    while (it.hasNext()) {
        
                        ClientInfo curr = it.next();
                        if (curr.name.equals(username)) {
                            communicator.write("Failed to login, re-input your credentials.");
                            return;
                        }
        
                    }
                }

                if (elo.equals("INVALID")) {
                    communicator.write("Failed to login, re-input your credentials.");
                    return;
                }          

                break;
            
            }
            
            case "2": {

                elo = validRegisterCredentials(credentials);
                
                if (elo.equals("INVALID")) {
                    communicator.write("Failed to register, re-input your credentials.");
                    return;
                }

                FileWriter db_writer = new FileWriter(db, true);
                try (BufferedWriter writer = new BufferedWriter(db_writer)) {
                    writer.write(String.format("%s,%s,%s\n", username, PasswordHashes.hash(password), elo));
                    db_writer.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                break;
                
            }

            default: break;
            
        }
        
        ClientInfo newPlayer = new ClientInfo(communicator, username, Integer.parseInt(elo));
        communicator.write(newPlayer.getToken());

        synchronized (loggedInClients) {
            loggedInClients.add(newPlayer);
        }

        boolean isSimpleGame = gameMode.equals("1");
        if (isSimpleGame) {
            synchronized (this.simplePlayerQueue) {
                System.out.println("[SERVER] - Adding new player to simple queue: " + newPlayer.toString());
                simplePlayerQueue.add(newPlayer);
                simplePlayerQueue.notify();
            }
        } else {
            synchronized (this.rankedPlayerQueue) {
                System.out.println("[SERVER] - Adding new player to ranked queue: " + newPlayer.toString());
                rankedPlayerQueue.add(newPlayer);
                rankedPlayerQueue.notify();
            }
        }

    }

    public String validLoginCredentials(String[] credentials) throws FileNotFoundException {

        String username = credentials[0];
        String password = credentials[1];

        FileReader db_reader = new FileReader(db);
        try (BufferedReader reader = new BufferedReader(db_reader)) {

            String line;
            while ((line = reader.readLine()) != null) {

                String[] values = line.split(",");
                String elo = values[2];

                boolean validUsername = values[0].equals(username);
                boolean equivalentHash = PasswordHashes.verify(password, values[1]);

                if (validUsername && equivalentHash) {
                    return elo;
                }

            }
            db_reader.close();

        } catch (IOException e) {
            e.printStackTrace();
        }

        return "INVALID";
        
    }


    public String validRegisterCredentials(String[] credentials) throws FileNotFoundException {

        String username = credentials[0];

        FileReader db_reader = new FileReader(db);
        try (BufferedReader reader = new BufferedReader(db_reader)) {

            String line;
            while ((line = reader.readLine()) != null) {

                String[] values = line.split(",");
                boolean invalidUsername = values[0].equals(username);
                if (invalidUsername) return "INVALID";

            }
            db_reader.close();

        } catch (IOException e) {
            e.printStackTrace();
        }

        return Integer.toString(BASE_ELO);
        
    }

    private ServerCommunicator getCommunicator(SelectionKey key) {
        for (ServerCommunicator com: connections) {
            if (com.getKey() == key) return com;
        }

        return null;
    }

    private ClientInfo getClient(SelectionKey key) {
        for (ClientInfo client: loggedInClients) {
            if (client.getCommunicator().getKey() == key) return client;
        }

        return null;
    }

    private List<ClientInfo> getNormalPlayers() {
        List<ClientInfo> players = new ArrayList<>();

        Iterator<ClientInfo> it = simplePlayerQueue.iterator();
        while (it.hasNext()) {
            
            ClientInfo player = it.next();    
            if (player != null && player.getCommunicator().isConnected()) {
                detachSocketChannel(player);
                players.add(player);
                it.remove();
            }
        
        }

        return players;
    }

    private List<ClientInfo> getRankedPlayers() {

        List<ClientInfo> players = new ArrayList<>();

        for (ClientInfo qPlayer: this.rankedPlayerQueue) {

            if (!qPlayer.getCommunicator().isConnected()) continue;
            players.add(qPlayer);

            int timeElapsed = qPlayer.getTimeElapsed();
            int numRelaxations = timeElapsed / ELO_RELAX_PERIOD;
            int eloRange = ELO_RELAX_QUANTITY * (numRelaxations * numRelaxations);

            int lhs = Math.max(qPlayer.elo - eloRange, 0);
            int rhs = qPlayer.elo + eloRange;

            
            for (ClientInfo qPlayer2: this.rankedPlayerQueue) {
                
                if (players.size() == PLAYERS_PER_GAME) break;
                if (qPlayer2.equals(qPlayer)) continue;
                
                int elo = qPlayer2.elo;
                if (elo >= lhs && elo <= rhs) {
        
                    if (qPlayer2.getCommunicator().isConnected()) players.add(qPlayer2);
        
                }

            }

            if (players.size() == PLAYERS_PER_GAME) {
                
                for (ClientInfo player: players) {
                    detachSocketChannel(player);
                    this.rankedPlayerQueue.remove(player);
                }
                
                return players;

            }

            players.clear();

        }

        return null;

    }

    private void detachSocketChannel(ClientInfo client) {

        ServerCommunicator communicator = client.getCommunicator();
        communicator.cancelKey();

    }


    public void run() throws IOException {

        createConnectionListener();
        createDisconnectHandler();
        createSimpleMatchmaking();
        createRankedMatchmaking();
        createEloHandler();
    
    }
}

