package sv.edu.utec.datos;
import sv.edu.utec.Modelo.Producto;
import sv.edu.utec.Modelo.ResumenCategoria;
import java.util.List;
import java.util.Optional;
/**
 * Contrato de acceso a datos. MongoDB, Firestore o PostgreSQL
 * solo cambian la clase que lo implementa; el resto del programa no se entera.
 */
public interface ProductoRepositorio {
    // ---- C: Create ----
    void insertar(Producto producto);
    int insertarVarios(List<Producto> productos);
    // ---- R: Read ----
    Optional<Producto> buscarPorCodigo(String codigo);
    List<Producto> listarTodos();
    List<Producto> listarPorCategoria(String categoria);
    List<Producto> buscarPorNombre(String texto);
    List<Producto> buscarPorRangoDePrecio(double minimo, double maximo);
    // ---- U: Update ----
    boolean actualizar(Producto producto);
    boolean ajustarExistencia(String codigo, int cantidad);
    // ---- D: Delete ----
    boolean eliminar(String codigo);
    // ---- Reporte ----
    List<ResumenCategoria> resumenPorCategoria();
}
