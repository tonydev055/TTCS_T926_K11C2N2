import java.nio.file.*;
import util.KetNoiDB;

/** Apply the additive catalog migration using the application's DB configuration. */
public class ApplyTechnologyCatalog {
    public static void main(String[] args) throws Exception {
        String sql = Files.readString(Path.of(args[0]));
        try (var connection = KetNoiDB.getConnection(); var statement = connection.createStatement()) {
            statement.execute(sql);
            System.out.println("Technology catalog migration applied.");
        }
    }
}
