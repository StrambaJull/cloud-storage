package com.geekbrains.cloud.files;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.Initializable;
import javafx.scene.control.ListView;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ResourceBundle;

public class FilesClient implements Initializable {
    private static final int SIZE = 256;
    private Path clientDir; // объявляем переменную в которой будем хранить путь к клиентской дирректории
    public ListView<String> clientView;
    public ListView<String> serverView;
    private DataOutputStream os; //объявляем переменные для выходного канала
    private DataInputStream is; //объявляем переменные для входного канала
    private byte[] buf; //объявляем переменную - буфер для полученного файла

    //....... в этой процедуре обрабатываем то, что получаем во входном потоке
    private void readLoop(){
        try{
            while (true){
                String command = is.readUTF(); //ждем команды
                //от сервера можем получить только 2 команды. Все эти команды должны быть поддержаны на стороне сервера.
                // Последовательность переданных элементов (количество файлов, имя файла) и последовательность их получения должна сохраняться
                if (command.equals("#list#")){ //получили список файлов
                    Platform.runLater(()-> serverView.getItems().clear());//очистим окно с файлами сервера,
                    // т.к. это выполняется в отдельном потоке (элементы ui выполняются не в нашем треде), то необходимо делать это через такую конструкцию
                    int filesCount = is.readInt(); //прочитаем из входящего потока количество передаваемых файлов
                    for (int i = 0; i < filesCount; i++){ //прочитаем их в цикле
                        String fileName = is.readUTF();
                        Platform.runLater(()-> serverView.getItems().add(fileName)); // добавим по одному в окно с файлами сервера
                    }
                }
                else if (command.equals("#file#")){ //получили файл
                    /*1. прочитаем имя файла; 2. прочитаем размер файла; 3. запишем сам файл используя буфер - байтовый массив*/
                    String fileName = is.readUTF();
                    long size = is.readLong();
                    //..... считываем байты файла из входящего потока с использованием буфера и записываем в файл
                    try (FileOutputStream fos = new FileOutputStream(clientDir.resolve(fileName).toFile())) { //открываем поток на запись файла
                        for (int i = 0; i<(size + SIZE -1)/SIZE; i++){ //количество итераций цикла будет кратно размеру буфера (size + SIZE -1)/SIZE
                            int readBytes = is.read(buf); // считываем байты в буфер и запоминаем какое количество байт прочитали
                            fos.write(buf, 0, readBytes); //записываем в файл байты из буфера от нуля до прочитанного количества
                        }
                    }
                    Platform.runLater(this::updateClientView);//обновляем клиентское view: вызываем метод обновления для конкретного экземпляра класса
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    private void updateClientView() {
        /*1. очистить view, 2. добавили новые через stream-api*/
        clientView.getItems().clear(); //почистили view;
        try {
            Files.list(clientDir) //добавили новые файлы через stream-api
                    .map (p -> p.getFileName().toString())
                    .forEach(f -> clientView.getItems().add(f));
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    //т.к. используем javafx и реализуем интерфейс Initializable, то должны реализовать метод, содержащийся в этом интерфейсе.
    //это значит, что прежде чем загрузить класс должна пройти инициализация
    // любая переменная класса должна быть проинициализирована здесь
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        try {
            buf = new byte[SIZE];
            clientDir = Paths.get("src/main/resources/com/geekbrains/cloud/clientsDir");//проинициализируем переменную с клиентской дирректорией
            updateClientView(); //обновили окно с клиентскими файлами
            //....... подключились к серверу
            Socket socket = new Socket("localhost", 8191);
            System.out.println("...Network created...");
            //....... инициализируем каналы для общения в сокете
            is = new DataInputStream(socket.getInputStream()); //входящий
            os = new DataOutputStream(socket.getOutputStream()); //исходящий
            Thread readThread = new Thread(this::readLoop);//для каждого цикла чтения открываем отдельный поток и выполняем определенные действия
            readThread.setDaemon(true);//устанавливаем для каждого потока признак Daemon чтобы потоки закрывались при закрытии программы
            readThread.start();//стартуем поток
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    //загрузить на сервер
    public void upload(ActionEvent actionEvent) throws IOException {
        /*
        1. определить команду, которую передаем на сервер
        2. передаем имя файла
        3. сколько байт будет файл: для этого сначала надо объявить переменную для хранения клиентской дирректории, потом проинициализировать ее при загрузке;
        */
        String fileName = clientView.getSelectionModel().getSelectedItem(); //определяем имя файла выделенное в окне со списком файлов на клиенте
        os.writeUTF("#file#"); //передаем команду на сервер
        os.writeUTF(fileName); //передаем имя выгружаемого файла
        //....... определяем размер файла
        Path file = clientDir.resolve(fileName); //получаем путь к файлу
        long size = Files.size(file); //получаем размер файла;
        byte[] bytes = Files.readAllBytes(file);//считываем файл в массив байтов
        os.writeLong(size); //передаем размер файла;
        os.write(bytes); //передаем массив байт файла
        os.flush();
    }
    //загрузить с сервера
    public void download(ActionEvent actionEvent) throws IOException {
        /*
        1. определить команду, которую передаем на сервер
        2. передаем имя файла
        */
        String fileName = serverView.getSelectionModel().getSelectedItem(); //определяем имя файла выделенное в окне со списком файлов на сервере
        os.writeUTF("#get_file#"); //передаем команду на сервер
        os.writeUTF(fileName); //передаем имя файла который хотим загрузить
    }

}
