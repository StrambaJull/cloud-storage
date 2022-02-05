package com.geekbrains.cloud.files;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class FilesHandler implements Runnable {

    private static final int SIZE = 256;
    private Path clientDir; //клиентская директория на сервере
    private DataInputStream is;
    private DataOutputStream os;
    private byte[] buf;

    public FilesHandler(Socket socket) throws IOException { //инициализация сокета
        clientDir = Paths.get("src/main/resources/com/geekbrains/cloud/serverDir");
        is = new DataInputStream(socket.getInputStream());
        os = new DataOutputStream(socket.getOutputStream());
        buf = new byte[SIZE];
        sendServerFiles(); //когда подключился клиент сразу отдаем ему список файлов
    }

    /*метод, отправляющий клиенту список файлов, хранящихся на сервере*/
    public void sendServerFiles() throws IOException {
        List<String> files = Files.list(clientDir) //создаем переменную в которую будем записывать список имен файлов
                .map(p -> p.getFileName().toString()) //открываем поток, получаем имя файла и записываем его в map
                .collect(Collectors.toList()); //преобразуем в список

        os.writeUTF("#list#");
        os.writeInt(files.size()); //по установленному протоколу передаем клиенту размер списка файлов
        for (String file:files) { //для каждого наименования в полученном списке
            os.writeUTF(file); //передаем клиенту имя файла
            os.flush();
        }
    }
/*обработка входящего потока*/
    @Override
    public void run() {
        try {
            while (true){
                String command = is.readUTF(); //читаем из входящего потока команду
                if (command.equals("#file#")){
                    String fileName = is.readUTF(); //получили имя файла из входящего потока
                    long size = is.readLong(); //получили размер файла
                    try(OutputStream fos = new FileOutputStream(clientDir.resolve(fileName).toFile())){//открыли поток для записи данных в файл
                        for(int i=0; i< (size + SIZE -1)/SIZE; i++){//количество итераций цикла будет кратно размеру буфера (size + SIZE -1)/SIZE
                            int readBytes = is.read(buf); //заполняем массив байтов из входящего потока и запоминаем количество прочитанных байтов
                            fos.write(buf,0,readBytes); //записываем в файл прочитанное количество байтов (следующую партию байтов допишет в конец файла)
                        }
                        sendServerFiles(); //если пришла команда на получение списка файлов клиента на сервере
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                }
                else if (command.equals("#get_file#")){
        /*
        1. определить команду, которую передаем на сервер
        2. передаем имя файла
        3. сколько байт будет файл: для этого сначала надо объявить переменную для хранения клиентской дирректории, потом проинициализировать ее при загрузке;
        */
                    String fileName = is.readUTF(); //вычитываем из потока имя файла
                    os.writeUTF("#file#"); //передаем команду на клиент
                    os.writeUTF(fileName); //передаем имя загружаемого файла
                    //..... определяем размер файла
                    Path file = clientDir.resolve(fileName); //получаем путь к файлу
                    long size = Files.size(file); //получаем размер файла;
                    byte[] bytes = Files.readAllBytes(file);//считываем файл в массив байтов
                    os.writeLong(size); //передаем размер файла;
                    os.write(bytes); //передаем массив байт файла
                    os.flush();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
