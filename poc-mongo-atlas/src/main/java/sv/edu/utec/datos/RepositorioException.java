package sv.edu.utec.datos;
/**
 * Error de acceso a datos expresado en el idioma del negocio.
 * Quien usa el repositorio la captura sin importar nada de MongoDB.
 */
public class RepositorioException extends RuntimeException {
    public RepositorioException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}