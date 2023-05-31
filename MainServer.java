import java.io.*;

import server.Server;

public class MainServer {
 
    public static void main(String[] args) throws IOException {
        if (args.length != 1)  {
            System.err.println("MainServer <port>");
            return;
        }
 
        int port = Integer.parseInt(args[0]);
        new Server(port);
    }

}