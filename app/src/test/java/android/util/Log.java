package android.util;

/** Mock of android.util.Log */
public class Log {
    public static int i(String tag, String message) {
        System.out.println("INFO: " + tag + ": " + message);
        return 0;
    }
}
