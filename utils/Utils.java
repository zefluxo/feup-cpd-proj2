package utils;

public class Utils {

    public static String getUsername(String credentials) {
        return credentials.split("/")[0];
    }

    public static String getPassword(String credentials) {
        return credentials.split("/")[1];
    }

    public static void clearConsole() {
        System.out.print("\033[H\033[2J");
        System.out.flush();
    }

}
