package sv.edu.utec.datos;

import sv.edu.utec.Modelo.Producto;
import sv.edu.utec.Modelo.ResumenCategoria;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Implementacion del contrato con PostgreSQL (Supabase) via JDBC.
 * Es la UNICA clase del proyecto que contiene SQL. Solo manipula datos
 * (DML): la tabla la crea el administrador con sql/01_esquema.sql.
 */
public class JdbcProductoRepositorio implements ProductoRepositorio {

    // Codigos SQLState estandar de PostgreSQL
    private static final String DUPLICADO = "23505";     // unique_violation
    private static final String REGLA_VIOLADA = "23514"; // check_violation
    private static final String COLUMNAS = "codigo, nombre, categoria, precio, existencia";

    //
    // C: Create
    //
    @Override
    public void insertar(Producto p) {
        String sql = "INSERT INTO producto (" + COLUMNAS + ") VALUES (?, ?, ?, ?, ?)";
        try (Connection cn = ConexionPostgres.obtener();
             PreparedStatement ps = cn.prepareStatement(sql)) {
            asignarParametros(ps, p);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw traducir(e, p.getCodigo());
        }
    }

    /** Todo o nada: si un producto falla, no se guarda ninguno. */
    @Override
    public int insertarVarios(List<Producto> productos) {
        String sql = "INSERT INTO producto (" + COLUMNAS + ") VALUES (?, ?, ?, ?, ?)";
        // 1. abrir la transaccion
        try (Connection cn = ConexionPostgres.obtener()) {
            cn.setAutoCommit(false);
            try (PreparedStatement ps = cn.prepareStatement(sql)) {
                // 2. acumular en un lote
                for (Producto p : productos) {
                    asignarParametros(ps, p);
                    ps.addBatch();
                }
                // 3. un solo viaje a la nube
                ps.executeBatch();
                // 4. confirmar
                cn.commit();
                return productos.size();
            } catch (SQLException e) {
                // 5. deshacer TODO
                cn.rollback();
                throw new RepositorioException(
                        "Carga cancelada, no se guardo ningun producto: " + motivo(e), e);
            }
        } catch (SQLException e) {
            throw new RepositorioException("Error de conexion en la carga", e);
        }
    }

    //
    // R: Read
    //
    @Override
    public Optional<Producto> buscarPorCodigo(String codigo) {
        List<Producto> r = consultar(
                "SELECT " + COLUMNAS + " FROM producto WHERE codigo = ?", codigo);
        return r.stream().findFirst();
    }

    @Override
    public List<Producto> listarTodos() {
        return consultar("SELECT " + COLUMNAS + " FROM producto ORDER BY codigo");
    }

    @Override
    public List<Producto> listarPorCategoria(String categoria) {
        return consultar(
                "SELECT " + COLUMNAS + " FROM producto WHERE categoria = ? ORDER BY nombre",
                categoria);
    }

    @Override
    public List<Producto> buscarPorNombre(String texto) {
        // Se escapan los comodines de LIKE: lo que escribe el usuario
        // se busca literal, igual que Pattern.quote en la Guia 4.
        String literal = texto.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return consultar(
                "SELECT " + COLUMNAS + " FROM producto WHERE nombre ILIKE ? ESCAPE '\\' ORDER BY nombre",
                "%" + literal + "%");
    }

    @Override
    public List<Producto> buscarPorRangoDePrecio(double minimo, double maximo) {
        return consultar(
                "SELECT " + COLUMNAS + " FROM producto WHERE precio BETWEEN ? AND ? ORDER BY precio",
                minimo, maximo);
    }

    //
    // U: Update
    //
    @Override
    public boolean actualizar(Producto p) {
        String sql = """
                UPDATE producto
                SET nombre = ?, categoria = ?, precio = ?, existencia = ?, actualizado_en = now()
                WHERE codigo = ?
                """;
        try (Connection cn = ConexionPostgres.obtener();
             PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setString(1, p.getNombre());
            ps.setString(2, p.getCategoria());
            ps.setDouble(3, p.getPrecio());
            ps.setInt(4, p.getExistencia());
            ps.setString(5, p.getCodigo());
            return ps.executeUpdate() > 0; // 0 filas: el codigo no existe
        } catch (SQLException e) {
            throw traducir(e, p.getCodigo());
        }
    }

    @Override
    public boolean ajustarExistencia(String codigo, int cantidad) {
        if (cantidad == 0) {
            throw new IllegalArgumentException("La cantidad no puede ser cero");
        }
        // Revisar y sumar/restar en UNA sentencia atomica dentro del servidor
        String sql = """
                UPDATE producto
                SET existencia = existencia + ?, actualizado_en = now()
                WHERE codigo = ? AND existencia + ? >= 0
                """;
        try (Connection cn = ConexionPostgres.obtener();
             PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setInt(1, cantidad);
            ps.setString(2, codigo);
            ps.setInt(3, cantidad);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw traducir(e, codigo);
        }
    }

    //
    // D: Delete
    //
    @Override
    public boolean eliminar(String codigo) {
        try (Connection cn = ConexionPostgres.obtener();
             PreparedStatement ps = cn.prepareStatement(
                     "DELETE FROM producto WHERE codigo = ?")) {
            ps.setString(1, codigo);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw traducir(e, codigo);
        }
    }

    //
    // Reporte: GROUP BY hace lo que en MongoDB hacia $group
    //
    @Override
    public List<ResumenCategoria> resumenPorCategoria() {
        String sql = """
                SELECT categoria,
                       COUNT(*) AS productos,
                       SUM(existencia) AS unidades,
                       SUM(precio * existencia) AS valor
                FROM producto
                GROUP BY categoria
                ORDER BY valor DESC
                """;
        List<ResumenCategoria> resultado = new ArrayList<>();
        try (Connection cn = ConexionPostgres.obtener();
             PreparedStatement ps = cn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                resultado.add(new ResumenCategoria(
                        rs.getString("categoria"),
                        rs.getInt("productos"),
                        rs.getInt("unidades"),
                        rs.getDouble("valor")));
            }
            return resultado;
        } catch (SQLException e) {
            throw new RepositorioException("Error al generar el reporte", e);
        }
    }

    //
    // Metodos privados de apoyo
    //

    /** Ejecuta un SELECT con parametros y convierte cada fila en Producto. */
    private List<Producto> consultar(String sql, Object... parametros) {
        List<Producto> resultado = new ArrayList<>();
        try (Connection cn = ConexionPostgres.obtener();
             PreparedStatement ps = cn.prepareStatement(sql)) {
            for (int i = 0; i < parametros.length; i++) {
                ps.setObject(i + 1, parametros[i]); // los indices empiezan en 1
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    resultado.add(aProducto(rs));
                }
            }
            return resultado;
        } catch (SQLException e) {
            throw new RepositorioException("Error al consultar productos", e);
        }
    }

    private void asignarParametros(PreparedStatement ps, Producto p) throws SQLException {
        ps.setString(1, p.getCodigo());
        ps.setString(2, p.getNombre());
        ps.setString(3, p.getCategoria());
        ps.setDouble(4, p.getPrecio());
        ps.setInt(5, p.getExistencia());
    }

    private Producto aProducto(ResultSet rs) throws SQLException {
        return new Producto(
                rs.getString("codigo"),
                rs.getString("nombre"),
                rs.getString("categoria"),
                rs.getDouble("precio"),
                rs.getInt("existencia"));
    }

    /** Traduce el SQLState de PostgreSQL al idioma del negocio. */
    private RepositorioException traducir(SQLException e, String codigo) {
        String estado = e.getSQLState();
        if (DUPLICADO.equals(estado)) {
            return new RepositorioException(
                    "Ya existe un producto con el codigo " + codigo, e);
        }
        if (REGLA_VIOLADA.equals(estado)) {
            return new RepositorioException(
                    "PostgreSQL rechazo un valor fuera de regla en " + codigo, e);
        }
        return new RepositorioException(
                "Error de base de datos [" + estado + "]: " + e.getMessage(), e);
    }

    /** En un lote, el motivo real viene encadenado en getNextException(). */
    private String motivo(SQLException e) {
        SQLException real = e.getNextException() != null ? e.getNextException() : e;
        return real.getMessage().lines().findFirst().orElse("");
    }
}