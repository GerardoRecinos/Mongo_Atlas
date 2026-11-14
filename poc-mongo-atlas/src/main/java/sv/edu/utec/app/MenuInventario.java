package sv.edu.utec.app;
import sv.edu.utec.datos.ProductoRepositorio;
import sv.edu.utec.datos.RepositorioException;
import sv.edu.utec.Modelo.Producto;
import sv.edu.utec.Modelo.ResumenCategoria;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;
/**
 * Interfaz de consola. Solo conoce la interfaz ProductoRepositorio:
 * no importa nada de com.mongodb.
 */
public class MenuInventario {
    private final ProductoRepositorio repo;
    private final Scanner entrada;
    public MenuInventario(ProductoRepositorio repo, Scanner entrada) {
        this.repo = repo;
        this.entrada = entrada;
    }
    public void iniciar() {
        int opcion;
        do {
            System.out.println();
            System.out.println("===== INVENTARIO DISTRISALVA =====");
            System.out.println(" 1. Listar todos 6. Agregar producto");
            System.out.println(" 2. Buscar codigo 7. Editar producto");
            System.out.println(" 3. Buscar nombre 8. Entrada/salida de existencia");
            System.out.println(" 4. Por categoria 9. Eliminar producto");
            System.out.println(" 5. Rango de precio 10. Reporte por categoria");
            System.out.println(" 0. Salir");
            opcion = leerEntero("Opcion: ");
            try {
                switch (opcion) {
                    case 1 -> mostrar(repo.listarTodos());
                    case 2 -> buscarPorCodigo();
                    case 3 -> mostrar(repo.buscarPorNombre(leerTexto("Texto: ")));
                    case 4 -> mostrar(repo.listarPorCategoria(leerTexto("Categoria: ")));
                    case 5 -> mostrar(repo.buscarPorRangoDePrecio(
                            leerDecimal("Precio minimo: "), leerDecimal("Precio maximo: ")));
                    case 6 -> agregar();
                    case 7 -> editar();
                    case 8 -> moverExistencia();
                    case 9 -> eliminar();
                    case 10 -> reporte();
                    case 0 -> System.out.println("Hasta pronto.");
                    default -> System.out.println("Opcion no valida.");
                }
            } catch (IllegalArgumentException | RepositorioException e) {
                // Un error no debe cerrar el programa: se informa y se sigue
                System.out.println("No se pudo completar: " + e.getMessage());
            }
        } while (opcion != 0);
    }
    // ------------------------- Operaciones -------------------------
    private void buscarPorCodigo() {
        String codigo = leerTexto("Codigo: ").toUpperCase();
        repo.buscarPorCodigo(codigo).ifPresentOrElse(
                System.out::println,
                () -> System.out.println("No existe el producto " + codigo));
    }
    private void agregar() {
        Producto p = new Producto(
                leerTexto("Codigo: "),
                leerTexto("Nombre: "),
                leerTexto("Categoria: "),
                leerDecimal("Precio: "),
                leerEntero("Existencia: "));
        repo.insertar(p);
        System.out.println("Producto agregado: " + p.getCodigo());
    }
    private void editar() {
        String codigo = leerTexto("Codigo a editar: ").toUpperCase();
        Optional<Producto> encontrado = repo.buscarPorCodigo(codigo);
        if (encontrado.isEmpty()) {
            System.out.println("No existe el producto " + codigo);
            return;
        }
        Producto p = encontrado.get();
        System.out.println("Actual: " + p);
        System.out.println("(Enter conserva el valor actual)");
        String nombre = leerOpcional("Nombre [" + p.getNombre() + "]: ");
        if (!nombre.isEmpty()) p.setNombre(nombre);
        String categoria = leerOpcional("Categoria [" + p.getCategoria() + "]: ");
        if (!categoria.isEmpty()) p.setCategoria(categoria);
        String precio = leerOpcional("Precio [" + p.getPrecio() + "]: ");
        if (!precio.isEmpty()) p.setPrecio(Double.parseDouble(precio));
        System.out.println(repo.actualizar(p) ? "Producto actualizado." : "No se actualizo.");
    }
    private void moverExistencia() {
        String codigo = leerTexto("Codigo: ").toUpperCase();
        int cantidad = leerEntero("Cantidad (+ entrada / - salida): ");
        if (repo.ajustarExistencia(codigo, cantidad)) {
            repo.buscarPorCodigo(codigo).ifPresent(System.out::println);
        } else {
            System.out.println("Sin cambios: el codigo no existe o no hay existencia suficiente.");
        }
    }
    private void eliminar() {
        String codigo = leerTexto("Codigo a eliminar: ").toUpperCase();
        String confirma = leerTexto("Seguro que desea eliminar " + codigo + "? (s/n): ");
        if (confirma.equalsIgnoreCase("s")) {
            System.out.println(repo.eliminar(codigo) ? "Eliminado." : "No existe ese codigo.");
        } else {
            System.out.println("Operacion cancelada.");
        }
    }
    private void reporte() {
        double total = 0;
        System.out.println("Categoria Prod. Unidades Valor");
        for (ResumenCategoria r : repo.resumenPorCategoria()) {
            System.out.printf("%-10s %5d %9d $%9.2f%n",
                    r.categoria(), r.productos(), r.unidades(), r.valorInventario());
            total += r.valorInventario();
        }
        System.out.printf("%-10s %27s%n", "TOTAL", String.format("$%9.2f", total));
    }
    private void mostrar(List<Producto> productos) {
        if (productos.isEmpty()) {
            System.out.println("(sin resultados)");
        }
        productos.forEach(System.out::println);
    }
    // ------------------- Lectura segura de datos -------------------
    private String leerOpcional(String mensaje) {
        System.out.print(mensaje);
        return entrada.nextLine().trim();
    }
    private String leerTexto(String mensaje) {
        String texto;
        do {
            texto = leerOpcional(mensaje);
        } while (texto.isEmpty());
        return texto;
    }
    private int leerEntero(String mensaje) {
        while (true) {
            try {
                return Integer.parseInt(leerTexto(mensaje));
            } catch (NumberFormatException e) {
                System.out.println("Escriba un numero entero.");
            }
        }
    }
    private double leerDecimal(String mensaje) {
        while (true) {
            try {
                return Double.parseDouble(leerTexto(mensaje));
            } catch (NumberFormatException e) {
                System.out.println("Escriba un numero, por ejemplo 2.50");
            }
        }
    }
}