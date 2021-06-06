import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Optional;
import java.util.ResourceBundle;

//общий контроллер, который следит за все таблицей и кнопками манипуляции данных
public class Controller implements Initializable {

    @FXML
    VBox leftPanel, rightPanel;

    @FXML
    VBox mainPanel;

    public static ClientConnection clientConnection;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        this.clientConnection = new ClientConnection();
        try {
            clientConnection.init(this);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void btnExitAction(ActionEvent actionEvent) {
        Platform.exit();
        clientConnection.closeConnection();
    }


    public void copyBtnAction(ActionEvent actionEvent) {
        PanelController leftPC = (PanelController) leftPanel.getProperties().get("ctl");
        PanelController rightPC = (PanelController) rightPanel.getProperties().get("ctl");

        if (leftPC.getSelectedFileName() == null && rightPC.getSelectedFileName() == null) {
            Alert alert = new Alert(Alert.AlertType.WARNING, "Ни один файл не был выбран", ButtonType.OK);
            alert.showAndWait();
            return;
        }
        PanelController scrPC = null;
        PanelController dstPC = null;
        if (leftPC.getSelectedFileName() != null) {
            scrPC = leftPC;
            dstPC = rightPC;
        }
        if (rightPC.getSelectedFileName() != null) {
            scrPC = rightPC;
            dstPC = leftPC;
        }


        Path srcPath = Path.of(scrPC.getCurrentPath(), scrPC.getSelectedFileName());
        Path dstPath = Path.of(dstPC.getCurrentPath()).resolve(srcPath.getFileName());
        System.out.println(srcPath);
        System.out.println(dstPath);

        if (!srcPath.toString().startsWith("server") && !dstPath.toString().startsWith("server")) {
            System.out.println(" local copy ");
            try {
                Files.copy(srcPath, dstPath);
                dstPC.updateList(Path.of(dstPC.getCurrentPath()));
            } catch (IOException e) {
                Alert alert = new Alert(Alert.AlertType.WARNING, "Такой файл уже имеется", ButtonType.OK);
                alert.showAndWait();
            }
        }

        if (!srcPath.toString().startsWith("server") && dstPath.toString().contains("server")) {
            clientConnection.sendMessage(new FileToSend(srcPath));
        }
        if (srcPath.toString().contains("server") && !dstPath.toString().startsWith("server")) {
            clientConnection.sendMessage("download ".concat(leftPC.getSelectedFileName()));
        }
    }

    public void btnRemove(ActionEvent actionEvent) {
        ButtonType yes = new ButtonType("Yes");
        ButtonType no = new ButtonType("No");

        Alert alert = new Alert(Alert.AlertType.WARNING, "Вы действительно хотите удалить?", yes, no);
        Optional<ButtonType> option = alert.showAndWait();

        if (option.get() == null) {
            return;
        } else if (option.get() == no) {
            return;
        } else if (option.get() == yes) {
            PanelController leftPC = (PanelController) leftPanel.getProperties().get("ctl");
            PanelController rightPC = (PanelController) rightPanel.getProperties().get("ctl");
            if (leftPC.getSelectedFileName() != null && leftPC.pathField.getText().contains("server")) {
                System.out.println("remove file");
                clientConnection.sendMessage("delete " + leftPC.getSelectedFileName());
            }
            if (leftPC.getSelectedFileName() != null || rightPC.getSelectedFileName() != null
                    && !leftPC.pathField.getText().contains("server") && !rightPC.pathField.getText().contains("server")) {
                System.out.println("remove local file");
                if (leftPC.getSelectedFileName() != null) {
                    deleteLocalFiles(leftPC);
                }
                if (rightPC.getSelectedFileName() != null) {
                    deleteLocalFiles(rightPC);
                }
            }
        }
    }

    private void deleteLocalFiles(PanelController PC) {
        Path pathForDelete = Path.of(PC.pathField.getText(), PC.getSelectedFileName());
        System.out.println(pathForDelete.toString() + " delete filese local");
        try {
            Files.walkFileTree(pathForDelete, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
            PC.updateList(pathForDelete.getParent());
        } catch (IOException ignored) {
        }
    }


    public void renameBtnAction(ActionEvent actionEvent) {
        PanelController leftPC = (PanelController) leftPanel.getProperties().get("ctl");
        PanelController rightPC = (PanelController) rightPanel.getProperties().get("ctl");
        boolean isFocusedRightPanel = false;
        boolean isFocusedLeftPanel = false;
        boolean sendToServer = false;
        Path oldPath = null;
        if (leftPC.getSelectedFileName() != null) {
            oldPath = Path.of(leftPC.pathField.getText(), leftPC.getSelectedFileName());
            isFocusedLeftPanel = true;
        } else if (rightPC.getSelectedFileName() != null) {
            oldPath = Path.of(rightPC.pathField.getText(), rightPC.getSelectedFileName());
            isFocusedRightPanel = true;
        }
        if (leftPC.getSelectedFileName() != null && leftPC.pathField.getText().startsWith("server")) {
            sendToServer = true;
        }
        Stage stage = new Stage();
        Label lbl = new Label();
        TextField textField = new TextField();
        textField.setPrefColumnCount(11);
        Button btn = new Button("Переименовать файл");
        Path finalOldPath = oldPath;
        boolean finalIsFocusedRightPanel = isFocusedRightPanel;
        boolean finalIsFocusedLeftPanel = isFocusedLeftPanel;
        boolean finalSendToServer = sendToServer;
        btn.setOnAction(event -> {
            System.out.println("text from textField : " + textField.getText());

            if (finalSendToServer) {
                System.out.println("remove file");
                clientConnection.sendMessage("rename " + finalOldPath.getFileName() +" " + textField.getText());
                stage.close();
                return;
            }
            if (!leftPC.pathField.getText().startsWith("server") && !rightPC.pathField.getText().startsWith("server")) {
                System.out.println("rename local file");
                renameFile(finalIsFocusedLeftPanel, finalIsFocusedRightPanel,finalOldPath, textField.getText());
                stage.close();
            }
        });
        stage.close();
        FlowPane root = new FlowPane(Orientation.VERTICAL, 10, 10, textField, btn, lbl);
        Scene scene = new Scene(root, 250, 100);
        stage.setScene(scene);
        stage.setTitle("Введите название файла");
        stage.show();
    }

    private void renameFile(boolean isleftPanel, boolean isrightPanel, Path oldPath, String newName) {
        PanelController leftPC = (PanelController) leftPanel.getProperties().get("ctl");
        PanelController rightPC = (PanelController) rightPanel.getProperties().get("ctl");
        Path newPath = Path.of(oldPath.getParent().toString(), newName);
        try {
            Files.copy(oldPath, newPath);
            Files.delete(oldPath);
            if(isleftPanel) {
                leftPC.updateList(oldPath.getParent());
            }
            if(isrightPanel) {
                rightPC.updateList(oldPath.getParent());
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
