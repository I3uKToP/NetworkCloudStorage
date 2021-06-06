
import io.netty.handler.codec.serialization.ObjectDecoderInputStream;
import io.netty.handler.codec.serialization.ObjectEncoderOutputStream;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

import java.io.IOException;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

public class ClientConnection {
    private Socket socket;
    private ObjectEncoderOutputStream oeos;
    private ObjectDecoderInputStream odis;
    protected static boolean isAuth;
    private boolean isConnected = true;
    public static final int PORT = 4006;
    public static List<FileInfo> fileInfoServerList = new ArrayList<>();
    public static Path pathRoot = Path.of("serverStorage");
    private static final int MAX_OBJ_SIZE = 1024 * 1024 * 100; // 100 mb

    public ClientConnection() {

    }

    public void init(Controller controller) throws IOException {
        this.socket = new Socket("localhost",PORT);
        this.oeos = new ObjectEncoderOutputStream(socket.getOutputStream(), MAX_OBJ_SIZE);
        this.odis = new ObjectDecoderInputStream(socket.getInputStream(), MAX_OBJ_SIZE);
        this.isAuth = false;




        new Thread(()->{
            while (isConnected) {
                try {
                    Object message = odis.readObject();
                    PanelController leftPC = (PanelController) controller.leftPanel.getProperties().get("ctl");
                    PanelController rightPC = (PanelController) controller.rightPanel.getProperties().get("ctl");
                    if (message != null) {
                        if( message instanceof List) {
                            fileInfoServerList = (List<FileInfo>) message;
                            leftPC.updateList(fileInfoServerList);

                        }
                        if (message instanceof FileInfo) {
                            FileInfo test = (FileInfo) message;
                            System.out.println(test.getFilename());
                        }
                        if (message instanceof FileToSend) {
                            System.out.println("получен файл ");
                            FileToSend file = (FileToSend) message;;
                            Path path = Paths.get(rightPC.pathField.getText() + "/"  + file.getFileName());
                            System.out.println(path + " path");
                            try {
                                if (Files.exists(path)) {
                                    Files.write(path, file.getData(), StandardOpenOption.TRUNCATE_EXISTING);
                                } else {
                                    Files.write(path, file.getData(), StandardOpenOption.CREATE);
                                }
                                rightPC.updateList(path.getParent());
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                        if (message.toString().contains("authOK")) {
                            System.out.println("authOK from client" );
                            isAuth = true;
                            System.out.println("auth client conn " + isAuth );
                            pathRoot = Path.of(pathRoot.toString(), message.toString().split(" ")[1]);
                            leftPC.pathField.setText(pathRoot.normalize().toString());
                        }
                        if ("WRONG".equals(message)) {
                            isAuth = false;
                        }

                    }
                    System.out.println("что пришло с сервера для дебага " + message);
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
                catch (IOException e) {
                    e.printStackTrace();
                }
            }

        }).start();
    }

    public void sendMessage(Object message) {
        try {
            oeos.writeObject(message);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void closeConnection() {
        try {
            isConnected = false;
            oeos.close();
            odis.close();
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
