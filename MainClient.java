

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

import client.Client;

public class MainClient {
    
    public static void main(String[] args) throws IOException, InterruptedException, NoSuchAlgorithmException, InvalidKeySpecException {

        if (args.length != 2) {
            System.err.println("MainClient <host> <port>");
            return;
        }

        String host = args[0];
        int port = Integer.parseInt(args[1]);

        new Client(host, port);
        
    }

}
