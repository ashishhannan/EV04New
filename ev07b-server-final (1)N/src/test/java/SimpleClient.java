import java.net.Socket;
import java.io.OutputStream;

public class SimpleClient {
    public static void main(String[] args) throws Exception {
        String host = args.length>0?args[0]:"localhost";
        int port = args.length>1?Integer.parseInt(args[1]):8080;

        try (Socket s = new Socket(host, port)) {
            OutputStream out = s.getOutputStream();
            byte[] msg = new byte[] {
                (byte)0xAB, (byte)0x10, 0x0F, 0x00, (byte)0xEB, 0x7E, 0x01, 0x01,
                0x02, 0x0D, 0x30, (byte)0xE0,
                0x31,0x37,0x35,0x31,0x32,0x30,0x39,0x31,0x32,0x38,0x39
            };
            out.write(msg);
            out.flush();
            Thread.sleep(2000);
        }
    }
}
