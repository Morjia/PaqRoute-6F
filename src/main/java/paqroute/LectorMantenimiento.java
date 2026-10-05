package paqroute;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Lee los archivos bimensuales de mantenimiento preventivo:
 * Nombre: mant.preventivo.aa.m1-m2.txt
 * Registro: aaaammdd:TTNN
 */
public final class LectorMantenimiento {

    private static final Pattern PATRON_ARCHIVO = Pattern.compile("mant\\.preventivo\\.\\d{2}\\.\\d{2}-\\d{2}\\.txt");
    private static final Pattern PATRON_REGISTRO = Pattern.compile("(\\d{4})(\\d{2})(\\d{2}):([A-Z]{2}\\d{2})");

    private LectorMantenimiento() {}

    public static List<Mantenimiento> leerArchivo(Path archivo) throws IOException {
        List<Mantenimiento> registros = new ArrayList<>();
        for (String linea : Files.readAllLines(archivo)) {
            String limpia = linea.trim();
            if (limpia.isEmpty()) continue;
            Matcher m = PATRON_REGISTRO.matcher(limpia);
            if (!m.matches()) {
                throw new IllegalArgumentException("Registro de mantenimiento con formato invalido: " + linea);
            }
            LocalDate fecha = LocalDate.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
            registros.add(new Mantenimiento(fecha, m.group(4)));
        }
        return registros;
    }

    /** Lee todos los archivos mant.preventivo.aa.m1-m2.txt de una carpeta. */
    public static List<Mantenimiento> leerCarpeta(Path carpeta) throws IOException {
        List<Mantenimiento> todos = new ArrayList<>();
        try (Stream<Path> archivos = Files.list(carpeta)) {
            List<Path> validos = archivos
                    .filter(p -> PATRON_ARCHIVO.matcher(p.getFileName().toString()).matches())
                    .toList();
            for (Path archivo : validos) todos.addAll(leerArchivo(archivo));
        }
        return todos;
    }
}
