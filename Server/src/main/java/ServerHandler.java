import Auth.Singleton;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ServerHandler extends ChannelInboundHandlerAdapter {
    public static List<FileInfo> fileInfoServerList = new ArrayList<>();
    private Path pathRoot = Path.of("serverStorage");
    private Path currentPath = Path.of("serverStorage");
    PreparedStatement preparedStatement = null;

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("client connected: " + ctx.channel());
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        String message = String.valueOf(msg);
        System.out.println(message);
        if ("auth".equals(message.split(" ")[0])){
            System.out.println("зашло в аторизацию на сервере");
            try {
                preparedStatement = Singleton.getConnection().prepareStatement("SELECT * FROM users WHERE login = ?");
                preparedStatement.setString(1, message.split(" ")[1]);
                ResultSet resultSet = preparedStatement.executeQuery();
                while (resultSet.next()) {
                    String pass = resultSet.getString(3);
                    if (pass.equals(message.split(" ")[2])) {
                        ctx.writeAndFlush("authOK " + message.split(" ")[1]);
                        System.out.println("authOK from server ");
                        Path pathForUser = Path.of(pathRoot.toString(), message.split(" ")[1]);
                        currentPath = pathForUser;
                        System.out.println(pathForUser + " pathForUser");
                        if (!Files.exists(pathForUser)) {
                            Files.createDirectories(pathForUser);

                        }
                        updateFilesList(ctx, pathForUser);
                    } else {
                        ctx.writeAndFlush("WRONG");
                    }
                }

            } catch (SQLException throwables) {
                throwables.printStackTrace();
//                LOGGER.log(Level.ERROR, throwables);

            } catch (ClassNotFoundException e) {
                e.printStackTrace();
//                LOGGER.log(Level.ERROR, e);
            }
        }
        if (message.contains("serverStorage")) {
            updateFilesList(ctx, pathRoot);
            pathRoot = currentPath;
        }
        if ("updateFiles".equals(message.split(" ")[0])) {
            currentPath = Path.of(currentPath.toString(), message.split(" ")[1]);
            updateFilesList(ctx,currentPath);
        }
        if ("parentDirectory".equals(message)) {
            System.out.println("parentDirectory on Server " + currentPath);
            currentPath = currentPath.getParent();
            System.out.println("parentDirectory send to client " + currentPath);
            updateFilesList(ctx,currentPath);
        }
        if (msg instanceof FileToSend) {
            System.out.println("Входящий файл");
            FileToSend file = (FileToSend) msg;
            Path path = Paths.get(currentPath + "/"  + file.getFileName());
            try {
                if (Files.exists(path)) {
                    Files.write(path, file.getData(), StandardOpenOption.TRUNCATE_EXISTING);
                } else {
                    Files.write(path, file.getData(), StandardOpenOption.CREATE);
                }
                updateFilesList(ctx,path.getParent());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if ("download".equals(message.split(" ",2)[0])) {
            ctx.writeAndFlush(new FileToSend(Path.of(currentPath.toString(), message.split(" ",2)[1])));
        }
        if ("delete".equals(message.split(" ")[0])) {
            Path pathForDelete = Path.of(currentPath.toString().concat("/").concat(message.split(" ",2)[1]));
            System.out.println(pathForDelete.toString());
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
                updateFilesList(ctx, currentPath);
            } catch (IOException ignored) {}
        }
        if ("create".equals(message.split(" ")[0])) {
            Path newPath = Path.of(currentPath.toString().concat("/").concat(message.split(" ",2)[1]));
            Files.createDirectories(newPath);
            updateFilesList(ctx, currentPath);
        }
        if ("find".equals(message.split(" ")[0])) {
            Path rootPath = pathRoot;
            String fileToFind = File.separator + message.split(" ",2)[1];

            try {
                Files.walkFileTree(rootPath, new SimpleFileVisitor<Path>() {

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        String fileString = file.toAbsolutePath().toString();

                        if(fileString.endsWith(fileToFind)){
                            System.out.println("file found at path: " + file.toAbsolutePath());
                            fileInfoServerList.clear();
                            fileInfoServerList.add(new FileInfo(file));
                            ctx.writeAndFlush(fileInfoServerList);
                            return FileVisitResult.TERMINATE;
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch(IOException e){
                e.printStackTrace();
            }

        }
        if ("rename".equals(message.split(" ")[0])) {
            System.out.println("rename files ");
            System.out.println(message + " message from client for rename");
            Path oldPath = Path.of(currentPath.toString(), message.split(" ",2)[1]);
            System.out.println("rename path current " + oldPath.toString());
            Path newPath = Path.of(currentPath.toString(), message.split(" ",2)[2]);
            System.out.println("rename path new " + newPath.toString());
            Files.copy(oldPath, newPath);
            Files.delete(oldPath);
            updateFilesList(ctx, currentPath);
        }

    }

    private void updateFilesList(ChannelHandlerContext ctx, Path currentPath) throws IOException {
        fileInfoServerList.clear();
        fileInfoServerList.addAll(Files.list(currentPath)
                .map(FileInfo::new).collect(Collectors.toList()));
        ctx.writeAndFlush(fileInfoServerList);

    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("client disconnected: " + ctx.channel());
    }
}

