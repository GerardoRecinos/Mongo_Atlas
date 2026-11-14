package sv.edu.utec.app;
import com.mongodb.MongoException;
import sv.edu.utec.datos.FabricaRepositorio;
import sv.edu.utec.datos.ProductoRepositorio;
import sv.edu.utec.datos.RepositorioException;
import java.util.Scanner;
/**
 * Punto de arranque. El motor lo decide FabricaRepositorio segun MOTOR_BD;
 * MenuInventario no sabe si los datos estan en MongoDB o en Firestore.
 */
public class App {
    public static void main(String[] args) {
        try (Scanner entrada = new Scanner(System.in)) {
            ProductoRepositorio repo = FabricaRepositorio.crear();
            System.out.println("Motor de datos: " + repo.getClass().getSimpleName());
            if (repo.listarTodos().isEmpty()) {
                int n = repo.insertarVarios(DatosDemo.productos());
                System.out.println("Coleccion vacia: se cargaron " + n + " productos de ejemplo.");
            }
            new MenuInventario(repo, entrada).iniciar();
        } catch (RepositorioException | IllegalStateException e) {
            System.err.println("Error de datos: " + e.getMessage());
        } catch (MongoException e) {
            System.err.println("Error de MongoDB: " + e.getMessage());
        } finally {
            FabricaRepositorio.cerrarConexiones();
        }
    }
}