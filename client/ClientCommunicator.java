package client;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.Scanner;


public class ClientCommunicator {
    
    private SocketChannel channel;
    private Scanner scanner;

    public ClientCommunicator(SocketChannel channel) throws IOException {
        this.channel = channel;
        this.scanner = new Scanner(System.in);
    }

    public String getUserInput(String message) {
        System.out.print(message);
        return scanner.nextLine();
    }

    public int read(ByteBuffer readBuffer) throws IOException {

        int bytesRead = channel.read(readBuffer);    
        
        while (bytesRead <= 0) {
            bytesRead = channel.read(readBuffer);
        }
        
        readBuffer.flip();
        return bytesRead;

    }

    public void write(String message) throws IOException {

        ByteBuffer writeBuffer = ByteBuffer.wrap(message.getBytes());
        channel.write(writeBuffer);

    }

    public void close() throws IOException { 
        channel.close();
        scanner.close();
    }


    public String readString() throws IOException {
        
        ByteBuffer readBuffer = ByteBuffer.allocate(1024);
        read(readBuffer);

        Charset charset = Charset.forName("UTF-8");
        return charset.decode(readBuffer).toString();

    }
    
}