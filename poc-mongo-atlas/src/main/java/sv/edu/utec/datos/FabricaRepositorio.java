package sv.edu.utec.datos;
/**
 * Unico lugar que decide que motor de datos usa el programa.
 * Se elige con la variable de entorno MOTOR_BD: mongo (por defecto) o firestore.
 * En la semana 16 se agrega aqui el caso de PostgreSQL.
 */
public final class FabricaRepositorio {
    private FabricaRepositorio() { }
    public static ProductoRepositorio crear() {
        String motor = System.getenv().getOrDefault("MOTOR_BD", "mongo")
                .trim().toLowerCase();
        return switch (motor) {
            case "mongo" -> new MongoProductoRepositorio(ConexionMongo.getBaseDeDatos());
            case "firestore" -> new FirestoreProductoRepositorio(ConexionFirestore.getFirestore());
            default -> throw new IllegalStateException(
                    "MOTOR_BD debe ser mongo o firestore, no: " + motor);
        };
    }
    public static void cerrarConexiones() {
        ConexionMongo.cerrar(); // cada una ignora la llamada
        ConexionFirestore.cerrar(); // si nunca se abrio
    }
}