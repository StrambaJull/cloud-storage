package com.geekbrains.cloud.files;

import com.geekbrains.cloud.chat.Handler;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class FilesServer {

    public static void main(String[] args) throws IOException {
        ServerSocket server = null;

        try {
            server = new ServerSocket(8191);
        } catch (IOException e) {
            System.out.println("Can't setup server on this port number.");
        }

        while (true){
            Socket serverSocket = server.accept(); //запускаем сервер
            System.out.println("New client connected...");
            new Thread(new FilesHandler(serverSocket)).start();
        }
    }

}
