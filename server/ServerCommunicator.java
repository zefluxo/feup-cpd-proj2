package server;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;

public class ServerCommunicator {
    
    private SelectionKey key;
    
    public void setKey(SelectionKey key) {
        this.key = key;
    }

    public SelectionKey getKey() {
        return key;
    }

    private SocketChannel channel;

    public SocketChannel getChannel() {
        return channel;
    }

    public ServerCommunicator(SelectionKey key) throws IOException {
        this.key = key;
        this.channel = (SocketChannel) key.channel();
    }

    public int read(ByteBuffer readBuffer) throws IOException {

        int bytesRead = channel.read(readBuffer);
        
        readBuffer.flip();
        if (bytesRead == -1) {
            System.err.println("[SERVER] - Closed channel: " + channel.getRemoteAddress());
        }

        return bytesRead;

    }

    public void write(String message) throws IOException {

        ByteBuffer writeBuffer = ByteBuffer.wrap(message.getBytes());
        channel.write(writeBuffer);

    }

    public void close() throws IOException { 
        key.cancel();
        channel.close();
    }


    public void cancelKey() {
        key.cancel();
        key = null;
    }

    public String readString() throws IOException {
        
        ByteBuffer readBuffer = ByteBuffer.allocate(1024);
        if (read(readBuffer) <= 0) {
            return null;   
        }

        Charset charset = Charset.forName("UTF-8");
        return charset.decode(readBuffer).toString();

    }

    public boolean isConnected() {
        return this.channel.isConnected();
    }
    
}