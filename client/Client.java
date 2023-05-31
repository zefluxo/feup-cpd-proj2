package client;

import java.io.*;
import java.nio.channels.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.net.InetSocketAddress;
import utils.Utils;

import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.List;

public class Client {

    private static String LOGIN_FAIL_MSG = "Failed to login, re-input your credentials.";
    private static String REGISTER_FAIL_MSG = "Failed to register, re-input your credentials.";
    private static String INVALID_TOKEN_MSG = "Invalid token";
    private static Path TOKEN_PATH = Paths.get("./client/token.txt");

    private ClientCommunicator communicator;
    private String name;

    public Client(String host, int port) throws IOException, InterruptedException, NoSuchAlgorithmException, InvalidKeySpecException {

        try {

            SocketChannel socketChannel = SocketChannel.open();
            socketChannel.connect(new InetSocketAddress(host, port));

            socketChannel.configureBlocking(false);

            this.communicator = new ClientCommunicator(socketChannel);
            
            int retries = 0;
            while (!socketChannel.finishConnect()) {
                
                if (retries == 5) {
                    System.err.println("Failed to connect to server.");
                    System.exit(-1);
                }   
                retries++;
                Thread.sleep(1000); 
                
            }
            
            retries = 0;
            run();

        } catch (IOException e) {

            System.err.println("I/O error " + e.getMessage());
            throw new IOException();

        }


    }

    public void run() throws IOException, NoSuchAlgorithmException, InvalidKeySpecException, InterruptedException {  
        
        if (!hasToken()) { authenticate(); }
        else { handleToken(); }

        Utils.clearConsole();

        System.out.println("In queue, finding a match...");

        String foundGame = communicator.readString();
        System.out.println(foundGame);

        communicator.write(communicator.getUserInput("Send message to server: "));

        String wonGame = communicator.readString();
        System.out.println(wonGame);

        String input = "";
        
        Utils.clearConsole();
        while (true) {
            
            Utils.clearConsole();
            input = communicator.getUserInput("What type of game do you want to play?\n\n1.) Simple\n2.) Ranked\n3.) Quit\n\nOpt: ");

            if (!(input.equals("1") || input.equals("2") || input.equals("3"))) {
                System.out.println("Not a valid input... Try again!\n");
                continue;
            }

            communicator.write(input);
            if (input.equals("3")) exit();

            Utils.clearConsole();

            System.out.println("In queue, finding a match...");
            
            foundGame = communicator.readString();
            System.out.println(foundGame);

            communicator.write(communicator.getUserInput("Send message to server: "));
            
            wonGame = communicator.readString();
            System.out.println(wonGame);
        }

    }

    private boolean hasToken() { return Files.exists(TOKEN_PATH); }
    
    private void handleToken() throws IOException, NoSuchAlgorithmException, InvalidKeySpecException, InterruptedException {

        communicator.write(Files.readString(TOKEN_PATH));
        
        String serverResponse = communicator.readString();
        if (serverResponse.equals(INVALID_TOKEN_MSG)) {
            Files.delete(TOKEN_PATH);
            authenticate();
        } else {
            System.out.println(serverResponse);
        }
        

    }

    private void authenticate() throws IOException, NoSuchAlgorithmException, InvalidKeySpecException, InterruptedException {

        boolean notLoggedIn = true;
        List<String> firstValidInputs = Arrays.asList(new String[]{"1", "2", "3"});
        List<String> secondValidInputs = Arrays.asList(new String[]{"1", "2"});

        Utils.clearConsole();

        while (notLoggedIn) {
            
            String firstInput = communicator.getUserInput("Welcome!\nWhat type of game do you want to play?\n\n1.) Simple\n2.) Ranked\n3.) Quit\n\nOpt: ");

            Utils.clearConsole();
            if (!firstValidInputs.contains(firstInput)) {
                System.out.println("Not a valid input... Try again!\n");
                continue;
            }

            if (firstInput.equals("3")) {
                communicator.write(firstInput);
                exit();
            }

            String secondInput = communicator.getUserInput("1.) Login\n2.) Register\n\nOpt: ");
            
            Utils.clearConsole();
            if (!secondValidInputs.contains(secondInput)) {
                System.out.println("Not a valid input... Try again!\n");
                continue;
            }
            
            
            String credentials = getCredentials();  
            if (credentials == null) {
                System.out.println("Blank characters are not allowed\n");
                continue;
            }
            
            String serverResponse = "";

            communicator.write(String.format("%s:%s:%s", firstInput, secondInput, credentials));
            serverResponse = communicator.readString();

            if (serverResponse.equals(LOGIN_FAIL_MSG) || serverResponse.equals(REGISTER_FAIL_MSG)) {
                System.out.println(serverResponse + "\n");
                continue;
            }

            this.name = Utils.getUsername(credentials);
            notLoggedIn = false;
            createTokenFile(serverResponse);

        }

    }

    private String getCredentials() {
        String username = communicator.getUserInput("Username: ");
        String password = communicator.getUserInput("Password: ");

        String credentials = String.format("%s/%s", username, password);

        Utils.clearConsole();

        if (credentials.split("/").length < 2) return null;
        return credentials;
    }

    private void createTokenFile(String token) throws IOException {

        Path tokenPath = Paths.get("./client/token.txt");
        if (!Files.exists(tokenPath)) Files.createFile(tokenPath);

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(tokenPath.toFile()))) {
            writer.write(token);
        }

    } 

    private void exit() throws IOException {
        System.out.println("See you next time!\n");
        communicator.close();
        
        if (hasToken()) Files.delete(TOKEN_PATH);
        
        System.exit(0);
    }
   
}