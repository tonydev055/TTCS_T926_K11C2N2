import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.LinkedHashMap;
import java.util.Map;
import service.HinhAnhService;
import util.KetNoiDB;

public final class SeedProductImages {

    private static final Map<String, String> IMAGES = new LinkedHashMap<>();

    static {
        IMAGES.put("SP-NK-330", "sp-nk-330.png");
        IMAGES.put("SP-NT-450", "sp-nt-450.png");
        IMAGES.put("SP-BQ-200", "sp-bq-200.png");
        IMAGES.put("SP-DG-1KG", "sp-dg-1kg.png");
        IMAGES.put("SP-NM-500", "sp-nm-500.png");
    }

    public static void main(String[] args) throws Exception {
        Path imageDirectory = Path.of(
            args.length == 0 ? "web/assets/products" : args[0]
        );
        int updated = 0;

        try (
            Connection connection = KetNoiDB.getConnection();
            PreparedStatement statement = connection.prepareStatement(
                "UPDATE products SET image=?, thumbnail=?, updated_at=CURRENT_TIMESTAMP WHERE sku=?"
            )
        ) {
            connection.setAutoCommit(false);
            try {
                for (var entry : IMAGES.entrySet()) {
                    Path file = imageDirectory.resolve(entry.getValue());
                    var images = HinhAnhService.normalize(Files.readAllBytes(file), false);
                    statement.setBytes(1, images.image());
                    statement.setBytes(2, images.thumbnail());
                    statement.setString(3, entry.getKey());
                    updated += statement.executeUpdate();
                }
                connection.commit();
            } catch (Exception error) {
                connection.rollback();
                throw error;
            }
        }

        System.out.println("Updated product images: " + updated);
    }
}
